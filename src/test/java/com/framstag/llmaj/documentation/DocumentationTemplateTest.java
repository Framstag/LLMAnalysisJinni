package com.framstag.llmaj.documentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentationTemplateTest {
    private static final Path TEMPLATE_DIR = Path.of("analysis", "software-architecture", "documentation");
    private static final Path REFERENCE = TEMPLATE_DIR.resolve("AnalysisReference.adoc.hbs");

    /**
     * The analyses whose sections have to name the reference entry that explains them, in the order the report
     * renders them. A section that loses its pointer leaves the reader of the report with an unexplained number.
     */
    private static final List<String> DOCUMENTED_SECTIONS = List.of(
            "cyclomatic-complexity",
            "method-nesting-depth",
            "method-visibility",
            "class-inheritance",
            "method-complexity",
            "field-visibility",
            "class-cohesion",
            "class-coupling",
            "test-coverage",
            "circular-dependencies",
            "method-count",
            "documentation-ratio",
            "data-class-candidates",
            "boolean-parameters",
            "annotation-usage",
            "package-tangles",
            "import-diversity",
            "inter-module-dependencies",
            "class-reference-structure",
            "dependency-diagrams",
            "god-class-candidates",
            "domain-language",
            "context-map");

    private static final String STATE_JSON = """
            {
              "modules": {
                "modules": [
                  {
                    "name": "core",
                    "path": "",
                    "root": true
                  }
                ]
              },
              "cyclomaticComplexityEvaluationAll": {
                "reasoning": "Overall complexity reasoning.",
                "moduleEvaluations": [
                  {
                    "moduleName": "core",
                    "reasoning": "Core has moderate complexity.",
                    "evaluations": [
                      {
                        "aspect": "Cyclomatic complexity",
                        "expectation": "Methods should stay below threshold.",
                        "reasoning": "Several methods exceed the threshold.",
                        "finding": "High complexity methods found.",
                        "recommendation": "Extract smaller methods.",
                        "urgency": "MEDIUM",
                        "criticality": "MEDIUM"
                      }
                    ]
                  }
                ]
              },
              "analysisReportsAll": {
                "reasoning": "Batch collection generated reports.",
                "reports": [
                  {
                    "moduleName": "core",
                    "status": "REUSED",
                    "programmingLanguage": "Java",
                    "reportName": "Java_core",
                    "reasoning": "Existing raw report reused."
                  }
                ],
                "skipped": []
              }
            }
            """;

    /**
     * One module whose diagram budget is used up, so the report has to list the diagrams it did not draw. A
     * bullet list that is glued to the paragraph above it is printed as text instead of as a list, so this
     * fixture is what proves the list is separated.
     */
    private static final String NOT_DRAWN_STATE_JSON = """
            {
              "modules": {
                "modules": [
                  {
                    "name": "core",
                    "path": "",
                    "root": true
                  }
                ]
              },
              "dependencyDiagrams": {
                "core": {
                  "moduleName": "core",
                  "reasoning": "Diagrams for module 'core'.",
                  "overview": {
                    "caption": "one package",
                    "source": "@startuml\\npackage \\"demo\\" as P0\\n@enduml\\n"
                  },
                  "groupDiagrams": [
                    {
                      "caption": "one group",
                      "source": "@startuml\\nclass \\"demo.A\\" as C0\\n@enduml\\n"
                    }
                  ],
                  "notDrawn": [
                    "No class detail for group 13 of 75 (2 class(es)): the budget of 12 class detail diagrams is used up.",
                    "No class detail for group 14 of 75 (2 class(es)): the budget of 12 class detail diagrams is used up."
                  ]
                }
              }
            }
            """;

    private static String renderDocumentation() throws IOException {
        return renderDocumentation(STATE_JSON);
    }

    private static String renderDocumentation(String stateJson) throws IOException {
        var mapper = new ObjectMapper();
        var state = new JsonNodeModelWrapper(mapper.readTree(stateJson));
        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(TEMPLATE_DIR.toString(), ".hbs"));
        Template template = handlebars.compile("Documentation.adoc");

        return template.apply(state);
    }

    /**
     * AsciiDoc prints the marker of a list that is not separated from the paragraph above it as text, which
     * turns a list into one long line. Every bullet has to start a list, so the line above it is blank or is
     * another bullet.
     */
    private static void assertNoListIsGluedToAParagraph(String rendered) {
        List<String> lines = rendered.lines().toList();

        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String above = lines.get(index - 1);

            // An indented line is the continuation of the item above it, and the next item may follow that
            // directly without a blank line. Only a paragraph line at column zero swallows the marker.
            if (line.startsWith("* ") && !above.isBlank() && !above.startsWith("*")
                    && !above.startsWith("+") && !above.startsWith("[") && !above.startsWith("...")
                    && above.equals(above.stripLeading())) {
                throw new AssertionError("the bullet on line " + (index + 1) + " is glued to the paragraph '"
                        + above + "', so AsciiDoc prints it as text: " + line);
            }
        }
    }

    private static List<String> anchorsOf(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);

        return matcher.results().map(match -> match.group(1)).toList();
    }

    @Test
    public void rendersBatchDocumentationSections() throws Exception {
        String rendered = renderDocumentation();

        assertTrue(rendered.contains("=== Analysis of Cyclomatic Complexity per Module"));
        assertTrue(rendered.contains("==== Module \"core\""));
        assertTrue(rendered.contains("|Cyclomatic complexity |MEDIUM |MEDIUM |Methods should stay below threshold. |Several methods exceed the threshold. |High complexity methods found. |Extract smaller methods."));
        assertTrue(rendered.contains("=== Batch Java Report Collection"));
        assertTrue(rendered.contains("|core |REUSED |Java |Java_core |Existing raw report reused."));
        assertFalse(rendered.contains("legacy raw report descriptors"));
        assertFalse(rendered.contains("Batch Java Metric Evaluations"));
    }

    @Test
    public void theNotDrawnListIsSeparatedFromItsParagraph() throws Exception {
        String rendered = renderDocumentation(NOT_DRAWN_STATE_JSON);

        assertTrue(rendered.contains("Not drawn:\n\n* No class detail for group 13 of 75"),
                "the list needs a blank line after the paragraph, otherwise its markers are printed as text");
        assertTrue(rendered.contains("group 13 of 75 (2 class(es)): the budget of 12 class detail diagrams"
                        + " is used up.\n* No class detail for group 14 of 75"),
                "one bullet per omission, each on its own line");
        assertNoListIsGluedToAParagraph(rendered);
    }

    @Test
    public void noBulletIsGluedToAParagraph() throws Exception {
        assertNoListIsGluedToAParagraph(renderDocumentation());
    }

    @Test
    public void rendersTheAnalysisReferenceAsAChapter() throws Exception {
        String rendered = renderDocumentation();
        String reference = Files.readString(REFERENCE);

        assertTrue(rendered.contains(reference),
                "the report carries the analysis reference verbatim, so both say the same about an analysis");
        assertEquals(1, countOccurrences(rendered, "== Analysis Methods and Interpretation"),
                "the reference is rendered as one chapter and not once per section");
    }

    @Test
    public void everyDocumentedSectionPointsToItsReferenceEntry() throws Exception {
        String rendered = renderDocumentation();
        String reference = Files.readString(REFERENCE);

        List<String> pointers = anchorsOf(rendered, Pattern.compile("see <<([a-z-]+),"));
        List<String> entries = anchorsOf(reference, Pattern.compile("\\[\\[([a-z-]+)]]"));

        assertEquals(DOCUMENTED_SECTIONS, pointers,
                "every documented section names its reference entry, in the order the report renders them");
        assertTrue(Set.copyOf(entries).containsAll(pointers),
                "every pointer names an entry the reference holds, unknown: "
                        + pointers.stream().filter(pointer -> !entries.contains(pointer)).collect(Collectors.toSet()));
        assertEquals(Set.copyOf(DOCUMENTED_SECTIONS), Set.copyOf(entries),
                "the reference holds no entry that no section points at");
    }

    private int countOccurrences(String text, String value) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }

        return count;
    }
}
