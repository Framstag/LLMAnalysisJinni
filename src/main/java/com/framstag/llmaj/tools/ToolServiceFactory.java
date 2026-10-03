package com.framstag.llmaj.tools;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.MCPServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

public class ToolServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(ToolServiceFactory.class);

    public static ToolService getToolService(Config config,
                                             AnalysisContext analysisContext) {

        HashMap<ToolSpecification, ToolExecutor> mcpServersDefinitions = new HashMap<>();

        int mcpServerIndex = 0;
        for (MCPServer server : config.getMcpServers())  {
            logger.info("Initializing MCP Server: '{}' of type {}",
                    server.getName(),
                    server.getType());

            McpTransport transport = null;

            switch (server.getType()) {
                case HTTP -> transport = StreamableHttpMcpTransport.builder()
                        .url(server.getUrl().toString())
                        .logRequests(config.isLogRequests())
                        .logResponses(config.isLogResponses())
                        .build();
                case STDIO -> transport = StdioMcpTransport.builder()
                        .command(server.getCommand())
                        .logEvents(server.isLogEvents())
                        .environment(server.getEnvironment())
                        .build();
            }

            McpClient mcpClient = DefaultMcpClient.builder()
                    .key("MCPServer_"+mcpServerIndex)
                    .transport(transport)
                    .build();

            ToolExecutor mcpToolExecutor = new McpToolExecutor(mcpClient);

            List<ToolSpecification> toolSpecifications = mcpClient.listTools();

            for (ToolSpecification toolSpecification : toolSpecifications) {
                logger.info("- Tool: '{}'", toolSpecification.name());

                mcpServersDefinitions.put(toolSpecification,mcpToolExecutor);
            }

            mcpServerIndex++;
        }

        ToolService toolService = new ToolService();

        // A tool call the framework cannot carry out is answered instead of ending the step: an
        // argument error and an execution error both go back to the model as the tool result, so
        // the model can correct the call and continue. This is the framework's own recommendation
        // for argument errors, and it makes the two error paths behave alike.
        toolService.argumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm());
        toolService.executionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm());

        // The engine's own tool loop enforces this bound and reads it back from the service, so the
        // configured value and the enforced value cannot diverge. The service's hallucinated tool
        // name strategy stays at its default: the engine's loop answers an unknown tool name itself.
        toolService.maxToolCallingRoundTrips(config.getMaxToolRoundTrips());

        toolService.tools(ToolFactory.getToolInstanceList(analysisContext));
        toolService.tools(mcpServersDefinitions);

        return toolService;
    }
}
