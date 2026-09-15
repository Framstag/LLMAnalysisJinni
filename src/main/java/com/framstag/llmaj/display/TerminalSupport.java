package com.framstag.llmaj.display;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.InfoCmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * The terminal the run may render to, together with the capabilities derived from it.
 * <p>
 * Capability is taken from the terminal itself instead of from the JVM console object: a process
 * often has no console object while stdout still is a perfectly usable terminal, and it is stdout
 * that the TUI writes to.
 */
public record TerminalSupport(Terminal terminal,
                              boolean stdoutIsTerminal,
                              boolean ansiSupported,
                              boolean unicodeSupported) {

    private static final Logger logger = LoggerFactory.getLogger(TerminalSupport.class);

    /**
     * No terminal at all, used when no display is needed.
     */
    public static TerminalSupport none() {
        return new TerminalSupport(null, false, false, false);
    }

    /**
     * Creates the system terminal and derives its capabilities. Never throws: a failure to obtain a
     * terminal degrades to {@link #none()}, which makes the run fall back to simple output.
     */
    public static TerminalSupport detect() {
        Terminal terminal;

        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (IOException e) {
            logger.warn("Cannot create a system terminal ({}), falling back to simple output", e.getMessage());
            return none();
        }

        if (terminal == null) {
            return none();
        }

        return of(terminal);
    }

    /**
     * Derives the capabilities of an already created terminal.
     */
    public static TerminalSupport of(Terminal terminal) {
        if (terminal == null) {
            return none();
        }

        boolean stdoutIsTerminal = isTerminal(terminal);
        boolean ansiSupported = stdoutIsTerminal
                && terminal.getStringCapability(InfoCmp.Capability.cursor_up) != null
                && terminal.getStringCapability(InfoCmp.Capability.clr_eos) != null;
        boolean unicodeSupported = ansiSupported
                && terminal.encoding() != null
                && terminal.encoding().name().toUpperCase().contains("UTF");

        return new TerminalSupport(terminal, stdoutIsTerminal, ansiSupported, unicodeSupported);
    }

    private static boolean isTerminal(Terminal terminal) {
        String type = terminal.getType();

        if (type == null) {
            return false;
        }

        return !(terminal instanceof DumbTerminal) && !"dumb".equalsIgnoreCase(type);
    }
}
