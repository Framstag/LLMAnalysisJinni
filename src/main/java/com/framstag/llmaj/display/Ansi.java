package com.framstag.llmaj.display;

/**
 * ANSI escape code constants and helpers for terminal output.
 */
public final class Ansi {

    private Ansi() {}

    // Reset
    public static final String RESET = "\033[0m";

    // Styles
    public static final String BOLD = "\033[1m";
    public static final String DIM = "\033[2m";

    // Foreground colours
    public static final String BLACK = "\033[30m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE = "\033[34m";
    public static final String MAGENTA = "\033[35m";
    public static final String CYAN = "\033[36m";
    public static final String WHITE = "\033[37m";
    public static final String BRIGHT_YELLOW = "\033[93m";
    public static final String BRIGHT_MAGENTA = "\033[95m";
    public static final String BRIGHT_CYAN = "\033[96m";
    public static final String BRIGHT_GREEN = "\033[92m";

    // Cursor movement
    public static final String CURSOR_UP = "\033[A";
    public static final String CURSOR_DOWN = "\033[B";
    public static final String CURSOR_RIGHT = "\033[C";
    public static final String CURSOR_LEFT = "\033[D";
    public static final String ERASE_LINE = "\033[2K";
    public static final String ERASE_DISPLAY = "\033[2J";
    public static final String CURSOR_HOME = "\033[H";
    public static final String HIDE_CURSOR = "\033[?25l";
    public static final String SHOW_CURSOR = "\033[?25h";

    // Helpers
    public static String cursorUp(int n) {
        return "\033[" + n + "A";
    }

    public static String cursorDown(int n) {
        return "\033[" + n + "B";
    }

    public static String cursorRight(int n) {
        return "\033[" + n + "C";
    }

    public static String cursorLeft(int n) {
        return "\033[" + n + "D";
    }

    public static String colour(String text, String colour) {
        return colour + text + RESET;
    }

    public static String bold(String text) {
        return BOLD + text + RESET;
    }

    public static String dim(String text) {
        return DIM + text + RESET;
    }

    /**
     * Format elapsed milliseconds as a human-readable string.
     */
    public static String formatElapsed(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        double seconds = millis / 1000.0;
        if (seconds < 60) {
            return String.format("%.1fs", seconds);
        }
        long minutes = (long) seconds / 60;
        long secs = (long) seconds % 60;
        return String.format("%dm%02ds", minutes, secs);
    }

    /**
     * Format token count in human-readable form (e.g., "1.2K").
     */
    public static String formatTokenCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        return String.format("%.1fK", count / 1000.0);
    }

    /**
     * Render a progress bar of the given width.
     */
    public static String progressBar(int completed, int total, int width) {
        if (total <= 0) {
            return " ".repeat(width);
        }
        int fill = (int) ((double) completed / total * width);
        if (fill > width) fill = width;
        return "\u2588".repeat(fill) + "\u2591".repeat(width - fill);
    }
}
