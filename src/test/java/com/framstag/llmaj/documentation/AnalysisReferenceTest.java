package com.framstag.llmaj.documentation;

import com.framstag.llmaj.config.Config;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The analysis reference is what tells a reader of the report what a number means and how to read it. Its shape
 * is therefore a contract: an entry that loses a part, or a steering table that stops saying where a value
 * comes from, leaves the reader of the report with an unexplained number.
 */
class AnalysisReferenceTest {
    private static final Path REFERENCE = Path.of("analysis", "software-architecture",
            "documentation", "AnalysisReference.adoc.hbs");

    private static final List<String> PARTS = List.of(
            "==== What is measured",
            "==== How to read it",
            "==== Weights",
            "==== Caveats",
            "==== Steering");

    /**
     * The analyses whose numbers a reader cannot read unaided, in the order the report renders them. An entry
     * that disappears from the reference, or one that appears without a section that needs it, leaves the
     * reader of the report with an unexplained number.
     */
    private static final List<String> DOCUMENTED_ANALYSES = List.of(
            "Cyclomatic complexity",
            "Method nesting depth",
            "Method visibility",
            "Class inheritance",
            "Method complexity",
            "Field visibility",
            "Class cohesion",
            "Class coupling",
            "Test coverage",
            "Circular dependencies",
            "Method count",
            "Documentation ratio",
            "Data class candidates",
            "Boolean parameters",
            "Annotation usage",
            "Package tangles",
            "Import diversity",
            "Inter-module dependencies",
            "Class reference structure",
            "Dependency diagrams",
            "God class candidates",
            "Domain language",
            "Context map");

    private static final Set<String> SEEDED_BUDGETS = Set.of(
            Config.DIAGRAM_MAX_NODES_PROPERTY,
            Config.DIAGRAM_MAX_OVERVIEW_NODES_PROPERTY,
            Config.DIAGRAM_MIN_EDGE_WEIGHT_PROPERTY,
            Config.DIAGRAM_MAX_EDGES_PER_NODE_PROPERTY,
            Config.DIAGRAM_MAX_GROUP_DIAGRAMS_PROPERTY,
            Config.DIAGRAM_MIN_CUT_NODE_LIMIT_PROPERTY,
            Config.GOD_CLASS_RANKING_LIMIT_PROPERTY,
            Config.GOD_CLASS_BATCH_RANKING_LIMIT_PROPERTY,
            Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY,
            Config.DOMAIN_LANGUAGE_MINIMUM_CANDIDATE_TERMS_PROPERTY,
            Config.DOMAIN_LANGUAGE_TOP_TERMS_PROPERTY,
            Config.CONTEXT_MAP_LEADING_TERMS_PROPERTY,
            Config.CONTEXT_MAP_MAX_PAIRS_PROPERTY,
            Config.CONTEXT_MAP_UBIQUITOUS_SHARE_PROPERTY);

    /**
     * @param name  the heading text of the entry, without the heading markers
     * @param lines the lines of the entry, excluding its own heading
     */
    private record Entry(String name, List<String> lines) {
    }

    private static List<String> readReference() throws IOException {
        return Files.readAllLines(REFERENCE);
    }

    private static List<Entry> entriesOf(List<String> lines) {
        List<Entry> entries = new ArrayList<>();
        String name = null;
        List<String> body = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("=== ")) {
                if (name != null) {
                    entries.add(new Entry(name, List.copyOf(body)));
                }
                name = line.substring("=== ".length()).trim();
                body = new ArrayList<>();
            } else if (name != null) {
                body.add(line);
            }
        }

        if (name != null) {
            entries.add(new Entry(name, List.copyOf(body)));
        }

        return entries;
    }

    /**
     * @return the cells of the rows of the steering table of an entry, its header row first, each cell trimmed
     */
    private static List<List<String>> steeringTableOf(Entry entry) {
        int heading = entry.lines().indexOf("==== Steering");
        assertTrue(heading >= 0, "the entry " + entry.name() + " has a steering part");

        List<String> rows = new ArrayList<>();
        boolean inTable = false;

        for (String line : entry.lines().subList(heading + 1, entry.lines().size())) {
            if (line.startsWith("|===")) {
                if (inTable) {
                    return rows.stream().map(AnalysisReferenceTest::cellsOf).toList();
                }
                inTable = true;
            } else if (inTable && line.startsWith("|")) {
                rows.add(line);
            }
        }

        assertTrue(inTable, "the steering table of the entry " + entry.name() + " is closed");
        return List.of();
    }

    private static List<String> cellsOf(String row) {
        List<String> cells = new ArrayList<>();

        for (String cell : row.split("\\|")) {
            if (!cell.isBlank()) {
                cells.add(cell.trim());
            }
        }

        return cells;
    }

    @Test
    void everyEntryCarriesAllItsParts() throws IOException {
        List<Entry> entries = entriesOf(readReference());

        assertFalse(entries.isEmpty(), "the reference describes at least one analysis");

        for (Entry entry : entries) {
            for (String part : PARTS) {
                assertTrue(entry.lines().contains(part),
                        "the entry " + entry.name() + " carries the part " + part);
            }
        }
    }

    @Test
    void everyDocumentedAnalysisHasExactlyOneEntry() throws IOException {
        List<String> names = entriesOf(readReference()).stream().map(Entry::name).toList();

        assertEquals(DOCUMENTED_ANALYSES, names,
                "the reference holds one entry per documented analysis, in the order the report renders them");
    }

    @Test
    void everySeededValueNamedInTheReferenceIsTheSeededValueOfTheEngine() throws IOException {
        Config config = new Config();
        Set<String> named = new HashSet<>();

        for (Entry entry : entriesOf(readReference())) {
            List<List<String>> rows = steeringTableOf(entry);

            for (List<String> row : rows.subList(1, rows.size())) {
                String source = row.get(2);

                if (!source.startsWith("config.json, seeded ")) {
                    continue;
                }

                String property = row.get(0).replace("`", "").trim();
                String seeded = source.substring("config.json, seeded ".length()).trim();

                assertEquals(config.getProperties().get(property), seeded,
                        "the entry " + entry.name() + " states the value " + property + " is seeded with");
                named.add(property);
            }
        }

        assertTrue(named.containsAll(SEEDED_BUDGETS),
                "every budget of the analyses is named in a steering table, missing: "
                        + SEEDED_BUDGETS.stream().filter(budget -> !named.contains(budget)).toList());
    }

    @Test
    void everySteeringTableSaysWhereItsValuesComeFrom() throws IOException {
        for (Entry entry : entriesOf(readReference())) {
            List<List<String>> rows = steeringTableOf(entry);

            assertFalse(rows.isEmpty(), "the entry " + entry.name() + " has a steering table with a header");
            assertEquals(List.of("Value", "Bounds", "Source"), rows.getFirst(),
                    "the steering table of the entry " + entry.name() + " names the value, its bounds and its source");
            assertTrue(rows.size() > 1,
                    "the steering table of the entry " + entry.name() + " names the values that steer it");

            for (List<String> row : rows.subList(1, rows.size())) {
                assertEquals(3, row.size(),
                        "the steering row " + row + " of the entry " + entry.name() + " fills all three columns");

                String source = row.get(2);

                assertTrue(source.matches("config\\.json, seeded .+") || source.equals("analysis knowledge"),
                        "the steering row " + row + " of the entry " + entry.name()
                                + " names where its value comes from, either a seeded configuration property"
                                + " or the analysis itself, but says " + source);
            }
        }
    }
}
