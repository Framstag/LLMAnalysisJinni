package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.tools.java.graph.IntraModuleGraph;
import com.framstag.llmaj.tools.java.graph.Percentiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ranks the classes of a module by how far they stand out from their peers.
 *
 * <p>Every factor is a percentile within the module, so nothing here is a threshold that would have to be
 * defended: the result never says a class is bad in absolute terms, only that it stands out against the other
 * classes of the same module. The factor that drove a class's score is named, so a reader can disagree with
 * the weighting instead of having to trust it.
 *
 * <p>Size and cohesion together are what make this a god class ranking rather than a large class ranking. A
 * large class whose methods work on the state of the class scores high on cohesion and is correctly not
 * flagged; a class whose methods each touch their own corner of the state does not get that protection.
 */
public final class GodClassRanking {
    public static final String FACTOR_SIZE = "WMC";
    public static final String FACTOR_COHESION = "cohesion (TCC-like)";
    public static final String FACTOR_FOREIGN_DATA = "foreign data accesses";
    public static final String FACTOR_NESTING = "maximum nesting depth";
    public static final String FACTOR_METHOD_LINES = "maximum method lines";
    public static final String FACTOR_COUPLING = "module coupling";

    public static final int DEFAULT_RANKING_LIMIT = 15;

    /** Fewer entries per module when every module is reported at once, so the batch stays readable. */
    public static final int DEFAULT_BATCH_RANKING_LIMIT = 5;

    private static final List<String> FACTORS = List.of(FACTOR_SIZE,
            FACTOR_COHESION,
            FACTOR_FOREIGN_DATA,
            FACTOR_NESTING,
            FACTOR_METHOD_LINES,
            FACTOR_COUPLING);

    /**
     * One factor of a class, with the measured value and where that value sits among the module's classes.
     */
    public record Factor(String name, double value, int percentile) {
    }

    /**
     * One entry of the ranking.
     *
     * @param score          the mean of the factor percentiles that count as badness, so a higher score is a
     *                       stronger candidate
     * @param dominantFactor the factor that contributed most to the score
     */
    public record RankedClass(String name,
                              int rank,
                              int rankedClassCount,
                              double score,
                              String dominantFactor,
                              List<Factor> factors) {
    }

    private record ClassMetrics(String name,
                                double wmc,
                                double cohesion,
                                double foreignData,
                                double nesting,
                                double methodLines,
                                double coupling) {
    }

    private GodClassRanking() {
    }

    /**
     * @param limit the number of entries to return; every class of the module is still ranked, so the rank of
     *              a returned entry is its position among all of them
     */
    public static List<RankedClass> of(Module module, IntraModuleGraph graph, int limit) {
        List<ClassMetrics> metrics = metricsOf(module, graph);

        Map<String, List<Double>> valuesByFactor = new LinkedHashMap<>();
        for (String factor : FACTORS) {
            valuesByFactor.put(factor, metrics.stream().map(entry -> valueOf(entry, factor)).toList());
        }

        List<RankedClass> unranked = new ArrayList<>();

        for (ClassMetrics entry : metrics) {
            List<Factor> factors = new ArrayList<>();
            double badnessSum = 0;
            String dominantFactor = FACTORS.getFirst();
            int dominantBadness = -1;

            for (String factor : FACTORS) {
                double value = valueOf(entry, factor);
                int percentile = Percentiles.of(valuesByFactor.get(factor), value);
                // Cohesion is the one factor where a low value is the problem. Reading it by inverting the
                // percentile would penalise every class in a module where nearly all classes are cohesive,
                // so it is read as the share of peers the class is behind on cohesion.
                int badness = FACTOR_COHESION.equals(factor)
                        ? Percentiles.above(valuesByFactor.get(factor), value)
                        : percentile;

                factors.add(new Factor(factor, value, percentile));
                badnessSum += badness;

                if (badness > dominantBadness) {
                    dominantFactor = factor;
                    dominantBadness = badness;
                }
            }

            unranked.add(new RankedClass(entry.name(), 0, metrics.size(),
                    Math.round(badnessSum / FACTORS.size() * 10.0) / 10.0,
                    dominantFactor,
                    List.copyOf(factors)));
        }

        unranked.sort(Comparator.comparingDouble(RankedClass::score).reversed()
                .thenComparing(RankedClass::name));

        List<RankedClass> ranking = new ArrayList<>();

        for (int index = 0; index < unranked.size(); index++) {
            RankedClass entry = unranked.get(index);
            ranking.add(new RankedClass(entry.name(), index + 1, unranked.size(), entry.score(),
                    entry.dominantFactor(), entry.factors()));
        }

        return List.copyOf(ranking.subList(0, Math.min(Math.max(limit, 1), ranking.size())));
    }

