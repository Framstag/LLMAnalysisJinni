package com.framstag.llmaj.documentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The diagrams are not rendered by the engine: the template prints the stored PlantUML source verbatim and the
 * reader's toolchain draws it. So what the template has to preserve is the source itself, arrows included, and
 * it has to say why a diagram is missing rather than leaving a hole.
 */
class IntraModuleDocumentationTemplateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private String render(ObjectNode state) throws Exception {
        Path templateDir = Path.of("analysis", "software-architecture", "documentation");
        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(templateDir.toString(), ".hbs"));
        Template template = handlebars.compile("Documentation.adoc");

        return template.apply(new JsonNodeModelWrapper(state));
    }

    private ObjectNode stateWithModules() {
        ObjectNode state = mapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");
        ObjectNode module = modules.addObject();
        module.put("name", "core");
        module.put("path", "");
        module.put("root", true);

        return state;
    }

    private ObjectNode stateWithDiagrams() {
        ObjectNode state = stateWithModules();

        ObjectNode diagrams = state.putObject("dependencyDiagrams").putObject("core");
        diagrams.put("moduleName", "core");
        diagrams.put("reasoning", "Diagrams for module 'core'.");

        ObjectNode overview = diagrams.putObject("overview");
        overview.put("name", "overview");
        overview.put("title", "core - packages");
        overview.put("caption", "2 package(s) covering 3 production class(es), edges below weight 2 omitted"
                + " (0 omitted).");
        overview.put("source", "@startuml\ntitle core - packages\npackage \"demo\" as P0\n"
                + "package \"demo.api\" as P1\nP0 --> P1 : 9\n@enduml\n");

        ArrayNode groupDiagrams = diagrams.putArray("groupDiagrams");
        ObjectNode group = groupDiagrams.addObject();
        group.put("name", "group-1");
        group.put("title", "core - group 1");
        group.put("caption", "2 of 2 class(es) of group 1 of 2, edges below weight 2 omitted (1 omitted).");
        group.put("source", "@startuml\ntitle core - group 1\nclass \"demo.Service\" as C0\n"
                + "class \"demo.Repository\" as C1\nC0 --> C1 : 9\nC1 ..> C0 : extends/implements\n@enduml\n");

        diagrams.putArray("notDrawn").add("No class detail for group 2 of 2 (30 class(es)): above the node"
                + " budget of 40.");

        return state;
    }

    @Test
    void theDiagramSourceIsPrintedVerbatim() throws Exception {
        String rendered = render(stateWithDiagrams());

        assertTrue(rendered.contains("=== Dependency Diagrams per Module"));
        assertTrue(rendered.contains("==== Module \"core\""));
        assertTrue(rendered.contains("[plantuml]"), "the reader's toolchain needs the block to draw it");
        assertTrue(rendered.contains("P0 --> P1 : 9"),
                "the arrow syntax has to survive template rendering, got: " + rendered);
        assertTrue(rendered.contains("C1 ..> C0 : extends/implements"),
                "a structural relation has to survive as well");
    }

    @Test
    void theCaptionsAndTheOmissionsArePrinted() throws Exception {
        String rendered = render(stateWithDiagrams());

        assertTrue(rendered.contains("2 package(s) covering 3 production class(es)"),
                "the caption of the overview belongs in the document");
        assertTrue(rendered.contains("edges below weight 2 omitted (1 omitted)"),
                "the caption of a class detail diagram belongs in the document");
        assertTrue(rendered.contains("* No class detail for group 2 of 2"),
                "a diagram that was not drawn has to be stated, got: " + rendered);
    }

    @Test
    void aMissingDiagramIsExplainedAndNotLeftAsAHole() throws Exception {
        String rendered = render(stateWithModules());

        assertTrue(rendered.contains("No dependency diagrams were generated."),
                "the section has to say why it is empty");
        assertFalse(rendered.contains("[plantuml]"));
    }

    @Test
    void theGodClassRankingIsRenderedWhenItIsPresent() throws Exception {
        ObjectNode state = stateWithDiagrams();
        ObjectNode godClass = state.putObject("godClassEvaluationAll");
        godClass.put("reasoning", "Overall god class reasoning.");

        ArrayNode moduleEvaluations = godClass.putArray("moduleEvaluations");
        ObjectNode moduleEvaluation = moduleEvaluations.addObject();
        moduleEvaluation.put("moduleName", "core");
        moduleEvaluation.put("reasoning", "Core has one candidate.");

        ArrayNode evaluations = moduleEvaluation.putArray("evaluations");
        ObjectNode evaluation = evaluations.addObject();
        evaluation.put("aspect", "God class candidate");
        evaluation.put("expectation", "Classes should stay cohesive.");
        evaluation.put("reasoning", "One class dominates the module.");
        evaluation.put("finding", "OrderService ranks 1 of 214.");
        evaluation.put("recommendation", "Extract the pricing responsibility.");
        evaluation.put("urgency", "HIGH");
        evaluation.put("criticality", "HIGH");

        String rendered = render(state);

        assertTrue(rendered.contains("=== God Class Candidates per Module"));
        assertTrue(rendered.contains("|God class candidate |HIGH |HIGH |Classes should stay cohesive."),
                "the finding table has to be rendered, got: " + rendered);
    }

    @Test
    void theMissingGodClassRankingIsExplained() throws Exception {
        String rendered = render(stateWithModules());

        assertTrue(rendered.contains("No god class ranking was produced"),
                "a missing ranking has to be explained, got: " + rendered);
    }
}
