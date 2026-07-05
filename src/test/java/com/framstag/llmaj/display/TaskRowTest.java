package com.framstag.llmaj.display;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskRowTest {

    @Test
    public void testInitialState() {
        TaskRow row = new TaskRow("test-id", "Test Task");
        assertEquals("test-id", row.getId());
        assertEquals("Test Task", row.getName());
        assertEquals(TaskRow.Status.PENDING, row.getStatus());
        assertEquals(0, row.getElapsedMillis());
        assertNull(row.getTokenUsage());
        assertNull(row.getErrorMessage());
        assertEquals(0, row.getLoopCompleted());
        assertEquals(0, row.getLoopTotal());
        assertFalse(row.hasLoop());
        assertTrue(row.getSteps().isEmpty());
    }

    @Test
    public void testStatusTransitions() {
        TaskRow row = new TaskRow("t1", "Task 1");
        assertEquals(TaskRow.Status.PENDING, row.getStatus());

        row.setStatus(TaskRow.Status.RUNNING);
        assertEquals(TaskRow.Status.RUNNING, row.getStatus());

        row.setStatus(TaskRow.Status.SUCCESSFUL);
        assertEquals(TaskRow.Status.SUCCESSFUL, row.getStatus());

        row.setStatus(TaskRow.Status.FAILED);
        assertEquals(TaskRow.Status.FAILED, row.getStatus());
    }

    @Test
    public void testElapsedTime() {
        TaskRow row = new TaskRow("t1", "Task 1");
        row.setElapsedMillis(1234);
        assertEquals(1234, row.getElapsedMillis());
    }

    @Test
    public void testTokenUsage() {
        TaskRow row = new TaskRow("t1", "Task 1");
        TokenUsage usage = new TokenUsage(100, 50, 150);
        row.setTokenUsage(usage);
        assertSame(usage, row.getTokenUsage());
    }

    @Test
    public void testErrorMessage() {
        TaskRow row = new TaskRow("t1", "Task 1");
        row.setErrorMessage("Something went wrong");
        assertEquals("Something went wrong", row.getErrorMessage());
    }

    @Test
    public void testLoopProgress() {
        TaskRow row = new TaskRow("t1", "Task 1");
        row.setHasLoop(true);
        assertTrue(row.hasLoop());

        row.setLoopTotal(10);
        assertEquals(10, row.getLoopTotal());

        assertEquals(0, row.getLoopCompleted());
        row.incrementLoopCompleted();
        assertEquals(1, row.getLoopCompleted());
        row.incrementLoopCompleted();
        assertEquals(2, row.getLoopCompleted());
    }

    @Test
    public void testInteractionSteps() {
        TaskRow row = new TaskRow("t1", "Task 1");
        assertTrue(row.getSteps().isEmpty());

        row.addStep(LoopWorkerRow.InteractionStep.REQUEST_SENT);
        assertEquals(1, row.getSteps().size());
        assertEquals(LoopWorkerRow.InteractionStep.REQUEST_SENT, row.getSteps().get(0));

        row.addStep(LoopWorkerRow.InteractionStep.RESPONSE_RECEIVED);
        assertEquals(2, row.getSteps().size());
        assertEquals(LoopWorkerRow.InteractionStep.RESPONSE_RECEIVED, row.getSteps().get(1));

        row.addStep(LoopWorkerRow.InteractionStep.TOOL_CALL);
        assertEquals(3, row.getSteps().size());

        row.addStep(LoopWorkerRow.InteractionStep.TOOL_RESULT);
        assertEquals(4, row.getSteps().size());
    }
}
