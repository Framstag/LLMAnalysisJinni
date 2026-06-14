package com.framstag.llmaj.documentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentationTemplateTest {

    @Test
    public void rendersBatchDocumentationSections() throws Exception {
        String stateJson = """
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

        var mapper = new ObjectMapper();
        var state = new JsonNodeModelWrapper(mapper.readTree(stateJson));
        Path templateDir = Path.of("analysis", "software-architecture", "documentation");
        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(templateDir.toString(), ".hbs"));
        Template template = handlebars.compile("Documentation.md");

        String rendered = template.apply(state);

        assertTrue(rendered.contains("### Analysis of Cyclomatic Complexity per Module"));
        assertTrue(rendered.contains("#### Module \"core\""));
        assertTrue(rendered.contains("|Cyclomatic complexity|MEDIUM|MEDIUM|Methods should stay below threshold.|Several methods exceed the threshold.|High complexity methods found.|Extract smaller methods.|"));
        assertTrue(rendered.contains("### Batch Java Report Collection"));
        assertTrue(rendered.contains("|core|REUSED|Java|Java_core|Existing raw report reused.|"));
        assertFalse(rendered.contains("legacy raw report descriptors"));
        assertFalse(rendered.contains("Batch Java Metric Evaluations"));
    }
}
