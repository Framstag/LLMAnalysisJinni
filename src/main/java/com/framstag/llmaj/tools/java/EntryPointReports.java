package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.tools.common.CsvReportWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the entry point evidence of the project to disk.
 *
 * <p>One report type, one file, one row per class that can be an entry point:
 *
 * <pre>
 * EntryPoints/EntryPoints.csv   every class that declares a main method, that carries an annotation or that
 *                               implements an interface, with its module, its code category, its annotations
 *                               and its implemented interfaces
 * </pre>
 *
 * <p>The rows are written in the order of the result, so the file carries the same order the model reads. No
 * wall clock time and no run identifier is written, so two runs over the same module reports produce
 * byte-identical files.
 */
public final class EntryPointReports {

    public static final String REPORT_TYPE = "EntryPoints";

    public static final String REPORT_FILE = REPORT_TYPE + "/EntryPoints.csv";

    private static final String[] HEADER = {"ClassName", "Module", "CodeCategory", "Annotations", "Interfaces"};

    private EntryPointReports() {
    }

    public static void write(Path workingDirectory, List<EntryPoint> entryPoints) {
        if (entryPoints.isEmpty()) {
            // A report with nothing to report is still written: a missing file would look like an analysis
            // that never ran.
            CsvReportWriter.writeCsvHeader(workingDirectory, REPORT_FILE, HEADER);
            return;
        }

        List<String[]> rows = new ArrayList<>(entryPoints.size());

        for (EntryPoint entryPoint : entryPoints) {
            rows.add(new String[]{entryPoint.className(),
                    entryPoint.module(),
                    entryPoint.codeCategory(),
                    String.join(" ", entryPoint.annotations()),
                    String.join(" ", entryPoint.interfaces())});
        }

        CsvReportWriter.writeCsv(workingDirectory, REPORT_FILE, HEADER, rows);
    }
}
