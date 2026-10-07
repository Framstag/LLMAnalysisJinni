package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigStorer;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.state.StateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The diagrams are written into the analysis state, and the engine persists that state at the end of a run.
 * This checks the whole path with the objects the run itself uses: the state node {@code AnalyseCmd} hands to
 * the tools, and the save that turns it into {@code analysis.json}, where the documentation finds it.
 */
class DependencyDiagramStatePersistenceTest {
    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name, ClassReference... references) {
        Clazz clazz = new Clazz(name, null);
        clazz.addMethod(new Method("run", "()V"));

        return new BuildUnit(name, true, false, List.of(), List.of(references), List.of(clazz));
    }

    private Module fixtureModule() {
        Package demo = new Package("demo");
        demo.addBuildUnit(buildUnit("demo.Service",
                new ClassReference("demo.Repository", 9, 9, false)));
        demo.addBuildUnit(buildUnit("demo.Repository",
                new ClassReference("demo.Model", 1, 1, false)));
        demo.addBuildUnit(buildUnit("demo.Model"));

        Module module = new Module("core");
        module.addPackage(demo);

        return module;
    }

    @Test
    void theStoredDiagramsReachAnalysisJson() throws Exception {
        Config config = new Config();
        config.setModelURL(URI.create("http://localhost:11434").toURL());
        config.setModelName("qwen2.5:7b");
        config.setProjectDirectory(tempDir);
        config.setAnalysisDirectory(Path.of("analysis/software-architecture"));
        ConfigStorer.save(config, tempDir);

        Path javaDirectory = tempDir.resolve("Java");
        Files.createDirectories(javaDirectory);
        ObjectMapperFactory.getJSONObjectMapperInstance()
                .writeValue(javaDirectory.resolve("Java_core.json").toFile(), fixtureModule());

        StateManager stateManager = StateManager.initializeState(tempDir);
        ObjectNode state = stateManager.getAnalysisState();

        ArrayNode modules = state.putObject("modules").putArray("modules");
        ObjectNode moduleNode = modules.addObject();
        moduleNode.put("name", "core");
        moduleNode.put("path", ".");
        ArrayNode languages = moduleNode.putObject("programmingLanguages").putArray("programmingLanguages");
        languages.addObject().put("name", "Java").put("version", "unknown");

        AnalysisContext context = new AnalysisContext(tempDir, tempDir, config.getProperties(), state);

        Map<String, Object> result = new JavaTool(context).generateDependencyDiagrams("core");
        assertEquals("OK", result.get("status"));

        stateManager.saveState();

        String analysisJson = Files.readString(tempDir.resolve("analysis.json"));

        assertTrue(analysisJson.contains("dependencyDiagrams"),
                "the state the engine saves has to carry the diagrams");
        assertTrue(analysisJson.contains("@startuml"),
                "the saved state has to carry the diagram source, got: "
                        + analysisJson.substring(0, Math.min(400, analysisJson.length())));
        assertTrue(analysisJson.contains("demo.Service"),
                "the diagram has to name the classes of the module");
    }
}
