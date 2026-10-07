package com.framstag.llmaj.tools.java.graph;

import com.framstag.llmaj.tools.java.BuildUnit;
import com.framstag.llmaj.tools.java.ClassFileParser;
import com.framstag.llmaj.tools.java.ClassReference;
import com.framstag.llmaj.tools.java.Module;
import com.framstag.llmaj.tools.java.Package;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The reference structure inside one module, extracted once and shared by the graph tool, the split
 * candidate analysis and the diagram.
 *
 * <p>Nodes are the top level classes of the module. An edge exists only between two of them: a reference to
 * a type the module does not define is coupling to the outside world, not structure inside the module, so it
 * is dropped and counted.
 *
 * <p>Everything is filtered to the project namespace before any graph is built. A module report is produced
 * from compiled artefacts, and when a module ships a shaded jar the scan sees dependency classes as if they
 * were the module's own. Filtering after the fact would leave clusters drawn around dependency internals.
 */
public final class IntraModuleGraph {
    /**
     * A coupling between two classes of the module, with both directions folded together. The API width is
     * the size of the interface that crosses the seam and is the weight a separation has to pay for it. The
     * traffic is how much code actually crosses and is reported beside it.
     */
    public record Coupling(String from, String to, int apiWidth, int traffic) {
    }

    /** A superclass, interface or declared field type relation. It carries no separation cost. */
    public record StructuralLink(String from, String to) {
    }

    /** One node of the graph, with the category the module report recorded for it. */
    public record ClassNode(String name, boolean production, boolean generated) {
        public boolean isProductionCode() {
            return production && !generated;
        }
    }

    private static final class Accumulator {
        private int apiWidth;
        private int traffic;
    }

    private final String moduleName;
    private final String projectNamespace;
    private final List<ClassNode> nodes;
    private final List<Coupling> couplings;
    private final List<StructuralLink> structuralLinks;
    private final MapWeightedGraph referenceGraph;
    private final Map<String, Integer> afferentCoupling;
    private final Map<String, Integer> efferentCoupling;
    private final int excludedNodes;
    private final int excludedEdges;

    private IntraModuleGraph(String moduleName,
                             String projectNamespace,
                             List<ClassNode> nodes,
                             List<Coupling> couplings,
                             List<StructuralLink> structuralLinks,
                             MapWeightedGraph referenceGraph,
                             Map<String, Integer> afferentCoupling,
                             Map<String, Integer> efferentCoupling,
                             int excludedNodes,
                             int excludedEdges) {
        this.moduleName = moduleName;
        this.projectNamespace = projectNamespace;
        this.nodes = List.copyOf(nodes);
        this.couplings = List.copyOf(couplings);
        this.structuralLinks = List.copyOf(structuralLinks);
        this.referenceGraph = referenceGraph;
        this.afferentCoupling = Map.copyOf(afferentCoupling);
        this.efferentCoupling = Map.copyOf(efferentCoupling);
        this.excludedNodes = excludedNodes;
        this.excludedEdges = excludedEdges;
    }

    public static IntraModuleGraph of(Module module) {
        String projectNamespace = projectNamespace(module);

        List<ClassNode> allBuildUnits = new ArrayList<>();
        Set<String> nodeNames = new TreeSet<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!isInNamespace(buildUnit.getName(), projectNamespace)) {
                    continue;
                }

