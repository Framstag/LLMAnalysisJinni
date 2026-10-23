package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.tools.common.CsvReportWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the seam evidence of the project to disk.
 *
 * <p>One report type, one file, one row per interface:
 *
 * <pre>
 * Seams/Seams.csv   every interface with its declaring module, its implementor count, its consumer count
 *                   and the modules that import it
 * </pre>
 *
 * <p>The rows are written in the order of the result, so the file carries the same rank the model reads. No
 * wall clock time and no run identifier is written, so two runs over the same module reports produce
 * byte-identical files.
 */
public final class SeamReports {

    public static final String REPORT_TYPE = "Seams";

    public static final String REPORT_FILE = REPORT_TYPE + "/Seams.csv";

    private static final String[] HEADER = {"Interface", "DeclaringModule", "ImplementorCount", "ConsumerCount",
            "ImportingModules"};

    private SeamReports() {
    }

    public static void write(Path workingDirectory, List<Seam> seams) {
        if (seams.isEmpty()) {
            // A report with nothing to report is still written: a missing file would look like an analysis
            // that never ran.
            CsvReportWriter.writeCsvHeader(workingDirectory, REPORT_FILE, HEADER);
            return;
        }

        List<String[]> rows = new ArrayList<>(seams.size());

        for (Seam seam : seams) {
            rows.add(new String[]{seam.interfaceName(),
                    seam.declaringModule(),
                    String.valueOf(seam.implementorCount()),
                    String.valueOf(seam.consumerCount()),
                    String.join(" ", seam.importingModules())});
        }

        CsvReportWriter.writeCsv(workingDirectory, REPORT_FILE, HEADER, rows);
    }
}
