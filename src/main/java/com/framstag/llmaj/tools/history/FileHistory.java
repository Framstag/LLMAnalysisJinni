package com.framstag.llmaj.tools.history;

import java.util.List;
import java.util.Map;

/**
 * The counted history of one file of the project.
 *
 * <p>A file the history does not know holds no row, so every row of this record counts at least one commit.
 *
 * <p>The commits of every author of the file are held beside the names of the authors, so that the history
 * of a module can be derived from the files of the module without a second pass over the history.
 */
public record FileHistory(String path,
                          int commitCount,
                          int linesAdded,
                          int linesRemoved,
                          String firstChangeDate,
                          String lastChangeDate,
                          List<String> authors,
                          Map<String, Integer> authorCommitCounts) {
}
