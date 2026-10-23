package com.framstag.llmaj.tools.history;

/**
 * The window of commits that was counted.
 *
 * <p>The window is a number of commits and not a time span, so that two runs of the same project count the
 * same history. The window names itself and states what it leaves out, so that a number of the result can be
 * read together with the bound it was counted under.
 */
public record HistoryWindow(int requestedCommits,
                            int countedCommits,
                            String description,
                            String excluded) {
}
