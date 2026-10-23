package com.framstag.llmaj.tools.history;

import java.util.List;

/**
 * The counted history of one build module of the project.
 *
 * <p>The figures of a module are derived from the counted history of the files of the module, so that every
 * number of a module can be traced back to the files behind it.
 *
 * <p>A module that no counted file belongs to holds no row, in the same way as a file the history does not
 * know holds no row.
 */
public record ModuleHistory(String name,
                            String path,
                            int changedFileCount,
                            int linesAdded,
                            int linesRemoved,
                            int churn,
                            String lastChangeDate,
                            int authorCount,
                            List<ModuleAuthor> authors) {
}
