package com.framstag.llmaj.tools.history;

/**
 * The named cases in which the history of a project cannot be read.
 *
 * <p>The three cases are kept apart because a reader can act on each of them differently: the first one is a
 * fact about the project, the second one is a fact about the machine and the third one is a fact about the
 * copy of the project. A single generic "no history" answer would not tell them apart.
 */
public enum HistoryAbsence {
    /** The history was counted. */
    NONE,

    /** The project directory is not inside a repository, so it holds no history to count. */
    NOT_A_REPOSITORY,

    /** The machine holds no git executable, so the history cannot be read at all. */
    GIT_EXECUTABLE_MISSING,

    /** The copy of the project is a shallow clone: it holds the recent commits and misses the older ones. */
    HISTORY_TRUNCATED
}
