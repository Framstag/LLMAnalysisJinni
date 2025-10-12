package com.framstag.llmaj.cli;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.ChatExecutionContext;
import com.framstag.llmaj.ChatListener;
import com.framstag.llmaj.json.JsonHelper;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.framstag.llmaj.tasks.TaskDefinition;
import com.framstag.llmaj.tools.file.FileTool;
import com.framstag.llmaj.tools.info.InfoTool;
import com.framstag.llmaj.tools.sbom.SBOMTool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    @Option(names={"--log-request"}, arity = "1", defaultValue = "false", description = "Make langchain4j log the chat requests")
    boolean logRequest = false;

    @Option(names={"--log-response"}, arity = "1", defaultValue = "false", description = "Make langchain4j log the chat response")
    boolean logResponse = false;

    @Option(names={"-o","--executeOnly"}, arity = "0..*", description = "A list of task ids, that should only be executed")
    Set<String> executeOnly;

    @Parameters(index = "0",description = "Path to the root directory of the project to analyse")
    String projectRoot;

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

        ToolServiceResult toolResult =
                executionContext.getToolService().executeInferenceAndToolsLoop(initialResponse,
                        request.parameters(),
                        executionContext.getMemory().messages(),
                        executionContext.getChatModel(),
                        executionContext.getMemory(),
                        null,
                        executionContext.getToolService().toolExecutors(),
                        true);

        ChatResponse finalResponse = toolResult.finalResponse();

        return finalResponse.aiMessage().text();
    }

    @Override
    public Integer call() throws Exception {
        AnalysisContext context = new AnalysisContext("ArchitectureAnalysis",
                "1.0.0",
                projectRoot);

        List<TaskDefinition> tasks = TaskDefinition.loadTasks(Path.of("tasks/tasks.yaml"));

        for  (TaskDefinition task : tasks) {
            logger.info("Task {}", task);
        }

        ChatModel model = OllamaChatModel.builder()
                .modelName(modelName)
                .baseUrl(modelUrl.toString())
                .timeout(Duration.ofMinutes(20))
                .temperature(0.0)
                .think(false)
                .returnThinking(false)
                .listeners(List.of(new ChatListener()))
                .logRequests(logRequest)
                .logResponses(logResponse)
                .build();

        logger.info("Model provider: '{}'", model.provider().name());

        Path resultPath = Path.of("result.json");

        InfoTool infoTool = new InfoTool(context);
        FileTool fileTool = new FileTool(context);
        SBOMTool sbomTool = new SBOMTool(context);

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(50);

        ServiceOutputParser outputParser = new ServiceOutputParser();

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JsonFactory factory = mapper.getFactory();

        ObjectNode result = mapper.createObjectNode();

        if (resultPath.toFile().exists() && resultPath.toFile().isFile()) {
            logger.info("Loading result from '{}'",resultPath);

            JsonNode fileContent = JsonHelper.readResult(mapper,resultPath);

            if (fileContent instanceof ObjectNode) {
                result = (ObjectNode) fileContent;
            }
            else {
                logger.error("Result is not an Json Object, ignore content");
            }
        }

        TemplateEngine templateEngine = new TemplateEngine();

        Context templateContext = new Context();
        templateContext.setVariable("state", new JsonNodeModelWrapper(result));

        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateResolver.setCacheable(false);

        templateEngine.setTemplateResolver(templateResolver);

        for (TaskDefinition task : tasks) {
            if (executeOnly != null && !executeOnly.isEmpty() && !executeOnly.contains(task.getId())) {
                continue;
            }

            logger.info("==> Task: {} - {}", task.getId(), task.getName());

            if (!task.isActive()) {
                logger.warn("Task is not active, skipping!");
                continue;
            }

            ToolService toolService = new ToolService();
            toolService.tools(List.of(infoTool, fileTool, sbomTool));

            ChatExecutionContext execContext =
                    new ChatExecutionContext(model, chatMemory, toolService, outputParser);

            String systemPrompt = null;
            String userPrompt = null;

            if (task.getSystemPrompt() != null) {
                systemPrompt = Files.readString(task.getSystemPrompt());
            }

            if (task.getPrompt() != null) {
                userPrompt = Files.readString(task.getPrompt());
            }

            if (systemPrompt == null && userPrompt == null) {
                logger.error("Task {} has no prompts, skipping", task.getId());
                continue;
            }

            if (task.getResponseFormat() == null) {
                logger.error("Task {} has no response format, skipping", task.getId());
                continue;
            }

            String jsonResponseRawSchema = Files.readString(task.getResponseFormat());
            JsonNode jsonResponseSchema = mapper.readTree(jsonResponseRawSchema);

            LinkedList<ChatMessage> messages = new LinkedList<>();

            if (systemPrompt != null) {
                systemPrompt = templateEngine.process(systemPrompt, templateContext);
                messages.add(SystemMessage.from(systemPrompt));
            }

            if (userPrompt!= null) {
                userPrompt = templateEngine.process(userPrompt, templateContext);
                messages.add(UserMessage.from(userPrompt));
            }

            String taskResultString = executeMessages(execContext,
                    messages,
                    jsonResponseRawSchema,
                    jsonResponseSchema);

            if (taskResultString != null && !taskResultString.isEmpty()) {
                try (JsonParser parser = factory.createParser(taskResultString)) {
                    JsonNode taskResultJson = mapper.readTree(parser);
                    logger.info("===> {}: {}", JsonHelper.getSchemaName(jsonResponseSchema), taskResultJson.toPrettyString());

                    result.set(task.getResponseProperty(), taskResultJson);
                }
            }
            else {
                logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
            }

            logger.info("Current result written to '{}'",resultPath);
            JsonHelper.writeResult(result, mapper,resultPath);
        }

        return 0;
    }
}
