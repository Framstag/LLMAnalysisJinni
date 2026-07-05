package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.display.DisplayManager;
import com.framstag.llmaj.display.ProgressCallback;
import com.framstag.llmaj.json.JsonHelper;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.lc4j.ChatExecutionContext;
import com.framstag.llmaj.lc4j.ChatExecutor;
import com.framstag.llmaj.lc4j.ChatModelFactory;
import com.framstag.llmaj.lc4j.ToolFilter;
import com.framstag.llmaj.state.StateManager;
import com.framstag.llmaj.tasks.TaskDefinition;
import com.framstag.llmaj.tasks.TaskManager;
import com.framstag.llmaj.tools.ToolServiceFactory;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import ch.qos.logback.classic.Level;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Command(name = "analyse", description = "Analyse the project")
public class AnalyseCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(AnalyseCmd.class);

    @Option(names={"--log-request"}, arity = "1", description = "Activate langchain4j low-level log of chat requests")
    Boolean logRequest = false;

    @Option(names={"--log-response"}, arity = "1", description = "Activate langchain4j low-level log of chat responses")
    Boolean logResponse = false;

    @Option(names={"--execution-trace"}, arity = "1", defaultValue = "false", description = "Show chat execution trace on console (disables TUI)")
    boolean executionTrace = false;

    @Option(names={"--execution-trace-system"}, arity = "1", defaultValue = "false", description = "Show system messages in console execution trace")
    boolean executionTraceSystem = false;

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
            config = ConfigLoader.loadFromWorkingDirectory(workingDirectory);

            if (logRequest != null) {
                config.setLogRequests(logRequest);
            }

            if (logResponse != null) {
                config.setLogResponses(logResponse);
            }

            config.setExecutionTrace(executionTrace);
            config.setExecutionTraceSystem(executionTraceSystem);

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

        if (taskManager == null) {
            logger.error("Cannot retrieve task list, aborting.");
            return 1;
        }

        StateManager stateManager = StateManager.initializeState(workingDirectory);

        var templateEngine = HandlebarsFactory.create()
                .with(new FileTemplateLoader(config.getAnalysisDirectoryAsString(),
                        ""));

        AnalysisContext analysisContext = new AnalysisContext(
                config.getProjectDirectory(),
                workingDirectory,
                config.getProperties(),
                stateManager.getAnalysisState());

        ToolService toolService = ToolServiceFactory.getToolService(config,analysisContext);

        // Initialize display
        boolean useTui = !executionTrace && System.console() != null;

        // Suppress SLF4J INFO console output when not in execution-trace mode
        if (!executionTrace) {
            ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(Level.WARN);
        }

        // Build set of pre-completed task IDs for display initialization
        Set<String> preCompletedTaskIds = new HashSet<>();
        for (var task : taskManager.getAllTasks()) {
            if (taskManager.isTaskSuccessful(task.getId())) {
                preCompletedTaskIds.add(task.getId());
            }
        }

        DisplayManager displayManager = new DisplayManager(
                config, useTui, executionTrace,
                taskManager.getAllTasks(), preCompletedTaskIds);

        try {
            while (taskManager.hasPendingTasks()) {
            TaskDefinition task = taskManager.getNextTask();

            String jsonResponseRawSchema = Files.readString(config.getAnalysisDirectory().resolve(task.getResponseFormat()));
            JsonNode jsonResponseSchema = mapper.readTree(jsonResponseRawSchema);

            if (task.hasLoopOn()) {
                if (!stateManager.startLoop(task.getLoopOn())) {
                    logger.error("Configuration error, aborting!");
                    return 1;
                }

                int totalIndices = stateManager.getLoopArraySize();
                int parallelism = config.getLoopParallelism();

                displayManager.onTaskStart(task.getId(), task.getName());
                displayManager.setLoopTotal(task.getId(), totalIndices);

                logger.info("Executing task '{}' with {} loop indices, parallelism={}",
                        task.getId(), totalIndices, parallelism);

                ExecutorService executor = Executors.newFixedThreadPool(parallelism);
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int index = 0; index < totalIndices; index++) {
                    if (taskManager.isIndexSuccessful(task, index)) {
                        logger.info("Loop index {} was already successfully executed, skipping...", index);
                        continue;
                    }

                    final int currentIndex = index;

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            stateManager.loopAtIndex(currentIndex);

                            // Inject per-worker loopIndex without touching shared state
                            Map<String, Object> workerState = new HashMap<>();
                            // Deep copy for thread safety - each worker gets own snapshot
                            workerState.putAll(new JsonNodeModelWrapper(stateManager.getAnalysisState().deepCopy()));
                            workerState.put("loopIndex", currentIndex);

                            logger.info("<===[{}] Task: {} - {}",
                                    currentIndex,
                                    task.getId(),
                                    task.getName());

                            LinkedList<ChatMessage> messages = resolveChatMessages(config,
                                    templateEngine,
                                    task,
                                    workerState);

                            ChatExecutionContext execContext =
                                    new ChatExecutionContext(config,
                                            model,
                                            toolService,
                                            new ToolFilter(task.getToolWhitelist(), task.getToolBlacklist()),
                                            mapper,
                                            task.getId(),
                                            currentIndex,
                                            workingDirectory);
                            execContext.setProgressCallback(displayManager.getCallback());

                            // Update display for this worker
                            displayManager.getCallback().onWorkerStart(task.getId(), currentIndex, task.getName() + "[" + currentIndex + "]");

                            JsonNode taskResultJson = new ChatExecutor().executeMessages(config,
                                    execContext,
                                    messages,
                                    jsonResponseRawSchema,
                                    jsonResponseSchema);

                            if (taskResultJson != null) {
                                logger.info("===>[{}] {}: {}",
                                        currentIndex,
                                        JsonHelper.getSchemaName(jsonResponseSchema),
                                        taskResultJson.toPrettyString());
                                // Thread-safe: synchronize on stateManager so update + save are atomic
                                synchronized (stateManager) {
                                    stateManager.updateLoopState(currentIndex, task.getResponseProperty(), taskResultJson);
                                    stateManager.saveState();
                                }
                                taskManager.markIndexSuccessful(task, currentIndex);

                                // Update display for completed worker
                                displayManager.getCallback().onWorkerComplete(task.getId(), currentIndex, task.getName() + "[" + currentIndex + "]");
                            } else {
                                logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
                                displayManager.getCallback().onWorkerError(task.getId(), currentIndex, task.getName() + "[" + currentIndex + "]", "No response from chat model");
                            }
                        } catch (Exception e) {
                            logger.error("Error processing loop index {}: {}", currentIndex, e.getMessage(), e);
                        }
                    }, executor);

                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                executor.shutdown();

                stateManager.endLoop();

                taskManager.markLoopTaskAsSuccessful(task);

                displayManager.onTaskComplete(task.getId(), task.getName());
            }
            else {
                displayManager.onTaskStart(task.getId(), task.getName());

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
                                new ToolFilter(task.getToolWhitelist(), task.getToolBlacklist()),
                                mapper,
                                task.getId(),
                                null,
                                workingDirectory);
                execContext.setProgressCallback(displayManager.getCallback());

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

                taskManager.markTaskAsSuccessful(task);

                if (taskResultJson != null) {
                    displayManager.onTaskComplete(task.getId(), task.getName());
                } else {
                    displayManager.onTaskError(task.getId(), task.getName(), "No response from chat model");
                }

            }

            if (singleStep) {
                break;
            }
        }

        return 0;
        } finally {
            displayManager.close();
        }
    }
}