package com.framstag.llmaj.tools;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.tools.filesystem.FilesystemTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class ToolServiceFactoryTest {

    private AnalysisContext analysisContext() {
        return new AnalysisContext(
                Paths.get("").toAbsolutePath(),
                Paths.get("").toAbsolutePath(),
                Collections.emptyMap(),
                null);
    }

    private ToolErrorContext errorContext() {
        return ToolErrorContext.builder()
                .toolExecutionRequest(ToolExecutionRequest.builder()
                        .id("1")
                        .name("filesystem_get_all_files_in_dir")
                        .arguments("{\"path\":\"\"}")
                        .build())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    @Test
    void toolParameterNamesAreAvailable() {
        ToolService toolService = new ToolService();
        toolService.tools(List.of(new FilesystemTool(analysisContext())));

        List<ToolSpecification> specs = toolService.toolSpecifications();

        ToolSpecification spec = specs.stream()
                .filter(s -> s.name().equals("filesystem_get_all_files_in_dir"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool specification not found"));

        java.util.Map<String, dev.langchain4j.model.chat.request.json.JsonSchemaElement> params =
                spec.parameters().properties();

        Assertions.assertTrue(params.containsKey("path"),
                "parameter must be named 'path' (requires javac -parameters), was: " + params.keySet());
    }

    @Test
    void builtServiceAnswersToolErrorsInsteadOfFailingTheStep() {
        Config config = new Config();

        ToolService toolService = ToolServiceFactory.getToolService(config, analysisContext());

        Assertions.assertTrue(toolService.hasExplicitArgumentsErrorHandler(),
                "the engine must configure the argument error handler explicitly");

        ToolErrorHandlerResult argumentResult = toolService.argumentsErrorHandler()
                .handle(new IllegalArgumentException("cannot coerce the arguments"), errorContext());

        Assertions.assertTrue(argumentResult.text().contains("cannot coerce the arguments"),
                "an argument error must become a tool result for the model, got: " + argumentResult.text());

        ToolErrorHandlerResult executionResult = toolService.executionErrorHandler()
                .handle(new IllegalStateException("the tool failed"), errorContext());

        Assertions.assertTrue(executionResult.text().contains("the tool failed"),
                "an execution error must become a tool result for the model, got: " + executionResult.text());
    }

    @Test
    void builtServiceCarriesTheConfiguredToolRoundBound() {
        Config config = new Config();
        config.setMaxToolRoundTrips(7);

        ToolService toolService = ToolServiceFactory.getToolService(config, analysisContext());

        Assertions.assertEquals(7, toolService.maxToolCallingRoundTrips(),
                "the bound the loop enforces must be the one the configuration asks for");
    }
}
