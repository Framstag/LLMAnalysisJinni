package com.framstag.llmaj.cli;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.ChatExecutionContext;
import com.framstag.llmaj.ChatListener;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonHelper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.lc4j.ChatExecutor;
import com.framstag.llmaj.state.StateManager;
import com.framstag.llmaj.tasks.TaskDefinition;
import com.framstag.llmaj.tasks.TaskManager;
import com.framstag.llmaj.tools.ToolFactory;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "analyse", description = "Analyse the project")
public class AnalyseCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(AnalyseCmd.class);

    private static final String ANSWER_ONLY_WITH_THE_FOLLOWING_JSON = "\nYou must answer strictly in the following JSON format: ";

    @Option(names={"-u","--modelUrl"}, arity = "1", description = "The URL of the ollama server")
    URL modelUrl;

    @Option(names={"-m","--model"}, arity = "1", description = "The name of the model to use")
    String modelName;

    @Option(names={"-j","--json-response"}, arity = "1", defaultValue = "false", description = "Enforce json response")
    boolean jsonResponse = false;

    @Option(names={"--log-request"}, arity = "1", defaultValue = "false", description = "Activate langchain4j low-level log of chat requests")
    boolean logRequest = false;

    @Option(names={"--log-response"}, arity = "1", defaultValue = "false", description = "Activate langchain4j low-level log of chat responses")
    boolean logResponse = false;

    @Option(names={"-o","--executeOnly"}, arity = "1..*", description = "A list of task ids, that should only be executed")
    Set<String> executeOnly = new HashSet<>();

    @Option(names={"--chatWindowsSize"}, arity = "1", defaultValue="50", description = "The number of messages to memorize between prompts")
    int chatWindowSize;

    @Option(names={"--requestTimeout"}, arity = "1", defaultValue="120", description = "Request timeout in minutes")
    int requestTimeout;

    @Option(names={"--maxToken"}, arity = "1", defaultValue="65536", description = "Maximum number of tokens to allow")
    int maxToken;

    @Parameters(index = "0",description = "Path to the root directory of the project to analyse")
    String projectRoot;

    @Parameters(index = "1",description = "Path to the directory with the concrete analysis definition")
    String analysisDirectory;

    @Parameters(index = "2",description = "Path to the working directory where result of analysis is stored")
    String workingDirectory;

    private UserMessage patchUserMessageWithSchema(UserMessage um, JsonNode responseSchema)
    {
        return UserMessage.from(
                um.singleText()
                        + ANSWER_ONLY_WITH_THE_FOLLOWING_JSON
                        + JsonHelper.createTypeDescription(responseSchema));
    }

    private String executeMessages(ChatExecutionContext executionContext,
                                   List<ChatMessage> messages,
                                   String rawResponseSchema,
                                   JsonNode responseSchema) {

        ResponseFormat responseFormat;

        if (jsonResponse) {
            responseFormat = ResponseFormat.builder()
                    .type(ResponseFormatType.JSON)
                    .jsonSchema(JsonSchema.builder()
                            .name(JsonHelper.getSchemaName(responseSchema))
                            .rootElement(JsonRawSchema.from(rawResponseSchema))
                            .build())
                    .build();
        }
        else {
            responseFormat = ResponseFormat.TEXT;
        }

        if (!jsonResponse && !messages.isEmpty() && messages.getLast() instanceof UserMessage) {
            UserMessage um = (UserMessage) messages.removeLast();
            messages.addLast(patchUserMessageWithSchema(um, responseSchema));
        }

        executionContext.getMemory().add(messages);

        ChatRequest request =
                ChatRequest.builder()
                        .messages(executionContext.getMemory().messages())
                        .toolSpecifications(executionContext.getToolService().toolSpecifications())
                        .toolChoice(ToolChoice.AUTO)
                        .responseFormat(responseFormat)
                        .build();

        ChatResponse initialResponse = executionContext.getChatModel().chat(request);

        InvocationContext invocationContext = InvocationContext.builder().build();

        ToolServiceResult toolResult =
                new ChatExecutor().execute(initialResponse,
                        request.parameters(),
                        executionContext.getChatModel(),
                        executionContext.getMemory(),
                        invocationContext,
                        executionContext.getToolService().toolExecutors());

        ChatResponse finalResponse = toolResult.finalResponse();

        return finalResponse.aiMessage().text();
    }

    @Override
    public Integer call() throws Exception {
        ChatModel model = OllamaChatModel.builder()
                .modelName(modelName)
                .baseUrl(modelUrl.toString())
                .timeout(Duration.ofMinutes(requestTimeout))
                .temperature(0.0)
                .think(false)
                .returnThinking(false)
                .listeners(List.of(new ChatListener()))
                .logRequests(logRequest)
                .logResponses(logResponse)
                .numCtx(maxToken)
                .build();

        logger.info(">> Parameter");
        logger.info("Model provider: '{}'", model.provider().name());
        logger.info("URL:            '{}'",modelUrl.toString());
        logger.info("Timeout:        {} minute(s)", requestTimeout);
        logger.info("Log requests:   {}", logRequest);
        logger.info("Log responses:  {}", logResponse);
        logger.info("Model name:     '{}'", modelName);
        logger.info("Maximum tokens: {}", maxToken);
        logger.info("Native JSON:    {}", jsonResponse);
        logger.info("==");
        logger.info("Project:        '{}'", projectRoot);
        logger.info("Workspace:      '{}'", workingDirectory);
        logger.info("<< Parameter");

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(chatWindowSize);

        ServiceOutputParser outputParser = new ServiceOutputParser();

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
        JsonFactory factory = mapper.getFactory();

        TaskManager taskManager = TaskManager.initializeTasks(Path.of(analysisDirectory),
                Path.of(workingDirectory),
                executeOnly);

        StateManager stateManager = StateManager.initializeState(Path.of(workingDirectory));

        TemplateLoader templateLoader = new FileTemplateLoader(Path.of(analysisDirectory)
                .toString(),
                "");

        var templateEngine = HandlebarsFactory.create()
                .with(templateLoader);

        AnalysisContext context = new AnalysisContext(
                Path.of(projectRoot),
                Path.of(workingDirectory),
                stateManager.getAnalysisState());

        List<Object> toolList = ToolFactory.getToolInstanceList(context);

        taskManager.dump();

        while (taskManager.hasPendingTasks()) {
            TaskDefinition task = taskManager.getNextTask();

            ToolService toolService = new ToolService();
            toolService.tools(toolList);

            ChatExecutionContext execContext =
                    new ChatExecutionContext(model, chatMemory, toolService, outputParser);

            String origSystemPrompt = null;
            String origUserPrompt = null;

            if (task.getSystemPrompt() != null) {
                origSystemPrompt = Files.readString(task.getSystemPrompt());
            }

            if (task.getPrompt() != null) {
                origUserPrompt = Files.readString(task.getPrompt());
            }

            String jsonResponseRawSchema = Files.readString(task.getResponseFormat());
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

                    LinkedList<ChatMessage> messages = new LinkedList<>();
                    String systemPrompt;
                    String userPrompt;

                    chatMemory.clear();

                    if (origSystemPrompt != null) {
                        Template template = templateEngine.compileInline(origSystemPrompt);
                        systemPrompt = template.apply(stateManager.getStateObject());
                        messages.add(SystemMessage.from(systemPrompt));
                    }

                    if (origUserPrompt!= null) {
                        Template template = templateEngine.compileInline(origUserPrompt);
                        userPrompt = template.apply(stateManager.getStateObject());

                        messages.add(UserMessage.from(userPrompt));
                    }

                    String taskResultString = executeMessages(execContext,
                            messages,
                            jsonResponseRawSchema,
                            jsonResponseSchema);

                    if (taskResultString != null && !taskResultString.isEmpty()) {
                        taskResultString = JsonHelper.extractJSON(taskResultString);
                        try (JsonParser parser = factory.createParser(taskResultString)) {
                            JsonNode taskResultJson = mapper.readTree(parser);
                            logger.info("===>[{}] {}: {}",
                                    stateManager.getLoopIndex(),
                                    JsonHelper.getSchemaName(jsonResponseSchema),
                                    taskResultJson.toPrettyString());

                            stateManager.updateLoopState(task.getResponseProperty(), taskResultJson);
                            stateManager.saveState();
                            taskManager.markTaskAskLoopProcessing(task, stateManager.getLoopIndex());
                        }
                    } else {
                        logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
                    }
                }

                stateManager.endLoop();
            }
            else {
                LinkedList<ChatMessage> messages = new LinkedList<>();
                String systemPrompt;
                String userPrompt;

                logger.info("===> Task: {} - {}",
                        task.getId(),
                        task.getName());

                chatMemory.clear();

                if (origSystemPrompt != null) {
                    Template template = templateEngine.compileInline(origSystemPrompt);
                    systemPrompt = template.apply(stateManager.getStateObject());
                    messages.add(SystemMessage.from(systemPrompt));
                }

                if (origUserPrompt!= null) {
                    Template template = templateEngine.compileInline(origUserPrompt);
                    userPrompt = template.apply(stateManager.getStateObject());

                    messages.add(UserMessage.from(userPrompt));
                }

                String taskResultString = executeMessages(execContext,
                        messages,
                        jsonResponseRawSchema,
                        jsonResponseSchema);

                if (taskResultString != null && !taskResultString.isEmpty()) {
                    taskResultString = JsonHelper.extractJSON(taskResultString);
                    try (JsonParser parser = factory.createParser(taskResultString)) {
                        JsonNode taskResultJson = mapper.readTree(parser);
                        logger.info("===> {}: {}", JsonHelper.getSchemaName(jsonResponseSchema), taskResultJson.toPrettyString());

                        stateManager.updateState(task.getResponseProperty(), taskResultJson);
                        stateManager.saveState();
                    }
                } else {
                    logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
                }
            }

            taskManager.markTaskAsSuccessful(task);
        }

        return 0;
    }
}
