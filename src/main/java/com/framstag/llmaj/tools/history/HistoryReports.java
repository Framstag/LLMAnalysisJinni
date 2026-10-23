package com.framstag.llmaj.tools.history;

import com.framstag.llmaj.tools.common.CsvReportWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the counted version history of the project to disk.
 *
 * <p>One report type, one file per view, because the views do not share their columns: the counted files, the
 * counted modules and the counted pairs of files that changed together are read for different questions.
 *
 * <pre>
 * VersionHistory/Files.csv      every counted file with its commit count, its added and removed lines, the
 *                               dates of its first and its last change and the authors that changed it
 * VersionHistory/Modules.csv    every counted module with its changed file count, its churn, the date of its
 *                               last change and its authors with their commit counts
 * VersionHistory/FilePairs.csv  every pair of files that changed together with the number of commits they shared
 * </pre>
 *
 * <p>The rows are written in the order of the counted history, so that the file carries the same rank the
 * model reads. No wall clock time and no run identifier is written, so two runs over the same repository
 * produce byte-identical files.
 *
 * <p>A history that cannot be read is not written: such a history holds no counts, and empty files would tell
 * a reader that the project holds no changed file, while the truth is that the history could not be read. The
 * result of the tool names the case instead.
 */
public final class HistoryReports {

    public static final String REPORT_TYPE = "VersionHistory";

    public static final String FILES_REPORT = REPORT_TYPE + "/Files.csv";
    public static final String MODULES_REPORT = REPORT_TYPE + "/Modules.csv";
    public static final String FILE_PAIRS_REPORT = REPORT_TYPE + "/FilePairs.csv";

    private static final String[] FILES_HEADER = {"File", "CommitCount", "LinesAdded", "LinesRemoved",
            "FirstChangeDate", "LastChangeDate", "Authors"};

    private static final String[] MODULES_HEADER = {"Module", "ChangedFileCount", "Churn", "LastChangeDate",
            "AuthorCount", "Authors"};

    private static final String[] FILE_PAIRS_HEADER = {"FirstFile", "SecondFile", "SharedCommits"};

    private HistoryReports() {
    }

    /**
     * Writes the counted history as the three views of the report.
     *
     * @param workingDirectory the working directory of the workspace
     * @param result the counted history of the project
     */
    public static void write(Path workingDirectory, HistoryResult result) {
        if (!result.isAvailable()) {
            return;
        }

        writeFiles(workingDirectory, result.files());
        writeModules(workingDirectory, result.modules());
        writeFilePairs(workingDirectory, result.pairs());
    }

    private static void writeFiles(Path workingDirectory, List<FileHistory> files) {
        List<String[]> rows = new ArrayList<>(files.size());

        for (FileHistory file : files) {
            rows.add(new String[]{file.path(),
                    String.valueOf(file.commitCount()),
                    String.valueOf(file.linesAdded()),
                    String.valueOf(file.linesRemoved()),
                    file.firstChangeDate(),
                    file.lastChangeDate(),
                    String.join(" ", file.authors())});
        }

        write(workingDirectory, FILES_REPORT, FILES_HEADER, rows);
    }

    private static void writeModules(Path workingDirectory, List<ModuleHistory> modules) {
        List<String[]> rows = new ArrayList<>(modules.size());

        for (ModuleHistory module : modules) {
            rows.add(new String[]{module.name(),
                    String.valueOf(module.changedFileCount()),
                    String.valueOf(module.churn()),
                    module.lastChangeDate(),
                    String.valueOf(module.authorCount()),
                    authorsOf(module)});
        }

        write(workingDirectory, MODULES_REPORT, MODULES_HEADER, rows);
    }

    private static void writeFilePairs(Path workingDirectory, List<FilePair> pairs) {
        List<String[]> rows = new ArrayList<>(pairs.size());

        for (FilePair pair : pairs) {
            rows.add(new String[]{pair.firstFile(),
                    pair.secondFile(),
                    String.valueOf(pair.sharedCommits())});
        }

        write(workingDirectory, FILE_PAIRS_REPORT, FILE_PAIRS_HEADER, rows);
    }

    /**
     * The authors of a module with the number of commits every one of them made, so that a module whose
     * authorship is concentrated can be told apart from a module that many people work on.
     */
    private static String authorsOf(ModuleHistory module) {
        List<String> authors = new ArrayList<>(module.authors().size());

        for (ModuleAuthor author : module.authors()) {
            authors.add(author.name() + ":" + author.commitCount());
        }

        return String.join(" ", authors);
    }

    /**
     * A view that has nothing to report is still written, with its header and no row: the three files are read
     * as one set of evidence, and a missing file would look like an analysis that never ran.
     */
    private static void write(Path workingDirectory, String report, String[] header, List<String[]> rows) {
        if (rows.isEmpty()) {
            CsvReportWriter.writeCsvHeader(workingDirectory, report, header);
            return;
        }

        CsvReportWriter.writeCsv(workingDirectory, report, header, rows);
    }
}
