package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.config.ConfigOverrides;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.display.DisplayDecision;
import com.framstag.llmaj.display.DisplayManager;
import com.framstag.llmaj.display.TerminalSupport;
import com.framstag.llmaj.json.JsonHelper;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.logging.EngineLogRouting;
import com.framstag.llmaj.logging.ForwardingLogLineSink;
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
import org.slf4j.MDC;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Command(name = "analyse", description = "Analyse the project")
public class AnalyseCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(AnalyseCmd.class);

    @Option(names={"--log-request"}, arity = "1", description = "Activate langchain4j low-level log of chat requests")
    Boolean logRequest;

    @Option(names={"--log-response"}, arity = "1", description = "Activate langchain4j low-level log of chat responses")
    Boolean logResponse;

    @Option(names={"--execution-trace"}, arity = "1", description = "Show chat execution trace on console (disables TUI)")
    Boolean executionTrace;

    @Option(names={"--execution-trace-system"}, arity = "1", description = "Show system messages in console execution trace")
    Boolean executionTraceSystem;

    @Option(names={"-o","--executeOnly"}, arity = "1", split = ",", description = "Task id to execute exclusively; repeat the option or separate ids with commas to select several tasks")
    Set<String> executeOnly = new HashSet<>();

    @Option(names={"--single-step"}, arity = "1", defaultValue = "false", description = "Stop execution after one task")
    boolean singleStep = false;

    @Option(names={"--task-parallelism"}, arity = "1", description = "Number of parallel DAG tasks to execute concurrently")
    Integer taskParallelism;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    Path workingDirectory;

    /**
     * Collects the option values that were actually passed on the command line.
     * <p>
     * An option the user did not pass must not write into the configuration, otherwise the
     * workspace configuration would never take effect for that setting. Package private so the
     * "was it passed" behaviour can be tested without running an analysis.
     */
    ConfigOverrides readOverrides() {
        var parseResult = spec.commandLine().getParseResult();

        if (parseResult == null) {
            return ConfigOverrides.NONE;
        }

        return new ConfigOverrides(
                parseResult.hasMatchedOption("--log-request") ? logRequest : null,
                parseResult.hasMatchedOption("--log-response") ? logResponse : null,
                parseResult.hasMatchedOption("--execution-trace") ? executionTrace : null,
                parseResult.hasMatchedOption("--execution-trace-system") ? executionTraceSystem : null,
                parseResult.hasMatchedOption("--task-parallelism") ? taskParallelism : null);
    }

    private static void logEffectiveConfiguration(ConfigOverrides overrides, Config config) {
        for (var resolution : overrides.resolutions(config)) {
            logger.info("Effective {}: {} (source: {})",
                    resolution.setting(), resolution.value(), resolution.source());
        }
    }

    /**
     * Reports the display mode and the reason the TUI was not used, so that a silent downgrade to
     * plain output cannot happen unnoticed. Prints nothing when the TUI is running.
     */
    private static void reportDisplayMode(DisplayDecision displayDecision) {
        if (displayDecision.useTui()) {
            return;
        }

        System.out.println("Display mode: " + displayDecision.mode().name().toLowerCase(Locale.ROOT)
                + " (" + displayDecision.reason() + ")");
    }

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

            ConfigOverrides overrides = readOverrides();
            overrides.applyTo(config);

            config.dumpToLog();
            logEffectiveConfiguration(overrides, config);
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

        // A run in which every task is already successful has nothing to execute. It is reported
        // here, before any display exists: probing a terminal would load the native terminal
        // implementation for nothing, and starting the TUI would paint a frame whose rows could
        // only be wrong, followed by a summary claiming results that this run did not produce.
        // No runnable task while pending tasks remain is a different case and keeps its error.
        if (taskManager.getRunnableTasks().isEmpty() && !taskManager.hasAnyPendingTasks()) {
            int taskCount = taskManager.getAllTasks().size();

            logger.info("No task is runnable, all {} tasks are already successful.", taskCount);
            System.out.println("Nothing to run: all " + taskCount
                    + " tasks are already successful. Use 'state clear' or 'state drop' to run them again.");

            return 0;
        }

        // Initialize display. The execution trace is resolved once, by the config merge above, and
        // every consumer reads that same value.
        TerminalSupport terminalSupport = config.isExecutionTrace()
                ? TerminalSupport.none()
                : TerminalSupport.detect();
        DisplayDecision displayDecision = DisplayDecision.decide(config.isExecutionTrace(),
                terminalSupport.stdoutIsTerminal());

        // Engine log output must stay out of the terminal the TUI draws on. The routing is installed
        // before the display is created, so that no record can reach the terminal once the first frame
        // is painted. The console appender is detached right after that frame, which keeps a diagnostic
        // the routing itself produces (an engine log file that cannot be opened) on the console. A run
        // without the TUI keeps the console as its diagnostic channel.
        ForwardingLogLineSink logLineSink = EngineLogRouting.installForDisplayMode(
                displayDecision.useTui(), workingDirectory, config.isExecutionTrace());

        // Build set of pre-completed task IDs for display initialization
        Set<String> preCompletedTaskIds = new HashSet<>();
        for (var task : taskManager.getAllTasks()) {
            if (taskManager.isTaskSuccessful(task.getId())) {
                preCompletedTaskIds.add(task.getId());
            }
        }

        DisplayManager displayManager = new DisplayManager(
                config, displayDecision, terminalSupport,
                taskManager.getAllTasks(), preCompletedTaskIds, logLineSink);

        // The first frame is on screen and the display is ready to show the records, so the terminal
        // can be handed over: from here on no log record goes to the console.
        EngineLogRouting.detachConsoleAfterFirstFrame(displayDecision.useTui());

        reportDisplayMode(displayDecision);

        try {
            int taskParallelism = config.getTaskParallelism();
            ExecutorService dagPool = Executors.newFixedThreadPool(taskParallelism);
            BlockingQueue<String> completionQueue = new LinkedBlockingQueue<>();
            Set<String> runningIds = ConcurrentHashMap.newKeySet();

            if (singleStep) {
                // Single-step: execute exactly one task (including its loops), then stop
                List<TaskDefinition> runnable = taskManager.getRunnableTasks();
                if (!runnable.isEmpty()) {
                    TaskDefinition task = runnable.get(0);
                    runningIds.add(task.getId());
                    String schema = Files.readString(config.getAnalysisDirectory().resolve(task.getResponseFormat()));
                    JsonNode schemaNode = mapper.readTree(schema);
                    dagPool.submit(buildTaskRunner(config, task, mapper, model, toolService,
                            templateEngine, stateManager, taskManager, displayManager,
                            completionQueue, runningIds, workingDirectory, schema, schemaNode));
                    completionQueue.take();
                }
            } else {
                // Submit all initially runnable tasks
                for (TaskDefinition task : taskManager.getRunnableTasks()) {
                    runningIds.add(task.getId());
                    String schema = Files.readString(config.getAnalysisDirectory().resolve(task.getResponseFormat()));
                    JsonNode schemaNode = mapper.readTree(schema);
                    dagPool.submit(buildTaskRunner(config, task, mapper, model, toolService,
                            templateEngine, stateManager, taskManager, displayManager,
                            completionQueue, runningIds, workingDirectory, schema, schemaNode));
                }

                // Dispatch loop: wait for completions, submit newly unblocked tasks
                while (taskManager.hasAnyPendingTasks() || !runningIds.isEmpty()) {
                    if (runningIds.isEmpty() && taskManager.hasAnyPendingTasks()) {
                        logger.error("No tasks running but pending tasks remain — possible dependency deadlock");
                        break;
                    }

                    String completedId = completionQueue.take();

                    // Submit newly unblocked tasks
                    for (TaskDefinition task : taskManager.getRunnableTasks()) {
                        if (!runningIds.contains(task.getId())) {
                            runningIds.add(task.getId());
                            String schema = Files.readString(config.getAnalysisDirectory().resolve(task.getResponseFormat()));
                            JsonNode schemaNode = mapper.readTree(schema);
                            dagPool.submit(buildTaskRunner(config, task, mapper, model, toolService,
                                    templateEngine, stateManager, taskManager, displayManager,
                                    completionQueue, runningIds, workingDirectory, schema, schemaNode));
                        }
                    }
                }
            }

            dagPool.shutdown();

            return 0;
        } finally {
            displayManager.close();
        }
    }

    private Runnable buildTaskRunner(
            Config config,
            TaskDefinition task,
            ObjectMapper mapper,
            ChatModel model,
            ToolService toolService,
            Handlebars templateEngine,
            StateManager stateManager,
            TaskManager taskManager,
            DisplayManager displayManager,
            BlockingQueue<String> completionQueue,
            Set<String> runningIds,
            Path workingDirectory,
            String jsonResponseRawSchema,
            JsonNode jsonResponseSchema
    ) {
        return () -> {
            String taskId = task.getId();
            String taskName = task.getName();

            // Set MDC context for log attribution
            MDC.put(EngineLogRouting.TASK_ID_MDC_KEY, taskId);

            try {
                if (task.hasLoopOn()) {
                    // ── Loop task execution ──
                    synchronized (stateManager) {
                        if (!stateManager.startLoop(task.getLoopOn())) {
                            logger.error("Configuration error, aborting task {}!", taskId);
                            return;
                        }
                    }

                    int totalIndices = stateManager.getLoopArraySize();
                    int parallelism = config.getLoopParallelism();

                    displayManager.onTaskStart(taskId, taskName);
                    displayManager.setLoopTotal(taskId, totalIndices);

                    logger.info("Executing task '{}' with {} loop indices, parallelism={}",
                            taskId, totalIndices, parallelism);

                    ExecutorService loopPool = Executors.newFixedThreadPool(parallelism);
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    AtomicBoolean anyIndexFailed = new AtomicBoolean(false);

                    for (int index = 0; index < totalIndices; index++) {
                        if (taskManager.isIndexSuccessful(task, index)) {
                            logger.info("Loop index {} was already successfully executed, skipping...", index);
                            continue;
                        }

                        final int currentIndex = index;

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            MDC.put(EngineLogRouting.TASK_ID_MDC_KEY, taskId);
                            MDC.put("loopIndex", String.valueOf(currentIndex));
                            try {
                                stateManager.loopAtIndex(currentIndex);

                                // Deep copy for thread safety — each worker gets own snapshot
                                Map<String, Object> workerState = new HashMap<>();
                                workerState.putAll(new JsonNodeModelWrapper(stateManager.getAnalysisState().deepCopy()));
                                workerState.put("loopIndex", currentIndex);

                                logger.info("<===[{}] Task: {} - {}",
                                        currentIndex, taskId, taskName);

                                LinkedList<ChatMessage> messages = resolveChatMessages(config,
                                        templateEngine, task, workerState);

                                ChatExecutionContext execContext =
                                        new ChatExecutionContext(config, model, toolService,
                                                new ToolFilter(task.getToolWhitelist(), task.getToolBlacklist()),
                                                mapper, taskId, currentIndex, workingDirectory);
                                execContext.setProgressCallback(displayManager.getCallback());

                                displayManager.getCallback().onWorkerStart(taskId, currentIndex,
                                        taskName + "[" + currentIndex + "]");

                                JsonNode taskResultJson = new ChatExecutor().executeMessages(config,
                                        execContext, messages,
                                        jsonResponseRawSchema, jsonResponseSchema);

                                if (taskResultJson != null) {
                                    logger.info("===>[{}] {}: {}",
                                            currentIndex,
                                            JsonHelper.getSchemaName(jsonResponseSchema),
                                            taskResultJson.toPrettyString());
                                    synchronized (stateManager) {
                                        stateManager.updateLoopState(currentIndex, task.getResponseProperty(), taskResultJson);
                                        stateManager.saveState();
                                    }
                                    taskManager.markIndexSuccessful(task, currentIndex);

                                    displayManager.getCallback().onWorkerComplete(taskId, currentIndex,
                                            taskName + "[" + currentIndex + "]");
                                } else {
                                    logger.error("No response from chat model, possibly json response was requested but is not supported by model?");
                                    anyIndexFailed.set(true);
                                    displayManager.getCallback().onWorkerError(taskId, currentIndex,
                                            taskName + "[" + currentIndex + "]", "No response from chat model");
                                }
                            } catch (Exception e) {
                                logger.error("Error processing loop index {}: {}", currentIndex, e.getMessage(), e);
                                anyIndexFailed.set(true);
                            } finally {
                                MDC.remove("loopIndex");
                            }
                        }, loopPool);

                        futures.add(future);
                    }

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    loopPool.shutdown();
                    stateManager.endLoop();

                    if (anyIndexFailed.get()) {
                        logger.warn("Task '{}' completed with some failed indices — marking as failed for retry", taskId);
                        synchronized (taskManager) {
                            taskManager.markTaskAsFailed(task);
                        }
                        displayManager.onTaskError(taskId, taskName, "Some loop indices failed");
                    } else {
                        synchronized (taskManager) {
                            taskManager.markLoopTaskAsSuccessful(task);
                        }
                        displayManager.onTaskComplete(taskId, taskName);
                    }
                } else {
                    // ── Non-loop task execution ──
                    displayManager.onTaskStart(taskId, taskName);

                    logger.info("===> Task: {} - {}", taskId, taskName);

                    LinkedList<ChatMessage> messages = resolveChatMessages(config,
                            templateEngine, task, stateManager.getStateObject());

                    ChatExecutionContext execContext =
                            new ChatExecutionContext(config, model, toolService,
                                    new ToolFilter(task.getToolWhitelist(), task.getToolBlacklist()),
                                    mapper, taskId, null, workingDirectory);
                    execContext.setProgressCallback(displayManager.getCallback());

                    JsonNode taskResultJson = new ChatExecutor().executeMessages(config,
                            execContext, messages,
                            jsonResponseRawSchema, jsonResponseSchema);

                    if (taskResultJson != null) {
                        logger.info("===> {}: {}",
                                JsonHelper.getSchemaName(jsonResponseSchema),
                                taskResultJson.toPrettyString());

                        stateManager.updateState(task.getResponseProperty(), taskResultJson);
                        stateManager.saveState();

                        synchronized (taskManager) {
                            taskManager.markTaskAsSuccessful(task);
                        }

                        displayManager.onTaskComplete(taskId, taskName);
                    } else {
                        logger.error("No response from chat model, possibly json response was requested but is not supported by model?");

                        // Without a payload there is no result. Recording the task as successful
                        // would publish its tags, so dependents would run against a response
                        // property that was never written, and the next run would skip the task.
                        synchronized (taskManager) {
                            taskManager.markTaskAsFailed(task);
                        }

                        displayManager.onTaskError(taskId, taskName, "No response from chat model");
                    }
                }
            } catch (Exception e) {
                logger.error("Error executing task {}: {}", taskId, e.getMessage(), e);
                synchronized (taskManager) {
                    taskManager.markTaskAsFailed(task);
                }
                displayManager.onTaskError(taskId, taskName, e.getMessage());
            } finally {
                runningIds.remove(taskId);
                completionQueue.offer(taskId);
                MDC.clear();
            }
        };
    }
}