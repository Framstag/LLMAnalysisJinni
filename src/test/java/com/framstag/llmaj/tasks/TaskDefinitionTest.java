package com.framstag.llmaj.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDefinitionTest {

    @TempDir
    Path tempDir;

    @Test
    void ignoresSuccessfulStateForInactiveTasks() throws Exception {
        Path tasksFile = tempDir.resolve("tasks.yaml");
        Files.writeString(tasksFile, """
                ---
                id: ActiveTask
                name: Active task
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                tags:
                  - active_tag
                ---
                id: InactiveTask
                name: Inactive task
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: false
                tags:
                  - inactive_tag
                ---
                id: DependentTask
                name: Dependent task
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                dependsOn:
                  - inactive_tag
                tags:
                  - dependent_tag
                """);

        Path workspaceDir = tempDir.resolve("workspace");
        Path stateFile = workspaceDir.resolve("state.json");
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, """
                [
                  {
                    "taskId" : "InactiveTask",
                    "lastExecution" : "2026-06-14T00:00:00.000+0000",
                    "state" : "SUCCESSFUL"
                  }
                ]
                """);

        writeSharedTaskFiles();

        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspaceDir, Set.of());

        assertEquals("ActiveTask", taskManager.getNextTask().getId());
        assertFalse(getScheduledTags(taskManager).contains("inactive_tag"));
    }

    @Test
    void loadsActiveFlagFromYaml() throws Exception {
        Path tasksFile = tempDir.resolve("tasks.yaml");
        Files.writeString(tasksFile, """
                ---
                id: ActiveTask
                name: Active task
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                tags:
                  - active_tag
                ---
                id: InactiveTask
                name: Inactive task
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: false
                tags:
                  - inactive_tag
                ---
                id: DependentTask
                name: Dependent task
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                dependsOn:
                  - inactive_tag
                tags:
                  - dependent_tag
                """);

        writeSharedTaskFiles();

        TaskManager taskManager = TaskManager.initializeTasks(tempDir, tempDir.resolve("workspace"), Set.of());

        assertTrue(getTask(taskManager, "ActiveTask").isActive());
        assertFalse(getTask(taskManager, "InactiveTask").isActive());
        assertFalse(getPendingTaskIds(taskManager).contains("InactiveTask"));
    }

    private TaskDefinition getTask(TaskManager taskManager, String taskId) throws Exception {
        Field allTasksField = TaskManager.class.getDeclaredField("allTasks");
        allTasksField.setAccessible(true);

        return ((Collection<TaskDefinition>) allTasksField.get(taskManager)).stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Set<String> getScheduledTags(TaskManager taskManager) throws Exception {
        Field scheduledTagsField = TaskManager.class.getDeclaredField("scheduledTags");
        scheduledTagsField.setAccessible(true);
        return (Set<String>) scheduledTagsField.get(taskManager);
    }

    private void writeSharedTaskFiles() throws Exception {
        Path responseSchema = tempDir.resolve("results/Response.json");
        Files.createDirectories(responseSchema.getParent());
        Path prompt = tempDir.resolve("prompts/system.md");
        Files.createDirectories(prompt.getParent());
        Files.writeString(prompt, "System prompt");
        Files.writeString(responseSchema, """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object"
                }
                """);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getPendingTaskIds(TaskManager taskManager) throws Exception {
        Field pendingTaskIdsField = TaskManager.class.getDeclaredField("pendingTaskIds");
        pendingTaskIdsField.setAccessible(true);
        return (Set<String>) pendingTaskIdsField.get(taskManager);
    }
}
