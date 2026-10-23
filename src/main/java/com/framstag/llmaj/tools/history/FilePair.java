package com.framstag.llmaj.tools.history;

/**
 * A pair of files that were changed in the same commits.
 *
 * <p>A pair that shared a single commit is reported as well: the unit of work the history shows is the
 * evidence, and a pair that shared one commit is not noise.
 */
public record FilePair(String firstFile, String secondFile, int sharedCommits) {
}
