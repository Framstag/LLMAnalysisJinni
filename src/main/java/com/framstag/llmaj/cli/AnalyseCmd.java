package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.ChatExecutionContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.config.MCPServer;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonHelper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.lc4j.ChatExecutor;
import com.framstag.llmaj.lc4j.ChatModelFactory;
import com.framstag.llmaj.state.StateManager;
import com.framstag.llmaj.tasks.TaskDefinition;
import com.framstag.llmaj.tasks.TaskManager;
import com.framstag.llmaj.tools.ToolFactory;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "analyse", description = "Analyse the project")
public class AnalyseCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(AnalyseCmd.class);

    @Option(names={"--log-request"}, arity = "1", defaultValue = "false", description = "Activate langchain4j low-level log of chat requests")
    boolean logRequest = false;

    @Option(names={"--log-response"}, arity = "1", defaultValue = "false", description = "Activate langchain4j low-level log of chat responses")
    boolean logResponse = false;

    @Option(names={"-o","--executeOnly"}, arity = "1..*", description = "A list of task ids, that should only be executed")
    Set<String> executeOnly = new HashSet<>();

    @Option(names={"--single-step"}, arity = "1", defaultValue = "false", description = "Stop execution after one task")
    boolean singleStep = false;

    @Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    Path workingDirectory;

    private LinkedList<ChatMessage> resolveChatMessages(Config config,
                                                        Handlebars templateEngine,
                                                        TaskDefinition task,
                                                        Object stateObject) throws IOException {
        LinkedList<ChatMessage> messages = new LinkedList<>();

        String origSystemPrompt = null;
        String origUserPrompt = null;

        if (task.getSystemPrompt() != null) {
            origSystemPrompt = Files.readString(config.getAnalysisDirectory().resolve(task.getSystemPrompt()));
        }

        if (task.getPrompt() != null) {
            origUserPrompt = Files.readString(config.getAnalysisDirectory().resolve(task.getPrompt()));
        }

        String systemPrompt;
        String userPrompt;

        if (origSystemPrompt != null) {
            Template template = templateEngine.compileInline(origSystemPrompt);
            systemPrompt = template.apply(stateObject);
            messages.add(SystemMessage.from(systemPrompt));
        }

        if (origUserPrompt!= null) {
            Template template = templateEngine.compileInline(origUserPrompt);
            userPrompt = template.apply(stateObject);

            messages.add(UserMessage.from(userPrompt));
        }

        return messages;
    }


    @Override
    public Integer call() throws Exception {
        Config config;

        try {
            logger.info("Loading config from workspace '{}'...", workingDirectory);
            config = ConfigLoader.load(workingDirectory);
            config.dumpToLog();
        } catch (IOException e) {
            logger.error("Cannot load config file", e);
            return 1;
        }

        ChatModel model = ChatModelFactory.getChatModel(config);

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

        TaskManager taskManager = TaskManager.initializeTasks(config.getAnalysisDirectory(),
                workingDirectory,
                executeOnly);

        StateManager stateManager = StateManager.initializeState(workingDirectory);

        var templateEngine = HandlebarsFactory.create()
                .with(new FileTemplateLoader(config.getAnalysisDirectoryAsString(),
                        ""));

        AnalysisContext analysisContext = new AnalysisContext(
                config.getProjectDirectory(),
                workingDirectory,
                stateManager.getAnalysisState());

        HashMap<ToolSpecification, ToolExecutor> mcpServersDefinitions = new HashMap<>();

        int mcpServerIndex = 0;
        for (MCPServer server : config.getMcpServers())  {
            logger.info("Initializing MCP Server: '{}'", server.getName());

            McpTransport transport = StreamableHttpMcpTransport.builder()
                    .url(server.getUrl().toString())
                    .logRequests(logRequest)
                    .logResponses(logResponse)
                    .build();

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

        taskManager.dump();

        while (taskManager.hasPendingTasks()) {
            TaskDefinition task = taskManager.getNextTask();

            String jsonResponseRawSchema = Files.readString(config.getAnalysisDirectory().resolve(task.getResponseFormat()));
            JsonNode jsonResponseSchema = mapper.readTree(jsonResponseRawSchema);

            if (task.hasLoopOn()) {
                if (!stateManager.startLoop(task.getLoopOn())) {
                    continue;
                }

                int lastSuccessfullyExecutedLoopIndex = taskManager.getTaskSuccessFullLoopIndex(task);

                if (lastSuccessfullyExecutedLoopIndex>=0) {
                    logger.info("Last successfully executed loop index: {}", lastSuccessfullyExecutedLoopIndex);
                }

                while (stateManager.canLoop()) {
                    stateManager.loopNext();

                    logger.info("===>[{}] Task: {} - {}",
                            stateManager.getLoopIndex(),
                            task.getId(),
                            task.getName());

                    if (stateManager.getLoopIndex()<=lastSuccessfullyExecutedLoopIndex) {
                        logger.info("Loop index was already successfully executed, skipping...");
                        continue;
                    }

                    LinkedList<ChatMessage> messages = resolveChatMessages(config,
                            templateEngine,
                            task,
                            stateManager.getStateObject());

                    ChatExecutionContext execContext =
                            new ChatExecutionContext(config,
                                    model,
                                    toolService,
                                    mapper);

                    JsonNode taskResultJson = new ChatExecutor().executeMessages(config,
                            execContext,
                            messages,
                            jsonResponseRawSchema,
                            jsonResponseSchema);

                    if (taskResultJson != null) {
                        logger.info("===>[{}] {}: {}",
                                stateManager.getLoopIndex(),
                                JsonHelper.getSchemaName(jsonResponseSchema),
                                taskResultJson.toPrettyString());

                        stateManager.updateLoopState(task.getResponseProperty(), taskResultJson);
                        stateManager.saveState();
                        taskManager.markTaskAsLoopProcessing(task, stateManager.getLoopIndex());
                    } else {
                        logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
                    }
                }

                stateManager.endLoop();
            }
            else {
                logger.info("===> Task: {} - {}",
                        task.getId(),
                        task.getName());

                LinkedList<ChatMessage> messages = resolveChatMessages(config,
                        templateEngine,
                        task,
                        stateManager.getStateObject());

                ChatExecutionContext execContext =
                        new ChatExecutionContext(config,
                                model,
                                toolService,
                                mapper);

                JsonNode taskResultJson = new ChatExecutor().executeMessages(config,
                        execContext,
                        messages,
                        jsonResponseRawSchema,
                        jsonResponseSchema);

                if (taskResultJson != null) {
                    logger.info("===> {}: {}", JsonHelper.getSchemaName(jsonResponseSchema), taskResultJson.toPrettyString());

                    stateManager.updateState(task.getResponseProperty(), taskResultJson);
                    stateManager.saveState();
                } else {
                    logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
                }
            }

            taskManager.markTaskAsSuccessful(task);

            if (singleStep) {
                break;
            }
        }

        return 0;
    }
}
