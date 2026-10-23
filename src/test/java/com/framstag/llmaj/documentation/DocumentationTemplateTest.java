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
            "context-map",
            "modernisation-anchors",
            "hotspots",
            "modernisation-strategy");

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

    /**
     * A project with two seams and an anchor type whose input analysis did not run. The anchors are the
     * places a modernisation could attach to, one ranked list per anchor type.
     */
    private static final String ANCHOR_STATE_JSON = """
            {
              "modernisation_anchors": {
                "reasoning": "The anchors follow from the counted evidence of the modules.",
                "anchors": [
                  {
                    "anchorType": "seam",
                    "module": "core",
                    "element": "com.example.core.OrderPort",
                    "evidence": [
                      "The seam analysis counted 3 implementors of the interface."
                    ],
                    "rank": 1,
                    "candidatePatterns": [
                      "branch-by-abstraction"
                    ],
                    "reasoning": "The interface is the widest abstraction of the module."
                  },
                  {
                    "anchorType": "seam",
                    "module": "core",
                    "element": "com.example.core.PricePort",
                    "evidence": [
                      "The seam analysis counted 1 implementor of the interface."
                    ],
                    "rank": 2,
                    "candidatePatterns": [],
                    "reasoning": "The interface has a single implementor."
                  },
                  {
                    "anchorType": "entry-point",
                    "module": "api",
                    "element": "com.example.api.Application",
                    "evidence": [
                      "The entry point analysis counted 1 class with a main method in the module api."
                    ],
                    "rank": 3,
                    "candidatePatterns": [
                      "retirement"
                    ],
                    "reasoning": "The class starts the process."
                  }
                ],
                "absentTypes": [
                  {
                    "anchorType": "domain",
                    "reason": "The context map analysis did not run, so no relation is available."
                  }
                ]
              }
            }
            """;

    /**
     * A project with counted hotspots, whose counted history is truncated, so that the section has to render
     * the table of the ranked classes and to name the truncation.
     */
    private static final String HOTSPOT_STATE_JSON = """
            {
              "hotspotEvaluation": {
                "reasoning": "The counted hotspots show the classes that change often and that are complicated.",
                "history": {
                  "available": true,
                  "window": "the most recent 500 commits of the repository",
                  "truncated": true,
                  "absence": "",
                  "reason": "",
                  "statement": "The counted history of the project is truncated: the copy of the repository holds the recent commits only. The classes that were changed before the counted window are left out of the ranking."
                },
                "hotspots": [
                  {
                    "className": "com.example.core.OrderService",
                    "module": "core",
                    "rank": 1,
                    "changeCount": 42,
                    "complexity": 118,
                    "methodCount": 9,
                    "reasoning": "The class changes often and carries a high complexity."
                  },
                  {
                    "className": "com.example.core.Order",
                    "module": "core",
                    "rank": 2,
                    "changeCount": 31,
                    "complexity": 64,
                    "methodCount": 6,
                    "reasoning": "The class changes often and carries a complexity of its own."
                  }
                ],
                "limits": [
                  "The change frequency counts the commits of the counted window of the history."
                ]
              }
            }
            """;

    /**
     * A project whose counted history is absent, so that the section has to name the absence instead of
     * rendering a table.
     */
    private static final String ABSENT_HISTORY_STATE_JSON = """
            {
              "hotspotEvaluation": {
                "reasoning": "The project holds no counted hotspots, because its counted history is absent.",
                "history": {
                  "available": false,
                  "window": "",
                  "truncated": false,
                  "absence": "NOT_A_REPOSITORY",
                  "reason": "The project directory is not a git repository.",
                  "statement": ""
                },
                "hotspots": [],
                "limits": []
              }
            }
            """;

    /**
     * A project with a modernisation area that rests on an anchor, and an area type whose input analysis holds
     * nothing, so that the section has to render one table per area and to name the absent type.
     */
    private static final String STRATEGY_STATE_JSON = """
            {
              "modernisationStrategy": {
                "reasoning": "The areas follow the counted cost of the counted evidence.",
                "areas": [
                  {
                    "module": "core",
                    "element": "com.example.core.OrderService",
                    "anchorType": "seam",
                    "patterns": [
                      {
                        "name": "strangler-fig",
                        "evidenceFor": [
                          "The seam analysis counted 3 implementors of the interface."
                        ],
                        "evidenceAgainst": [],
                        "whatWouldMakeItWrong": "The interface would have to be used by the whole project."
                      }
                    ],
                    "costFigures": [
                      {
                        "name": "afferent coupling",
                        "value": "4",
                        "source": "inter-module dependency analysis"
                      },
                      {
                        "name": "price of the cut",
                        "value": "18",
                        "source": "separation ladder"
                      }
                    ],
                    "cost": "The change of the area reaches four modules.",
                    "reasoning": "The interface is the widest abstraction of the module."
                  }
                ],
                "absentTypes": [
                  {
                    "type": "hotspot",
                    "reason": "The hotspot analysis holds no counted hotspot."
                  }
                ]
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
    public void rendersTheModernisationAnchorsPerAnchorType() throws Exception {
        String rendered = renderDocumentation(ANCHOR_STATE_JSON);

        assertTrue(rendered.contains("=== Modernisation Anchors"));
        assertTrue(rendered.contains("see <<modernisation-anchors,Modernisation anchors>"),
                "the section names the reference entry that explains the anchor types");
        assertTrue(rendered.contains("==== Anchors of the type seam"),
                "every anchor type has a table of its own");
        assertTrue(rendered.contains("==== Anchors of the type entry-point"),
                "a type whose anchors do not carry the rank one still has a table of its own");
        assertTrue(rendered.contains("|core |com.example.core.OrderPort |The seam analysis counted 3 implementors of the interface. |1 |branch-by-abstraction |The interface is the widest abstraction of the module."),
                "a row names the anchor, its element, its evidence, its rank and its candidate patterns");
        assertTrue(rendered.contains("|core |com.example.core.PricePort |The seam analysis counted 1 implementor of the interface. |2 |none |The interface has a single implementor."),
                "the second anchor of the type is a row of the same table");
        assertTrue(rendered.contains("|api |com.example.api.Application |The entry point analysis counted 1 class with a main method in the module api. |3 |retirement |The class starts the process."),
                "the anchor of the second type is a row of the table of its type");
        assertTrue(rendered.contains("* domain: The context map analysis did not run, so no relation is available."),
                "an anchor type that is absent is named with its reason");
        assertNoListIsGluedToAParagraph(rendered);
    }

    @Test
    public void rendersTheHotspotsWithTheirTwoFigures() throws Exception {
        String rendered = renderDocumentation(HOTSPOT_STATE_JSON);

        assertTrue(rendered.contains("see <<hotspots,Hotspots>>"),
                "the section names the reference entry that explains the join");
        assertTrue(rendered.contains("|com.example.core.OrderService |core |1 |42 |118"),
                "a row names the class, its module, its rank, its change count and its complexity");
        assertTrue(rendered.contains("|com.example.core.Order |core |2 |31 |64"),
                "the second hotspot is a row of the same table");
        assertTrue(rendered.contains("The counted history of the project is truncated"),
                "the section names the truncation of the counted history");
        assertTrue(rendered.contains("left out of the ranking"),
                "the section names what the truncation leaves out");
        assertNoListIsGluedToAParagraph(rendered);
    }

    @Test
    public void rendersTheModernisationStrategyPerArea() throws Exception {
        String rendered = renderDocumentation(STRATEGY_STATE_JSON);

        assertTrue(rendered.contains("=== Modernisation Strategy"));
        assertTrue(rendered.contains("see <<modernisation-strategy,Modernisation strategy>>"),
                "the section names the reference entry that explains the cost of an area");
        assertTrue(rendered.contains("The order follows the counted cost and is not a decision"),
                "the section states that the order is an order and not a verdict");
        assertTrue(rendered.contains("the analysis names no area as the one to start with"),
                "the section names no area as the one to start with");
        assertTrue(rendered.contains("==== Modernisation area of the module \"core\" at \"com.example.core.OrderService\""),
                "every area has a heading of its own");
        assertTrue(rendered.contains("|core / com.example.core.OrderService |strangler-fig |The seam analysis counted 3 implementors of the interface. |none |The interface would have to be used by the whole project. |afferent coupling: 4 (inter-module dependency analysis), price of the cut: 18 (separation ladder)"),
                "a row names the area, its pattern, the evidence for and against it and the figures of the cost");
        assertTrue(rendered.contains("The change of the area reaches four modules."),
                "the cost of the area is rendered");
        assertTrue(rendered.contains("* hotspot: The hotspot analysis holds no counted hotspot."),
                "an area type that is absent is named with its reason");
        assertNoListIsGluedToAParagraph(rendered);
    }

    @Test
    public void rendersTheAbsentHistoryOfTheHotspotsInsteadOfATable() throws Exception {
        String rendered = renderDocumentation(ABSENT_HISTORY_STATE_JSON);

        assertTrue(rendered.contains("The counted history of the project is absent (NOT_A_REPOSITORY)"),
                "the section names the absence of the counted history");
        assertTrue(rendered.contains("The project directory is not a git repository."),
                "the section names the reason of the absence");
        assertFalse(rendered.contains("|Class |Module |Rank |Change count |Complexity"),
                "an absent history is not rendered as an empty table");
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
