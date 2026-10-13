package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.tools.java.BuildUnit;
import com.framstag.llmaj.tools.java.ClassFileParser;
import com.framstag.llmaj.tools.java.ClassReference;
import com.framstag.llmaj.tools.java.Clazz;
import com.framstag.llmaj.tools.java.Field;
import com.framstag.llmaj.tools.java.Method;
import com.framstag.llmaj.tools.java.Module;
import com.framstag.llmaj.tools.java.Package;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether the names of a module follow the code structure of that module.
 *
 * <p>A raw average of name similarity says little, because every class of a module shares the words of its
 * package and its framework. The value reported here is the difference between two averages over the same
 * population: the name overlap of two types that reference each other minus the name overlap of two types
 * that do not. A positive value says names follow the references, a value near zero says the names of the
 * module are interchangeable, and that is the case the analysis is after.
 *
 * <p>The population is the unordered pairs of two different build units of the module, restricted to the
 * build units that production code is made of. A pair whose two name sets share no token at all is left
 * out, because an overlap over two empty sets is not a measurement. A module can therefore report no value:
 * when no pair references the other, or when no pair is unrelated, there is nothing to compare and the
 * cohesion is unavailable rather than zero.
 *
 * <p>The name set of a build unit is the tokens of the names of its types, its methods, its fields and its
 * field types. Package segments stay out: they are the same for nearly every build unit of a module and
 * would lift every overlap by the same amount without telling anything apart.
 */
public record NamingCohesion(Double value,
                             Double linkedOverlap,
                             Double unlinkedOverlap,
                             List<ClassPairOverlap> pairs) {

    /**
     * The name overlap of one unordered pair of build units of a module.
     */
    public record ClassPairOverlap(String firstBuildUnit,
                                   String secondBuildUnit,
                                   boolean linked,
                                   double overlap,
                                   List<String> sharedTerms) {
    }

    public static NamingCohesion of(Module module) {
        Map<String, Set<String>> nameTokensByBuildUnit = new LinkedHashMap<>();
        Map<String, Set<String>> referencesByBuildUnit = new LinkedHashMap<>();

        if (module != null) {
            for (Package pck : module.getPackages()) {
                for (BuildUnit buildUnit : pck.getBuildUnits()) {
                    if (!ModuleVocabulary.isCounted(buildUnit)) {
                        continue;
                    }

                    nameTokensByBuildUnit.put(buildUnit.getName(), nameTokensOf(buildUnit));
                    referencesByBuildUnit.put(buildUnit.getName(), referenceTargetsOf(buildUnit));
                }
            }
        }

        List<ClassPairOverlap> pairs = new ArrayList<>();
        List<String> buildUnits = new ArrayList<>(nameTokensByBuildUnit.keySet());

        for (int i = 0; i < buildUnits.size(); i++) {
            for (int j = i + 1; j < buildUnits.size(); j++) {
                String first = buildUnits.get(i);
                String second = buildUnits.get(j);

                Set<String> firstTokens = nameTokensByBuildUnit.get(first);
                Set<String> secondTokens = nameTokensByBuildUnit.get(second);

                if (firstTokens.isEmpty() && secondTokens.isEmpty()) {
                    continue;
                }

                boolean linked = references(referencesByBuildUnit, first, second)
                        || references(referencesByBuildUnit, second, first);

                pairs.add(new ClassPairOverlap(first, second, linked,
                        jaccard(firstTokens, secondTokens), sharedTerms(firstTokens, secondTokens)));
            }
        }

        Double linkedOverlap = meanOverlap(pairs, true);
        Double unlinkedOverlap = meanOverlap(pairs, false);

        if (linkedOverlap == null || unlinkedOverlap == null) {
            return new NamingCohesion(null, linkedOverlap, unlinkedOverlap, List.copyOf(pairs));
        }

        return new NamingCohesion(linkedOverlap - unlinkedOverlap, linkedOverlap, unlinkedOverlap,
                List.copyOf(pairs));
    }

    private static boolean references(Map<String, Set<String>> referencesByBuildUnit, String from, String to) {
        return referencesByBuildUnit.getOrDefault(from, Set.of()).contains(to);
    }

    private static Set<String> nameTokensOf(BuildUnit buildUnit) {
        Set<String> tokens = new LinkedHashSet<>();

        for (Clazz clazz : buildUnit.getClazzes()) {
            tokens.addAll(IdentifierTokenizer.tokenize(simpleNameOf(clazz.getQualifiedName())));

            for (Method method : clazz.getMethods()) {
                tokens.addAll(IdentifierTokenizer.tokenize(method.getName()));
            }

            for (Field field : clazz.getFields()) {
                tokens.addAll(IdentifierTokenizer.tokenize(field.getName()));
                tokens.addAll(IdentifierTokenizer.tokenize(simpleTypeOf(field.getType())));
            }
        }

        return tokens;
    }

    private static Set<String> referenceTargetsOf(BuildUnit buildUnit) {
        Set<String> targets = new LinkedHashSet<>();

        for (ClassReference reference : buildUnit.getReferences()) {
            if (reference.getTarget() != null && !reference.getTarget().isEmpty()) {
                targets.add(ClassFileParser.getBuildUnitName(reference.getTarget()));
            }
        }

        return targets;
    }

    private static String simpleNameOf(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }

        int lastDot = qualifiedName.lastIndexOf('.');
        int lastDollar = qualifiedName.lastIndexOf('$');
        int start = Math.max(lastDot, lastDollar);

        return start < 0 ? qualifiedName : qualifiedName.substring(start + 1);
    }

    private static String simpleTypeOf(String type) {
        return ProseText.simpleTypeName(type);
    }

    private static List<String> sharedTerms(Set<String> first, Set<String> second) {
        return first.stream().filter(second::contains).sorted().toList();
    }

    private static double jaccard(Set<String> first, Set<String> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return 0.0;
        }

        int shared = 0;

        for (String token : first) {
            if (second.contains(token)) {
                shared++;
            }
        }

        return (double) shared / (first.size() + second.size() - shared);
    }

    private static Double meanOverlap(List<ClassPairOverlap> pairs, boolean linked) {
        int count = 0;
        double sum = 0.0;

        for (ClassPairOverlap pair : pairs) {
            if (pair.linked() == linked) {
                count++;
                sum += pair.overlap();
            }
        }

        return count == 0 ? null : sum / count;
    }
}
