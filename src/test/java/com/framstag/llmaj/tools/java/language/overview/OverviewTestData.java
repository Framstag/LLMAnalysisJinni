package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.tools.java.BuildUnit;
import com.framstag.llmaj.tools.java.Clazz;
import com.framstag.llmaj.tools.java.Module;
import com.framstag.llmaj.tools.java.Package;
import com.framstag.llmaj.tools.java.language.DomainLexicon;
import com.framstag.llmaj.tools.java.language.ModuleLanguageEvidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test data for the project-level overview. A module becomes a package of types, and a type name is the way to
 * put a term into its vocabulary, so a test reads like the names it is about.
 *
 * <p>The package is named with a single character, which the tokenizer drops, so the package channel adds no
 * term and a test only sees the names it wrote.
 */
final class OverviewTestData {

    private OverviewTestData() {
    }

    static DomainLexicon lexicon(Path directory, String... termAndTier) throws IOException {
        List<String> lines = new ArrayList<>(List.of("Version,test", "Term,Tier,Note"));

        for (int i = 0; i + 1 < termAndTier.length; i += 2) {
            lines.add(termAndTier[i] + "," + termAndTier[i + 1] + ",test entry");
        }

        Path path = directory.resolve("domain_lexicon.csv");
        Files.write(path, lines);

        return DomainLexicon.load(path);
    }

    /**
     * @return a module whose production types are named as given, so that a type name contributes its tokens to
     * the vocabulary of the module
     */
    static ModuleLanguageEvidence evidence(String moduleName, DomainLexicon lexicon, String... typeNames) {
        Package pck = new Package("x");
        Module module = new Module(moduleName);

        for (String typeName : typeNames) {
            Clazz clazz = new Clazz("x." + typeName, null);
            pck.addBuildUnit(new BuildUnit("x." + typeName, true, false, List.of(), List.of(clazz)));
        }

        module.addPackage(pck);

        return ModuleLanguageEvidence.of(moduleName, module, null, lexicon, 1, 20);
    }

    /**
     * @return the same token repeated as camel case inside one name, which produces that many occurrences of it
     */
    static String repeats(String token, int times) {
        StringBuilder name = new StringBuilder();

        for (int i = 0; i < times; i++) {
            name.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }

        return name.toString();
    }

    static DomainLanguageOverview overview(int leadingTerms, Map<String, String> descriptions,
                                           ModuleLanguageEvidence... evidence) {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(evidence));
        // A fixture of two or three modules has no vocabulary of its own to speak of, so nothing is set aside.
        Set<String> noUbiquitousTerms = Set.of();
        LexicalOverlap overlap = LexicalOverlap.of(vocabulary, noUbiquitousTerms);

        return new DomainLanguageOverview(vocabulary,
                overlap,
                TermIntrusion.of(vocabulary, descriptions, leadingTerms, noUbiquitousTerms),
                QuadrantRow.of(vocabulary, overlap, ImportMatrix.absent(), leadingTerms, noUbiquitousTerms),
                List.of(),
                ImportMatrix.absent());
    }

    static Map<String, String> descriptions(Object... moduleAndDescription) {
        Map<String, String> descriptions = new LinkedHashMap<>();

        for (int i = 0; i + 1 < moduleAndDescription.length; i += 2) {
            descriptions.put((String) moduleAndDescription[i], (String) moduleAndDescription[i + 1]);
        }

        return descriptions;
    }
}
