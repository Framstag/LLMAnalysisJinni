package com.framstag.llmaj.lc4j;

import java.util.List;

/**
 * Why one attempt of a task step was rejected, together with the material a further attempt is
 * told about: the schema violations and the bounded excerpt of a payload that did not parse.
 * <p>
 * The text that reaches a further attempt ({@link #repairHint()}) and the text that reaches the
 * display ({@link #displayMessage()}) are bounded on purpose: the hint is injected into a prompt,
 * and the display has a single reserved line for it.
 */
public record TaskStepFailure(StepFailureReason reason,
                              String message,
                              List<String> violationMessages,
                              String excerpt) {

    /**
     * How many violation messages a repair hint carries before the rest is summarised.
     */
    public static final int MAX_HINT_VIOLATIONS = 5;

    /**
     * How long a repair hint may become, so a long violation list cannot dominate the prompt.
     */
    public static final int MAX_HINT_LENGTH = 600;

    /**
     * How long the reason may become on the display and in the engine log, which share one line.
     */
    public static final int MAX_DISPLAY_LENGTH = 300;

    public TaskStepFailure {
        violationMessages = violationMessages == null ? List.of() : List.copyOf(violationMessages);
    }

    public static TaskStepFailure of(StepFailureReason reason, String message) {
        return new TaskStepFailure(reason, message, List.of(), null);
    }

    public static TaskStepFailure of(StepFailureReason reason, String message, String excerpt) {
        return new TaskStepFailure(reason, message, List.of(), excerpt);
    }

    public static TaskStepFailure schemaViolation(List<String> violationMessages) {
        return new TaskStepFailure(StepFailureReason.SCHEMA_VIOLATION,
                "the response does not conform to the declared JSON schema",
                violationMessages,
                null);
    }

    /**
     * The reason as one bounded line, for the display and the engine log.
     */
    public String displayMessage() {
        return truncate(reason.label() + ": " + message, MAX_DISPLAY_LENGTH);
    }

    /**
     * The text a further attempt is given, appended to its user message after the schema
     * description. Names the reason and, when they exist, the violations and the payload excerpt. When
     * the hint carries less than every violation, it also names how many there were in total.
     */
    public String repairHint() {
        StringBuilder hint = new StringBuilder();

        hint.append("\nYour previous answer was rejected: ")
                .append(reason.label())
                .append(" (").append(message).append(").\n");

        if (!violationMessages.isEmpty()) {
            hint.append("The violations were");

            // The total is what tells the model whether one field or the whole shape has to change.
            if (violationMessages.size() > MAX_HINT_VIOLATIONS) {
                hint.append(" (").append(violationMessages.size()).append(" in total, ")
                        .append(MAX_HINT_VIOLATIONS).append(" shown)");
            }

            hint.append(":\n");

            for (int i = 0; i < Math.min(violationMessages.size(), MAX_HINT_VIOLATIONS); i++) {
                hint.append("- ").append(violationMessages.get(i)).append('\n');
            }

            if (violationMessages.size() > MAX_HINT_VIOLATIONS) {
                hint.append("- ... and ").append(violationMessages.size() - MAX_HINT_VIOLATIONS)
                        .append(" further violation(s)\n");
            }
        }

        if (excerpt != null && !excerpt.isBlank()) {
            hint.append("The rejected answer began with: ").append(excerpt).append('\n');
        }

        hint.append("Answer again with a single JSON object that matches the JSON schema above.");

        return truncate(hint.toString(), MAX_HINT_LENGTH);
    }

    private static String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }

        return text.substring(0, limit - 3) + "...";
    }
}
