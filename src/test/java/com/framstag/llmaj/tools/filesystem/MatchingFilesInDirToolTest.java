package com.framstag.llmaj.tools.filesystem;

import com.framstag.llmaj.AnalysisContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tool that lists the files of a directory that match a wildcard is called by the model, and the
 * model leaves the flag that skips the dot directories out more often than not. The flag must
 * therefore be optional in the schema, and the default of its description must be the default of the
 * tool.
 */
public class MatchingFilesInDirToolTest {

    private static final String TOOL_NAME = "filesystem_get_matching_files_in_dir_recursively";

    private AnalysisContext context;
    private FilesystemTool filesystemTool;

    @BeforeEach
    void initializeContext() {
        context = new AnalysisContext(
                Paths.get("").toAbsolutePath(),
                Paths.get("").toAbsolutePath(),
                Collections.emptyMap(),
                null);

        filesystemTool = new FilesystemTool(context);
    }

    @Test
    void theFlagToSkipTheDotDirectoriesIsOptionalInTheSchema() {
        ToolService toolService = new ToolService();
        toolService.tools(List.of(filesystemTool));

        ToolSpecification specification = toolService.toolSpecifications().stream()
                .filter(spec -> TOOL_NAME.equals(spec.name()))
                .findFirst()
                .orElseThrow();

        List<String> required = specification.parameters().required();

        assertFalse(required.contains("skipDotDirs"),
                "the model must be allowed to leave the flag out, but these are required: " + required);
        assertTrue(required.contains("path"), "the path is needed and must stay required");
        assertTrue(required.contains("wildcards"), "the wildcards are needed and must stay required");
    }

    @Test
    void theToolRunsWhenTheModelLeavesTheFlagOut(@TempDir Path projectRoot) throws IOException {
        Files.createDirectories(projectRoot.resolve("module"));
        Files.writeString(projectRoot.resolve("module/pom.xml"), "<project/>");
        Files.createDirectories(projectRoot.resolve(".hidden"));
        Files.writeString(projectRoot.resolve(".hidden/pom.xml"), "<project/>");

        AnalysisContext tempContext = new AnalysisContext(
                projectRoot,
                projectRoot,
                Collections.emptyMap(),
                null);
        FilesystemTool tool = new FilesystemTool(tempContext);

        ToolExecutionResult result = execute(tool, "{\"path\":\"\",\"wildcards\":[\"pom.xml\"]}");
        String text = String.valueOf(result.result());

        assertFalse(result.isError(), "the call must not fail, but it failed with: " + result);
        assertTrue(text.contains("module"),
                "the build file of the module must be found, but the result was: " + result);
        assertFalse(text.contains(".hidden"),
                "the dot directories must be skipped by default, but the result was: " + result);
    }

    @Test
    void theToolRunsWhenTheModelAsksForTheDotDirectories(@TempDir Path projectRoot) throws IOException {
        Files.createDirectories(projectRoot.resolve(".hidden"));
        Files.writeString(projectRoot.resolve(".hidden/pom.xml"), "<project/>");

        AnalysisContext tempContext = new AnalysisContext(
                projectRoot,
                projectRoot,
                Collections.emptyMap(),
                null);
        FilesystemTool tool = new FilesystemTool(tempContext);

        ToolExecutionResult result = execute(tool, "{\"path\":\"\",\"wildcards\":[\"pom.xml\"],\"skipDotDirs\":false}");
        String text = String.valueOf(result.result());

        assertFalse(result.isError(), "the call must not fail, but it failed with: " + result);
        assertTrue(text.contains(".hidden"),
                "the dot directories must be found when they are asked for, but the result was: " + result);
    }

    private ToolExecutionResult execute(FilesystemTool tool, String arguments) {
        ToolService toolService = new ToolService();
        toolService.tools(List.of(tool));

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        return ToolService.executeWithErrorHandling(request,
                toolService.toolExecutors().get(TOOL_NAME),
                null,
                ToolArgumentsErrorHandler.sendExceptionMessageToLlm(),
                ToolExecutionErrorHandler.sendExceptionMessageToLlm());
    }
}
