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
        toolService.tools(ToolFactory.getToolInstanceList(analysisContext));
        toolService.tools(mcpServersDefinitions);

        return toolService;
    }
}
