package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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

    private final Path workingDirectory;
    private final List<TaskDefinition> allTasks;
    private final Set<String> pendingTaskIds;
    private final Map<String,TaskState> taskStateMap;

    static {
        mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    private TaskManager(Path workingDirectory,
                        List<TaskDefinition> allTasks,
                        Map<String,TaskState> taskStateMap,
                        Set<String> pendingTaskIds) {
        this.workingDirectory = workingDirectory;
        this.allTasks =allTasks;
        this.taskStateMap = taskStateMap;
        this.pendingTaskIds = pendingTaskIds;
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

    private String getTaskStatus(TaskDefinition task, boolean pending) {
        if (!task.isActive()) {
            return "/";
        }
        else if (pending) {
            return "x";
        }
        else {
            return " ";
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

    public void dump() {
        for (TaskDefinition task : this.allTasks) {
            logger.info("Task: [{}] {}",
                    getTaskStatus(task, pendingTaskIds.contains(task.getId())),
                    task);
        }
    }

    public boolean hasPendingTasks() {
        return !pendingTaskIds.isEmpty();
    }

    public TaskDefinition getNextTask() {
        for (TaskDefinition task : this.allTasks) {
            if (pendingTaskIds.contains(task.getId())) {
                return task;
            }
        }

        return null;
    }

    public void markTaskAsSuccessful(String taskId) {
        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as finished, though it is not pending!", taskId);
            return;
        }

        pendingTaskIds.remove(taskId);

        taskStateMap.put(taskId,
                new TaskState(taskId,
                        ZonedDateTime.now(),
                        true));

        saveState();
    }

    public void markTaskAsFailed(String taskId) {
        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as finished, though it is not pending!", taskId);
            return;
        }

        pendingTaskIds.remove(taskId);

        taskStateMap.put(taskId,
                new TaskState(taskId,
                        ZonedDateTime.now(),
                        false));

        saveState();
    }

    public static TaskManager initializeTasks(Path analyseDirectory,
                                              Path workingDirectory,
                                              Set<String> executeOnly) throws IOException {
        Path taskFilePath = analyseDirectory.resolve("tasks.yaml");
        Path stateFilePath = workingDirectory.resolve("state.json");
        File stateFile = stateFilePath.toFile();

        List<TaskDefinition> allTasks = TaskDefinition.loadTasks(taskFilePath);

        if (!validateRequiredFields(allTasks) ||
            !validateDependsOn(allTasks) ||
            !validateExecuteVsActive(allTasks,executeOnly) ||
            !validateNoDependencyCycles(allTasks)) {
            logger.error("There were errors in the task definitions");

            return null;
        }

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

        taskStateMap.forEach((taskId, state) -> {
            if (state.success()) {
                pendingTaskIds.remove(taskId);
            }
        });

        return new TaskManager(workingDirectory,
                allTasks,
                taskStateMap,
                pendingTaskIds);
    }
}
