package com.framstag.llmaj.display;

/**
 * Which display the run uses, and why the TUI was not used when it was not.
 * <p>
 * Pure decision logic, so it can be unit tested without a terminal: the caller owns the terminal
 * probing and only reports whether stdout is a terminal.
 */
public record DisplayDecision(Mode mode, String reason) {

    public enum Mode {
        /** Live terminal UI. */
        TUI,
        /** Sequential status lines for a non-TTY environment. */
        SIMPLE,
        /** No display at all; the verbose console execution trace is active instead. */
        NONE
    }

    /**
     * Decides the display mode.
     *
     * @param executionTrace  true when the effective console execution trace is active
     * @param stdoutIsTerminal true when stdout is a terminal able to host the TUI
     */
    public static DisplayDecision decide(boolean executionTrace, boolean stdoutIsTerminal) {
        if (executionTrace) {
            return new DisplayDecision(Mode.NONE, "console execution trace is active");
        }

        if (!stdoutIsTerminal) {
            return new DisplayDecision(Mode.SIMPLE, "stdout is not a terminal");
        }

        return new DisplayDecision(Mode.TUI, null);
    }

    public boolean useTui() {
        return mode == Mode.TUI;
    }
}
