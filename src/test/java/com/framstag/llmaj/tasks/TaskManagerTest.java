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
    void anIdleWorkspaceHasNoRunnableAndNoPendingTasks() throws Exception {
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
                """);

        writeSharedTaskFiles();

        Path workspace = tempDir.resolve("idle-workspace");
        Files.createDirectories(workspace);

        TaskManager fresh = TaskManager.initializeTasks(tempDir, workspace, Set.of());

        assertFalse(fresh.getRunnableTasks().isEmpty(), "a fresh workspace has runnable tasks");
        assertTrue(fresh.hasAnyPendingTasks(), "a fresh workspace has pending tasks");

        for (TaskDefinition task : fresh.getAllTasks()) {
            fresh.markTaskAsSuccessful(task);
        }

        // The idle run is decided from the state file, so the reload is the case that matters.
        TaskManager reloaded = TaskManager.initializeTasks(tempDir, workspace, Set.of());

        assertTrue(reloaded.getRunnableTasks().isEmpty(),
                "a workspace whose tasks are all successful has no runnable task");
        assertFalse(reloaded.hasAnyPendingTasks(),
                "a workspace whose tasks are all successful has no pending task");
    }

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

    @Test
    void markTaskAsFailedDoesNotUnlockDependents() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        taskManager.markTaskAsFailed(taskById(taskManager, "TaskA"));

        assertFalse(taskManager.isTaskSuccessful("TaskA"), "a failed task must not count as successful");
        assertFalse(getScheduledTags(taskManager).contains("tag_a"),
                "a failed task must not publish its tags");
        assertFalse(getPendingTaskIds(taskManager).contains("TaskA"),
                "a finished task leaves the pending set");
        assertTrue(taskManager.getRunnableTasks().stream().noneMatch(task -> task.getId().equals("TaskB")),
                "the dependent of a failed task must not become runnable");
    }

    @Test
    void markTaskAsSuccessfulUnlocksDependents() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        taskManager.markTaskAsSuccessful(taskById(taskManager, "TaskA"));

        assertTrue(taskManager.isTaskSuccessful("TaskA"));
        assertTrue(getScheduledTags(taskManager).contains("tag_a"),
                "a successful task publishes its tags");
        assertTrue(taskManager.getRunnableTasks().stream().anyMatch(task -> task.getId().equals("TaskB")),
                "the dependent of a successful task must become runnable");
    }

    @Test
    void theDumpDistinguishesAFailedTaskFromAPendingOne() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");
        TaskDefinition taskB = taskById(taskManager, "TaskB");

        assertEquals(" ", taskManager.getTaskStatus(taskA),
                "a task that was not executed yet is pending");

        taskManager.markTaskAsFailed(taskA);

        assertEquals("!", taskManager.getTaskStatus(taskA),
                "a failed task must not be reported like a task the run never reached");
        assertEquals(" ", taskManager.getTaskStatus(taskB),
                "a dependent that is still pending keeps its own marker");

        taskManager.markTaskAsSuccessful(taskB);

        assertEquals("x", taskManager.getTaskStatus(taskB), "a successful task is reported as successful");
    }

    @Test
    void aReloadedFailedTaskIsReportedAsFailedInTheDump() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        taskManager.markTaskAsFailed(taskById(taskManager, "TaskA"));

        TaskManager reloaded = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(reloaded);

        assertEquals("!", reloaded.getTaskStatus(taskById(reloaded, "TaskA")),
                "the recorded failure must survive the reload, which is what state dump reads");
    }

    @Test
    void failedTaskIsRetriedWhenStateIsReloaded() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of("TaskA"));
        assertNotNull(taskManager);

        taskManager.markTaskAsFailed(taskById(taskManager, "TaskA"));

        assertTrue(Files.exists(workspace.resolve("state.json")), "the outcome must be recorded");

        TaskManager reloaded = TaskManager.initializeTasks(tempDir, workspace, Set.of("TaskA"));
        assertNotNull(reloaded);

        assertFalse(reloaded.isTaskSuccessful("TaskA"), "a failed task must not be treated as completed");
        assertTrue(reloaded.getRunnableTasks().stream().anyMatch(task -> task.getId().equals("TaskA")),
                "a failed task must be executed again on the next run");
    }

    @Test
    void successfulTaskIsSkippedWhenStateIsReloaded() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of("TaskA"));
        assertNotNull(taskManager);

        taskManager.markTaskAsSuccessful(taskById(taskManager, "TaskA"));

        TaskManager reloaded = TaskManager.initializeTasks(tempDir, workspace, Set.of("TaskA"));
        assertNotNull(reloaded);

        assertTrue(reloaded.isTaskSuccessful("TaskA"));
        assertTrue(reloaded.getRunnableTasks().stream().noneMatch(task -> task.getId().equals("TaskA")),
                "a successful task must not be executed again");
    }

    private Path workspaceDirectory() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        return workspace;
    }

    private static StepTokenUsage usage(int input, int output) {
        return new StepTokenUsage(input, output, input + output);
    }

    @Test
    void theUsageOfANonLoopTaskIsRecorded() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        taskManager.recordStepTokenUsage(taskById(taskManager, "TaskA"), null, usage(100, 10));

        StepTokenUsage recorded = taskManager.getTokenUsage("TaskA");

        assertNotNull(recorded, "the usage of a step must be recorded");
        assertEquals(100, recorded.inputTokens());
        assertEquals(10, recorded.outputTokens());
        assertEquals(110, recorded.totalTokens());
    }

    @Test
    void theUsageOfALoopIndexIsRecordedPerIndex() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));
        taskManager.recordStepTokenUsage(taskA, 1, usage(200, 20));

        assertEquals(100, taskManager.getIndexTokenUsage("TaskA").get(0).inputTokens());
        assertEquals(200, taskManager.getIndexTokenUsage("TaskA").get(1).inputTokens());
    }

    @Test
    void theUsageOfALoopTaskIsTheSumOfItsIndices() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));
        taskManager.recordStepTokenUsage(taskA, 1, usage(200, 20));

        StepTokenUsage recorded = taskManager.getTokenUsage("TaskA");

        assertNotNull(recorded);
        assertEquals(300, recorded.inputTokens(),
                "the usage of a loop task is the sum of the usages of its indices");
        assertEquals(30, recorded.outputTokens());
        assertEquals(330, recorded.totalTokens());
    }

    @Test
    void theUsageSurvivesTheStateOfTheTaskBeingReplaced() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));

        taskManager.markIndexSuccessful(taskA, 0);

        assertNotNull(taskManager.getTokenUsage("TaskA"),
                "marking an index successful must not lose the recorded usage");

        taskManager.markTaskAsSuccessful(taskA);

        assertNotNull(taskManager.getTokenUsage("TaskA"),
                "marking a task successful must not lose the recorded usage");

        taskManager.markTaskAsFailed(taskA);

        assertNotNull(taskManager.getTokenUsage("TaskA"),
                "marking a task failed must not lose the recorded usage");
    }

    @Test
    void theUsageOfAStepThatRunsAgainReplacesTheOldOne() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));
        taskManager.recordStepTokenUsage(taskA, 0, usage(50, 5));

        assertEquals(50, taskManager.getIndexTokenUsage("TaskA").get(0).inputTokens(),
                "a step that runs again reports the usage of its last execution");
        assertEquals(50, taskManager.getTokenUsage("TaskA").inputTokens());
    }

    @Test
    void theUsageOfAStepThatIsNotExecutedAgainStaysTheSame() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));
        taskManager.recordStepTokenUsage(taskA, 1, usage(200, 20));

        assertEquals(100, taskManager.getIndexTokenUsage("TaskA").get(0).inputTokens(),
                "a step that is skipped keeps the usage of its last execution");
        assertEquals(300, taskManager.getTokenUsage("TaskA").inputTokens());
    }

    @Test
    void aStepWithoutAnyReportedUsageIsNotRecorded() throws Exception {        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        taskManager.recordStepTokenUsage(taskById(taskManager, "TaskA"), null, null);

        assertNull(taskManager.getTokenUsage("TaskA"),
                "a step whose provider reported no count has no recorded usage");
        assertTrue(taskManager.getTokenUsages().isEmpty());
    }

    @Test
    void theRecordedUsageSurvivesTheReloadOfTheState() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));

        TaskManager reloaded = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(reloaded);

        StepTokenUsage recorded = reloaded.getTokenUsage("TaskA");

        assertNotNull(recorded, "the recorded usage must be read back from the state file");
        assertEquals(100, recorded.inputTokens());
        assertEquals(10, recorded.outputTokens());
        assertEquals(100, reloaded.getIndexTokenUsage("TaskA").get(0).inputTokens());
    }

    @Test
    void theIndicesOfATaskThatIsNoLongerALoopTaskAreDropped() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();
        TaskManager taskManager = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(taskManager);

        TaskDefinition taskA = taskById(taskManager, "TaskA");

        taskManager.recordStepTokenUsage(taskA, 0, usage(100, 10));
        taskManager.recordStepTokenUsage(taskA, null, usage(50, 5));

        assertTrue(taskManager.getIndexTokenUsage("TaskA").isEmpty(),
                "a task that is not a loop task must not keep the indices of an earlier run");
        assertEquals(50, taskManager.getTokenUsage("TaskA").inputTokens());
    }

    @Test
    void aStateFileWithoutUsagesStillLoads() throws Exception {
        writeDependencyTasks();
        writeSharedTaskFiles();

        Path workspace = workspaceDirectory();

        // A state file written before the token usage was recorded.
        Files.writeString(workspace.resolve("state.json"), """
                [ {
                  "taskId" : "TaskA",
                  "lastExecution" : "2026-10-06T20:02:52.891+0000",
                  "state" : "SUCCESSFUL"
                } ]
                """);

        TaskManager reloaded = TaskManager.initializeTasks(tempDir, workspace, Set.of());
        assertNotNull(reloaded, "a state file without the new fields must still load");

        assertNull(reloaded.getTokenUsage("TaskA"));
        assertTrue(reloaded.getTokenUsages().isEmpty());
    }

    private TaskDefinition taskById(TaskManager taskManager, String taskId) {
        return taskManager.getAllTasks().stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow();
    }

    private void writeDependencyTasks() throws Exception {
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
                """);
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
