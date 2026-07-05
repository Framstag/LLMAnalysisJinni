package com.framstag.llmaj.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void getRunnableTasksReturnsOnlyTasksWithSatisfiedDeps() throws Exception {
        Path tasksFile = tempDir.resolve("tasks.yaml");
        Files.writeString(tasksFile, """
                ---
                id: TaskA
                name: Task A
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                tags:
                  - tag_a
                ---
                id: TaskB
                name: Task B
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                dependsOn:
                  - tag_a
                tags:
                  - tag_b
                ---
                id: TaskC
                name: Task C
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                dependsOn:
                  - tag_a
                  - tag_b
                tags:
                  - tag_c
                ---
                id: TaskD
                name: Task D
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                tags:
                  - tag_d
                """);

        writeSharedTaskFiles();

        TaskManager taskManager = TaskManager.initializeTasks(tempDir, tempDir.resolve("workspace"), Set.of());

        // Initially, no tags are scheduled, so only tasks with no deps should be runnable
        List<TaskDefinition> runnable = taskManager.getRunnableTasks();
        assertEquals(2, runnable.size(), "TaskA and TaskD should be runnable initially");
        assertTrue(runnable.stream().anyMatch(t -> t.getId().equals("TaskA")));
        assertTrue(runnable.stream().anyMatch(t -> t.getId().equals("TaskD")));
        assertTrue(runnable.stream().noneMatch(t -> t.getId().equals("TaskB")));
        assertTrue(runnable.stream().noneMatch(t -> t.getId().equals("TaskC")));

        // Simulate TaskA completing: add tag_a to scheduledTags, remove from pending
        Set<String> scheduledTags = getScheduledTags(taskManager);
        scheduledTags.add("tag_a");
        getPendingTaskIds(taskManager).remove("TaskA");

        runnable = taskManager.getRunnableTasks();
        assertEquals(2, runnable.size(), "TaskB and TaskD should be runnable after TaskA");
        assertTrue(runnable.stream().anyMatch(t -> t.getId().equals("TaskB")));
        assertTrue(runnable.stream().anyMatch(t -> t.getId().equals("TaskD")));
        assertTrue(runnable.stream().noneMatch(t -> t.getId().equals("TaskA"))); // removed from pending
        assertTrue(runnable.stream().noneMatch(t -> t.getId().equals("TaskC"))); // needs tag_b too

        // Simulate TaskB completing: add tag_b to scheduledTags, remove from pending
        scheduledTags.add("tag_b");
        getPendingTaskIds(taskManager).remove("TaskB");

        runnable = taskManager.getRunnableTasks();
        assertEquals(2, runnable.size(), "TaskC and TaskD should be runnable after TaskB");
        assertTrue(runnable.stream().anyMatch(t -> t.getId().equals("TaskC")));
        assertTrue(runnable.stream().anyMatch(t -> t.getId().equals("TaskD")));
    }

    @Test
    void getRunnableTasksReturnsEmptyWhenAllBlocked() throws Exception {
        Path tasksFile = tempDir.resolve("tasks.yaml");
        Files.writeString(tasksFile, """
                ---
                id: NeverSatisfied
                name: Never satisfied
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                tags:
                  - never_satisfied
                ---
                id: TaskA
                name: Task A
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                dependsOn:
                  - never_satisfied
                tags:
                  - tag_a
                ---
                id: TaskB
                name: Task B
                systemPrompt: prompts/system.md
                responseFormat: results/Response.json
                responseProperty: result
                active: true
                dependsOn:
                  - never_satisfied
                tags:
                  - tag_b
                """);

        writeSharedTaskFiles();

        TaskManager taskManager = TaskManager.initializeTasks(tempDir, tempDir.resolve("workspace"), Set.of());
        assertNotNull(taskManager, "TaskManager should initialize despite blocked tasks");

        List<TaskDefinition> runnable = taskManager.getRunnableTasks();
        // NeverSatisfied has no deps, so it IS runnable. TaskA and TaskB are blocked.
        assertEquals(1, runnable.size(), "Only NeverSatisfied should be runnable");
        assertEquals("NeverSatisfied", runnable.get(0).getId());
    }

    @Test
    void getRunnableTasksExcludesInactiveTasks() throws Exception {
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
                """);

        writeSharedTaskFiles();

        TaskManager taskManager = TaskManager.initializeTasks(tempDir, tempDir.resolve("workspace"), Set.of());

        List<TaskDefinition> runnable = taskManager.getRunnableTasks();
        assertEquals(1, runnable.size());
        assertEquals("ActiveTask", runnable.get(0).getId());
    }

    @SuppressWarnings("unchecked")
    private Set<String> getScheduledTags(TaskManager taskManager) throws Exception {
        Field scheduledTagsField = TaskManager.class.getDeclaredField("scheduledTags");
        scheduledTagsField.setAccessible(true);
        return (Set<String>) scheduledTagsField.get(taskManager);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getPendingTaskIds(TaskManager taskManager) throws Exception {
        Field pendingTaskIdsField = TaskManager.class.getDeclaredField("pendingTaskIds");
        pendingTaskIdsField.setAccessible(true);
        return (Set<String>) pendingTaskIdsField.get(taskManager);
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
}
