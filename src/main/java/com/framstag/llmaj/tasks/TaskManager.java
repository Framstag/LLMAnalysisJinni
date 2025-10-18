package com.framstag.llmaj.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TaskManager {
    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    private final List<TaskDefinition> allTasks;
    private final Set<String> pendingTaskIds;

    private TaskManager(List<TaskDefinition> allTasks,
                        Set<String> pendingTaskIds) {
        this.allTasks =allTasks;
        this.pendingTaskIds = pendingTaskIds;
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

    public void markTasksAsSuccessful(String taskId) {
        if (!pendingTaskIds.contains(taskId)) {
            logger.error("Trying to mark task with id {} as finished, though it is not pending!", taskId);
            return;
        }

        pendingTaskIds.remove(taskId);
    }

    public static TaskManager initializeTasks(Path analyseDirectory, Set<String> executeOnly) throws IOException {
        Path taskFile = analyseDirectory.resolve("tasks.yaml");

        List<TaskDefinition> allTasks = TaskDefinition.loadTasks(taskFile).stream()
                .filter(TaskDefinition::isActive)
                .toList();

        for (TaskDefinition task : allTasks) {
            if (executeOnly.contains(task.getId())&& !task.isActive()) {
                logger.warn("Task '{}' was marked for execution, but is not active => ignoring execution",
                        task.getId());
            }
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

        return new TaskManager(allTasks, pendingTaskIds);
    }
}
