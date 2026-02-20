package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskManager {
    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);
    private static final ObjectMapper mapper;
    public static final String STATE_JSON_FILENAME = "state.json";
    public static final String TASKS_YAML_FILENAME = "tasks.yaml";

    private final Path workingDirectory;
    private final List<TaskDefinition> allTasks;
    private final Set<String> pendingTaskIds;
    private final Set<String> successfullyProcessedTaskIds;
    private final Set<String> scheduledTags;

    private final Map<String,TaskState> taskStateMap;

    static {
        mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
    }

    private TaskManager(Path workingDirectory,
                        List<TaskDefinition> allTasks,
                        Map<String,TaskState> taskStateMap,
                        Set<String> pendingTaskIds,
                        Set<String> successfullyProcessedTaskIds,
                        Set<String> scheduledTags) {
        this.workingDirectory = workingDirectory;
        this.allTasks =allTasks;
        this.taskStateMap = taskStateMap;
        this.pendingTaskIds = pendingTaskIds;
        this.successfullyProcessedTaskIds = successfullyProcessedTaskIds;
        this.scheduledTags= scheduledTags;
    }

    public static boolean deleteStateFile(Path workingDirectory) {
        File stateFile = workingDirectory.resolve(STATE_JSON_FILENAME).toFile();

        if (!stateFile.exists() || !stateFile.isFile()) {
            logger.warn("State file {} does not exist or is not a file", stateFile.getAbsolutePath());
            return false;
        }

        if (!stateFile.delete()) {
            logger.warn("Unable to delete file {}", stateFile.getAbsolutePath());
            return false;
        }

        return true;
    }

    private static void markDependentTasks(TaskDefinition task,
                                           Map<String, List<TaskDefinition>> taskListMap,
                                           Set<TaskDefinition> markedTask) {
        for (String tag : task.getDependsOn()) {
            for (TaskDefinition dependentTask : taskListMap.get(tag)) {
                if (!markedTask.contains(dependentTask)) {
                    markedTask.add(dependentTask);
                    markDependentTasks(dependentTask, taskListMap, markedTask);
                }
            }
        }
    }

    private static boolean validateRequiredFields(List<TaskDefinition> tasks) {
        boolean errorsFound = false;

        for (TaskDefinition task : tasks) {
            if (!task.hasSystemPrompt() && !task.hasPrompt()) {
                logger.error("Task '{}' has neither system prompt nor normal prompt!", task.getId());

                errorsFound = true;
            }

            if (!task.hasResponseFormat()) {
                logger.error("Task '{}' has no response format defined!", task.getId());

                errorsFound = true;
            }
        }

        return !errorsFound;
    }

    private static boolean validateDependsOn(List<TaskDefinition> tasks) {
        boolean errorsFound = false;

        // Collect all tags
        Set<String> allTags = tasks.stream()
                .map(TaskDefinition::getTags)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        // Check dependsOn only references existing tags
        for  (TaskDefinition task : tasks) {
            for (String tag : task.getDependsOn()) {
                if (!allTags.contains(tag)) {
                    logger.error("Task '{}' depends on Tag '{}', which is not defined by any task",
                            task.getId(),
                            tag);
                    errorsFound = true;
                }
            }
        }

        return !errorsFound;
    }

    private static boolean validateExecuteVsActive(List<TaskDefinition> tasks,Set<String> executeOnly) {
        boolean errorsFound = false;

        for (TaskDefinition task : tasks) {
            if (executeOnly.contains(task.getId()) && !task.isActive()) {
                logger.warn("Task '{}' was marked for execution, but is not active => ignoring execution",
                        task.getId());

                errorsFound = true;
            }
        }

        return !errorsFound;
    }

    private static boolean validateNoDependencyCycles(List<TaskDefinition> tasks) {
        boolean errorsFound = false;

        // Build a map of tags to tasks
        Map<String,List<TaskDefinition>> taskListMap = new HashMap<>();

        for (TaskDefinition task : tasks) {
            for (String tag : task.getTags()) {
                if (!taskListMap.containsKey(tag)) {
                    taskListMap.put(tag, new LinkedList<>());
                }

                taskListMap.get(tag).add(task);
            }
        }

        // See if we can mark all tasks
        for (TaskDefinition task : tasks) {
            Set<TaskDefinition> markedTasks = new HashSet<>();

            markDependentTasks(task,taskListMap,markedTasks);

            if (markedTasks.contains(task)) {
                logger.error("The task '{}' starts a dependency cycle", task.getId());

                errorsFound = true;
            }
        }

        return !errorsFound;
    }

    private String getTaskStatus(TaskDefinition task, boolean pending, boolean successful) {
        if (!task.isActive()) {
            return "/";
        }
        else if (successful) {
            return "x";
        }
        else if (pending) {
            return " ";
        }
        else {
            return "-";
        }
    }

    private static Path getStateFilePath(Path workingDirectory) {
        return workingDirectory.resolve("state.json");
    }

    private void saveState() {
        Path stateFilePath=getStateFilePath(workingDirectory);
        File stateFile = stateFilePath.toFile();

        logger.info("Writing current execution state  to '{}'...",stateFilePath);

        try {
            TaskState[] taskStates = taskStateMap.values().toArray(TaskState[]::new);
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, taskStates);
        } catch (IOException e) {
            logger.error("Exception while writing result to file", e);
        }
    }

    private TaskDefinition calculateNextTask() {
        List<TaskDefinition> toBeScheduledTasks = allTasks.stream()
                .filter(task -> pendingTaskIds.contains(task.getId()))
                .toList();

        for (TaskDefinition task : toBeScheduledTasks) {
            boolean scheduled = true;

            for (String dependsOn : task.getDependsOn()) {
                if (!scheduledTags.contains(dependsOn)) {
                    scheduled = false;
                    break;
                }
            }

            if (scheduled) {
                return task;
            }
        }

        return null;
    }

    public void dump() {
        for (TaskDefinition task : this.allTasks) {
            logger.info("Task: [{}] {} - {}",
                    getTaskStatus(task,
                            pendingTaskIds.contains(task.getId()),
                            successfullyProcessedTaskIds.contains(task.getId())),
                    task.getId(),
                    task.getName());
        }
    }

    public boolean hasPendingTasks() {
        return calculateNextTask() != null;
    }

    public TaskDefinition getNextTask() {
        return calculateNextTask();
    }

    public void markTaskAskLoopProcessing(TaskDefinition task,
                                          int successfulLoopIndex) {
        String taskId = task.getId();

        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as pending, though it is not pending!",
                    taskId);
            return;
        }

        taskStateMap.put(taskId,
                new TaskState(taskId,
                        ZonedDateTime.now(),
                        successfulLoopIndex,
                        TaskStatus.PROCESSING));

        saveState();
    }

    public int getTaskSuccessFullLoopIndex(TaskDefinition task) {
        String taskId = task.getId();

        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as finished, though it is not pending!",
                    taskId);
            return -1;
        }

        TaskState taskState = taskStateMap.get(taskId);

        if (taskState == null) {
            return -1;
        }

        Integer lastSuccessfulIndex = taskState.getLastSuccessfulIndex();

        if (lastSuccessfulIndex == null) {
            return -1;
        }

        return lastSuccessfulIndex;
    }

    public void markTaskAsSuccessful(TaskDefinition task) {
        String taskId = task.getId();

        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as finished, though it is not pending!",
                    taskId);
            return;
        }

        pendingTaskIds.remove(taskId);
        successfullyProcessedTaskIds.add(taskId);
        scheduledTags.addAll(task.getTags());

        taskStateMap.put(taskId,
                new TaskState(taskId,
                        ZonedDateTime.now(),
                        null,
                        TaskStatus.SUCCESSFUL));

        saveState();
    }

    public void markTaskAsFailed(TaskDefinition task) {
        String taskId = task.getId();

        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as finished, though it is not pending!", taskId);
            return;
        }

        pendingTaskIds.remove(taskId);

        taskStateMap.put(taskId,
                new TaskState(taskId,
                        ZonedDateTime.now(),
                        null,
                        TaskStatus.FAILED));

        saveState();
    }

    public static TaskManager initializeTasks(Path analyseDirectory,
                                              Path workingDirectory,
                                              Set<String> executeOnly) throws IOException {
        Path taskFilePath = analyseDirectory.resolve(TASKS_YAML_FILENAME);
        Path stateFilePath = workingDirectory.resolve(STATE_JSON_FILENAME);
        File stateFile = stateFilePath.toFile();

        List<TaskDefinition> allTasks = TaskDefinition.loadTasks(taskFilePath);

        if (!validateRequiredFields(allTasks) ||
            !validateDependsOn(allTasks) ||
            !validateExecuteVsActive(allTasks,executeOnly) ||
            !validateNoDependencyCycles(allTasks)) {
            logger.error("There were errors in the task definitions");

            return null;
        }

        Map<String, TaskDefinition> taskByIdMap = new HashMap<>();

        allTasks.forEach(task -> taskByIdMap.put(task.getId(), task));

        Set<String> pendingTaskIds;

        if (executeOnly != null && !executeOnly.isEmpty()) {
            pendingTaskIds = allTasks.stream()
                    .filter(TaskDefinition::isActive)
                    .map(TaskDefinition::getId)
                    .filter(executeOnly::contains)
                    .collect(Collectors.toSet());
        }
        else {
            pendingTaskIds = allTasks.stream()
                    .filter(TaskDefinition::isActive)
                    .map(TaskDefinition::getId)
                    .collect(Collectors.toSet());
        }

        Map<String,TaskState> taskStateMap;

        if (stateFile.exists() && stateFile.isFile()) {
            taskStateMap = Arrays.stream(TaskState.loadTaskState(stateFilePath))
                    .collect(Collectors.toMap(TaskState::taskId, Function.identity()));
        }
        else {
            taskStateMap = new HashMap<>();
        }

        Set<String> successfullyProcessedTaskIds = new HashSet<>();

        taskStateMap.forEach((taskId, state) -> {
            if (state.isSuccessful()) {
                pendingTaskIds.remove(taskId);
                successfullyProcessedTaskIds.add(taskId);
            }
        });

        Set<String> scheduledTags = new HashSet<>();

        taskStateMap.forEach((taskId, state) -> {
            if (state.isSuccessful()) {
                scheduledTags.addAll(taskByIdMap.get(taskId).getTags());
            }
        });

        return new TaskManager(workingDirectory,
                allTasks,
                taskStateMap,
                pendingTaskIds,
                successfullyProcessedTaskIds,
                scheduledTags);
    }
}