                allBuildUnits.add(new ClassNode(buildUnit.getName(),
                        buildUnit.isProduction(),
                        buildUnit.isGenerated()));
                nodeNames.add(buildUnit.getName());
            }
        }

        int excludedNodes = countBuildUnits(module) - allBuildUnits.size();

        Map<String, Accumulator> referencePairs = new LinkedHashMap<>();
        Map<String, Accumulator> structuralPairs = new LinkedHashMap<>();
        Map<String, Set<String>> referencedBy = new HashMap<>();
        Map<String, Set<String>> references = new HashMap<>();
        int excludedEdges = 0;

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!isInNamespace(buildUnit.getName(), projectNamespace)) {
                    continue;
                }

                for (ClassReference reference : buildUnit.getReferences()) {
                    String target = ClassFileParser.getBuildUnitName(reference.getTarget());

                    if (target.equals(buildUnit.getName()) || !nodeNames.contains(target)) {
                        // A reference inside the same class group, or to something the module does not
                        // define. Neither is structure inside this module.
                        if (!target.equals(buildUnit.getName())) {
                            excludedEdges++;
                        }
                        continue;
                    }

                    if (reference.isStructural()) {
                        accumulate(structuralPairs, buildUnit.getName(), target, 0, 0);
                    }

                    if (reference.getTraffic() > 0) {
                        accumulate(referencePairs, buildUnit.getName(), target,
                                reference.getApiWidth(), reference.getTraffic());
                        references.computeIfAbsent(buildUnit.getName(), ignored -> new TreeSet<>()).add(target);
                        referencedBy.computeIfAbsent(target, ignored -> new TreeSet<>()).add(buildUnit.getName());
                    }
                }
            }
        }

        List<Coupling> couplings = couplingsOf(referencePairs);
        List<StructuralLink> structuralLinks = structuralLinksOf(structuralPairs);

        MapWeightedGraph referenceGraph = new MapWeightedGraph();
        for (ClassNode node : allBuildUnits) {
            referenceGraph.addNode(node.name());
        }
        for (Coupling coupling : couplings) {
            referenceGraph.addEdge(coupling.from(), coupling.to(), coupling.apiWidth());
        }

        return new IntraModuleGraph(module.getName(),
                projectNamespace,
                sortedNodes(allBuildUnits),
                couplings,
                structuralLinks,
                referenceGraph,
                couplingDegrees(referencedBy),
                couplingDegrees(references),
                excludedNodes,
                excludedEdges);
    }

    private static Map<String, Integer> couplingDegrees(Map<String, Set<String>> partnersByClass) {
        Map<String, Integer> degrees = new HashMap<>();

        partnersByClass.forEach((name, partners) -> degrees.put(name, partners.size()));

        return degrees;
    }

    /**
     * The share of the module's classes a package prefix has to cover to count as the module's namespace.
     *
     * <p>Measured against the checked in workspaces: a maven module whose classes live under
     * {@code org.apache.maven} also carries five classes of {@code org.jline.nativ}, and the jabref modules
     * carry a handful of shaded classes beside {@code org.jabref}. At 90 percent the deepest prefix that still
     * holds nine out of ten classes is the module's own namespace for every one of them, while a lower share
     * starts cutting off real packages: on jablib, 80 percent would already leave out
     * {@code org.jabref.model}.
     */
    private static final int NAMESPACE_MINIMUM_COVERAGE_PERCENT = 90;

    /**
     * The project namespace is the deepest package prefix that still holds nine out of ten of the module's
     * classes.
     *
     * <p>A module report is produced from compiled artefacts. When a module ships a shaded jar the scan also
     * sees a handful of dependency packages, so the prefix every package shares is not the module's namespace:
     * the only thing {@code org.apache.maven}, {@code org.jline.nativ} and {@code org.fusesource.jansi} have in
     * common is {@code org}, which filters nothing. A shaded dependency is always a small minority of the
     * classes, which is what makes the coverage rule work. Everything outside the namespace is excluded, and
     * the number of exclusions is reported rather than left implicit.
     */
    public static String projectNamespace(Module module) {
        Map<String, Integer> buildUnitsByPrefix = new TreeMap<>();
        int totalBuildUnits = 0;

        for (Package pck : module.getPackages()) {
            int buildUnits = pck.getBuildUnits().size();
            totalBuildUnits += buildUnits;

            for (String prefix : prefixesOf(pck.getName())) {
                buildUnitsByPrefix.merge(prefix, buildUnits, Integer::sum);
            }
        }

        String namespace = "";

        for (Map.Entry<String, Integer> candidate : buildUnitsByPrefix.entrySet()) {
            if (candidate.getValue() * 100 >= totalBuildUnits * NAMESPACE_MINIMUM_COVERAGE_PERCENT
                    && candidate.getKey().length() > namespace.length()) {
                namespace = candidate.getKey();
            }
        }

        return namespace;
    }

    private static List<String> prefixesOf(String packageName) {
        if (packageName.isEmpty()) {
            return List.of();
        }

        String[] segments = packageName.split("\\.");
        List<String> prefixes = new ArrayList<>();

        for (int length = 1; length <= segments.length; length++) {
            prefixes.add(String.join(".", Arrays.copyOfRange(segments, 0, length)));
        }

        return prefixes;
    }

    public static boolean isInNamespace(String qualifiedName, String projectNamespace) {
        if (projectNamespace.isEmpty()) {
            return true;
        }

        return qualifiedName.equals(projectNamespace) || qualifiedName.startsWith(projectNamespace + ".");
    }

    private static int countBuildUnits(Module module) {
        return module.getPackages().stream().mapToInt(pck -> pck.getBuildUnits().size()).sum();
    }

    private static void accumulate(Map<String, Accumulator> pairs,
                                   String from,
                                   String to,
                                   int apiWidth,
                                   int traffic) {
        String first = from.compareTo(to) <= 0 ? from : to;
        String second = from.compareTo(to) <= 0 ? to : from;

        Accumulator accumulator = pairs.computeIfAbsent(first + "\u0000" + second, ignored -> new Accumulator());
        accumulator.apiWidth += apiWidth;
        accumulator.traffic += traffic;
    }

    private static List<String> sortedKeys(Map<String, Accumulator> pairs) {
        List<String> keys = new ArrayList<>(pairs.keySet());
        Collections.sort(keys);

        return keys;
    }

    private static List<Coupling> couplingsOf(Map<String, Accumulator> pairs) {
        List<Coupling> couplings = new ArrayList<>();

        for (String key : sortedKeys(pairs)) {
            String[] endpoints = key.split("\u0000");
            Accumulator accumulator = pairs.get(key);

            couplings.add(new Coupling(endpoints[0], endpoints[1],
                    accumulator.apiWidth, accumulator.traffic));
        }

        return couplings;
    }

    private static List<StructuralLink> structuralLinksOf(Map<String, Accumulator> pairs) {
        List<StructuralLink> links = new ArrayList<>();

        for (String key : sortedKeys(pairs)) {
            String[] endpoints = key.split("\u0000");

            links.add(new StructuralLink(endpoints[0], endpoints[1]));
        }

        return links;
    }

    private static List<ClassNode> sortedNodes(List<ClassNode> nodes) {
        List<ClassNode> sorted = new ArrayList<>(nodes);
        sorted.sort((left, right) -> left.name().compareTo(right.name()));

        return sorted;
    }

    public String moduleName() {
        return moduleName;
    }

    public String projectNamespace() {
        return projectNamespace;
    }

    public List<ClassNode> nodes() {
        return nodes;
    }

    public List<Coupling> couplings() {
        return couplings;
    }

    public List<StructuralLink> structuralLinks() {
        return structuralLinks;
    }

    public MapWeightedGraph referenceGraph() {
        return referenceGraph;
    }

    /** The subgraph of production code, which is what a separation of the module's design can be based on. */
    public MapWeightedGraph productionReferenceGraph() {
        MapWeightedGraph production = new MapWeightedGraph();

        for (ClassNode node : nodes) {
            if (node.isProductionCode()) {
                production.addNode(node.name());
            }
        }

        for (Coupling coupling : couplings) {
            if (production.nodes().contains(coupling.from()) && production.nodes().contains(coupling.to())) {
                production.addEdge(coupling.from(), coupling.to(), coupling.apiWidth());
            }
        }

        return production;
    }

    public int excludedNodes() {
        return excludedNodes;
    }

    /** How many classes of the module reference this class. */
    public int afferentCoupling(String className) {
        return afferentCoupling.getOrDefault(className, 0);
    }

    /** How many distinct classes of the module this class references. */
    public int efferentCoupling(String className) {
        return efferentCoupling.getOrDefault(className, 0);
    }

    public int excludedEdges() {
        return excludedEdges;
    }
}
