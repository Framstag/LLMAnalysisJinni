package com.framstag.llmaj.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DisplayDecisionTest {

    @Test
    public void testTuiWhenTraceInactiveAndStdoutIsTerminal() {
        DisplayDecision decision = DisplayDecision.decide(false, true);

        assertEquals(DisplayDecision.Mode.TUI, decision.mode());
        assertTrue(decision.useTui());
        assertNull(decision.reason(), "a running TUI needs no fallback reason");
    }

    @Test
    public void testSimpleFallbackWhenStdoutIsNotATerminal() {
        DisplayDecision decision = DisplayDecision.decide(false, false);

        assertEquals(DisplayDecision.Mode.SIMPLE, decision.mode());
        assertFalse(decision.useTui());
        assertNotNull(decision.reason());
    }

    @Test
    public void testNoDisplayWhenExecutionTraceIsActive() {
        DisplayDecision decision = DisplayDecision.decide(true, true);

        assertEquals(DisplayDecision.Mode.NONE, decision.mode());
        assertFalse(decision.useTui());
        assertNotNull(decision.reason());
    }

    @Test
    public void testTraceWinsOverTerminalCapability() {
        DisplayDecision decision = DisplayDecision.decide(true, false);

        assertEquals(DisplayDecision.Mode.NONE, decision.mode());
        assertFalse(decision.useTui());
    }
}
