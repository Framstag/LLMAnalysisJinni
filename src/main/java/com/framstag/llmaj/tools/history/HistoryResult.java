package com.framstag.llmaj.tools.history;

import java.util.List;

/**
 * The counted version history of the project directory.
 *
 * <p>A history that cannot be read is a result and not a failure: the result names the case it is in, so that
 * a reader can tell a project without a history apart from a machine that cannot read one.
 *
 * <p>Beside the counted files and the counted pairs of files, the result holds the counted history of the
 * build modules, which is derived from the counted files.
 */
public record HistoryResult(HistoryAbsence absence,
                            String reason,
                            HistoryWindow window,
                            int maxFilesPerCommit,
                            int commitsSkippedForPairCount,
                            List<FileHistory> files,
                            List<FilePair> pairs,
                            List<ModuleHistory> modules) {

    /**
     * @return true when the history was counted, which holds for a complete and for a truncated history
     */
    public boolean isAvailable() {
        return absence == HistoryAbsence.NONE || absence == HistoryAbsence.HISTORY_TRUNCATED;
    }

    /**
     * @return true when the copy of the project holds only the recent commits
     */
    public boolean isTruncated() {
        return absence == HistoryAbsence.HISTORY_TRUNCATED;
    }
}
