package com.framstag.llmaj.tools;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.tools.filesystem.FilesystemTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class ToolServiceFactoryTest {

    @Test
    void toolParameterNamesAreAvailable() {
        AnalysisContext context = new AnalysisContext(
                Paths.get("").toAbsolutePath(),
                Paths.get("").toAbsolutePath(),
                Collections.emptyMap(),
                null);

        ToolService toolService = new ToolService();
        toolService.tools(List.of(new FilesystemTool(context)));

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
}