    private static double valueOf(ClassMetrics metrics, String factor) {
        return switch (factor) {
            case FACTOR_SIZE -> metrics.wmc();
            case FACTOR_COHESION -> metrics.cohesion();
            case FACTOR_FOREIGN_DATA -> metrics.foreignData();
            case FACTOR_NESTING -> metrics.nesting();
            case FACTOR_METHOD_LINES -> metrics.methodLines();
            case FACTOR_COUPLING -> metrics.coupling();
            default -> throw new IllegalArgumentException("unknown factor " + factor);
        };
    }

    private static List<ClassMetrics> metricsOf(Module module, IntraModuleGraph graph) {
        Map<String, IntraModuleGraph.ClassNode> nodesByName = graph.nodes().stream()
                .collect(Collectors.toMap(IntraModuleGraph.ClassNode::name, node -> node));

        List<ClassMetrics> metrics = new ArrayList<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                IntraModuleGraph.ClassNode node = nodesByName.get(buildUnit.getName());

                if (node == null || !node.isProductionCode()) {
                    continue;
                }

                metrics.add(metricsOf(buildUnit, graph));
            }
        }

        return metrics;
    }

    private static ClassMetrics metricsOf(BuildUnit buildUnit, IntraModuleGraph graph) {
        List<Method> methods = buildUnit.getClazzes().stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .toList();

        int wmc = 0;
        int maximumNesting = 0;
        int maximumLines = 0;
        int foreignData = 0;
        int methodsTouchingState = 0;

        for (Method method : methods) {
            wmc += method.getCyclomaticComplexity() == null ? 0 : method.getCyclomaticComplexity();
            maximumNesting = Math.max(maximumNesting, method.getNestingDepth());
            maximumLines = Math.max(maximumLines, method.getLinesOfCode() == null ? 0 : method.getLinesOfCode());
            foreignData += method.getForeignFieldAccesses();

            if (method.getInternalFieldAccesses() > 0) {
                methodsTouchingState++;
            }
        }

        return new ClassMetrics(buildUnit.getName(),
                wmc,
                cohesionOf(methods.size(), methodsTouchingState),
                foreignData,
                maximumNesting,
                maximumLines,
                graph.afferentCoupling(buildUnit.getName()) + graph.efferentCoupling(buildUnit.getName()));
    }

    /**
     * The share of method pairs that both work on the state of the class.
     *
     * <p>This is a TCC-like approximation, not TCC itself: the module report records how often a method
     * touches a field of its own class, not which field, so two methods count as sharing state when both
     * touch any of it. A class with fewer than two methods has no pair that could disagree and counts as
     * cohesive.
     */
    private static double cohesionOf(int methodCount, int methodsTouchingState) {
        int pairs = methodCount * (methodCount - 1) / 2;

        if (pairs == 0) {
            return 1.0;
        }

        int sharingPairs = methodsTouchingState * (methodsTouchingState - 1) / 2;

        return (double) sharingPairs / pairs;
    }

    /**
     * @return the number of classes of the module that were not ranked, so the omission is visible
     */
    public static int excludedClassCount(Module module, IntraModuleGraph graph) {
        Set<String> ranked = metricsOf(module, graph).stream()
                .map(ClassMetrics::name)
                .collect(Collectors.toSet());

        int excluded = 0;

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!ranked.contains(buildUnit.getName())) {
                    excluded++;
                }
            }
        }

        return excluded;
    }
}
