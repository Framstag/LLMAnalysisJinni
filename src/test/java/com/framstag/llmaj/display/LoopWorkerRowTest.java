package com.framstag.llmaj.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LoopWorkerRowTest {

    @Test
    public void testInitialState() {
        LoopWorkerRow row = new LoopWorkerRow("task-id", 0, "worker-label");
        assertEquals("task-id", row.getTaskId());
        assertEquals(0, row.getIndex());
        assertEquals("worker-label", row.getLabel());
        assertEquals(LoopWorkerRow.Status.PENDING, row.getStatus());
        assertEquals(0, row.getElapsedMillis());
        assertTrue(row.getSteps().isEmpty());
        assertEquals(0, row.getRoundCount());
        assertNull(row.getErrorMessage());
    }

    @Test
    public void testStatusTransitions() {
        LoopWorkerRow row = new LoopWorkerRow("t1", 0, "w1");
        assertEquals(LoopWorkerRow.Status.PENDING, row.getStatus());

        row.setStatus(LoopWorkerRow.Status.RUNNING);
        assertEquals(LoopWorkerRow.Status.RUNNING, row.getStatus());

        row.setStatus(LoopWorkerRow.Status.SUCCESSFUL);
        assertEquals(LoopWorkerRow.Status.SUCCESSFUL, row.getStatus());

        row.setStatus(LoopWorkerRow.Status.FAILED);
        assertEquals(LoopWorkerRow.Status.FAILED, row.getStatus());
    }

    @Test
    public void testElapsedTime() {
        LoopWorkerRow row = new LoopWorkerRow("t1", 0, "w1");
        row.setElapsedMillis(5678);
        assertEquals(5678, row.getElapsedMillis());
    }

    @Test
    public void testInteractionSteps() {
        LoopWorkerRow row = new LoopWorkerRow("t1", 0, "w1");
        assertTrue(row.getSteps().isEmpty());

        row.addStep(LoopWorkerRow.InteractionStep.REQUEST_SENT);
        assertEquals(1, row.getSteps().size());
        assertEquals(LoopWorkerRow.InteractionStep.REQUEST_SENT, row.getSteps().get(0));

        row.addStep(LoopWorkerRow.InteractionStep.TOOL_CALL);
        assertEquals(2, row.getSteps().size());
        assertEquals(LoopWorkerRow.InteractionStep.TOOL_CALL, row.getSteps().get(1));

        row.addStep(LoopWorkerRow.InteractionStep.TOOL_RESULT);
        assertEquals(3, row.getSteps().size());

        row.addStep(LoopWorkerRow.InteractionStep.RESPONSE_RECEIVED);
        assertEquals(4, row.getSteps().size());
    }

    @Test
    public void testRoundCount() {
        LoopWorkerRow row = new LoopWorkerRow("t1", 0, "w1");
        assertEquals(0, row.getRoundCount());

        row.incrementRoundCount();
        assertEquals(1, row.getRoundCount());

        row.incrementRoundCount();
        assertEquals(2, row.getRoundCount());
    }

    @Test
    public void testErrorMessage() {
        LoopWorkerRow row = new LoopWorkerRow("t1", 0, "w1");
        row.setErrorMessage("Worker failed");
        assertEquals("Worker failed", row.getErrorMessage());
    }

    @Test
    public void testMultipleWorkers() {
        LoopWorkerRow row0 = new LoopWorkerRow("t1", 0, "w0");
        LoopWorkerRow row1 = new LoopWorkerRow("t1", 1, "w1");
        LoopWorkerRow row2 = new LoopWorkerRow("t1", 2, "w2");

        assertEquals(0, row0.getIndex());
        assertEquals(1, row1.getIndex());
        assertEquals(2, row2.getIndex());

        assertEquals("w0", row0.getLabel());
        assertEquals("w1", row1.getLabel());
        assertEquals("w2", row2.getLabel());
    }
}
