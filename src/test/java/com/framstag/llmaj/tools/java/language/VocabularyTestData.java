package com.framstag.llmaj.tools.java.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test data shared by the vocabulary tests: a counted vocabulary without the module model it usually comes
 * from, and a lexicon written through the real loader so that the tests exercise the file format.
 */
final class VocabularyTestData {

    private VocabularyTestData() {
    }

    static Map<String, TermOccurrences> occurrences(Object... termAndCount) {
        Map<String, TermOccurrences> occurrencesByTerm = new LinkedHashMap<>();

        for (int i = 0; i + 1 < termAndCount.length; i += 2) {
            String term = (String) termAndCount[i];
            int count = (Integer) termAndCount[i + 1];

            occurrencesByTerm.computeIfAbsent(term, t -> new TermOccurrences())
                    .add(VocabularyChannel.TYPE, count);
        }

        return occurrencesByTerm;
    }

    static Map<String, TermOccurrences> occurrencesOf(Map<String, Integer> termAndCount) {
        Map<String, TermOccurrences> occurrencesByTerm = new LinkedHashMap<>();

        termAndCount.forEach((term, count) -> occurrencesByTerm
                .computeIfAbsent(term, t -> new TermOccurrences())
                .add(VocabularyChannel.TYPE, count));

        return occurrencesByTerm;
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
}
