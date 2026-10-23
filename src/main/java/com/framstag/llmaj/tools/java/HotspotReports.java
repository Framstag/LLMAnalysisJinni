package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.tools.common.CsvReportWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the hotspot evidence of the project to disk.
 *
 * <p>One report type, one file, one row per class of the ranking:
 *
 * <pre>
 * Hotspots/Hotspots.csv   every ranked class with the modules it appears in, its change count, its
 *                         complexity and its rank
 * </pre>
 *
 * <p>A class that several module reports hold is written once, with the modules it appears in, so the file
 * holds one row per class and not one row per module report.
 *
 * <p>The rows are written in the order of the ranking, so the file carries the same rank the model reads. No
 * wall clock time and no run identifier is written, so two runs over the same module reports and the same
 * history produce byte-identical files.
 *
 * <p>A history that is absent is not written: such a project holds no counted hotspots, and empty files would
 * tell a reader that the project holds no hotspot, while the truth is that the history could not be read.
 */
public final class HotspotReports {

    public static final String REPORT_TYPE = "Hotspots";

    public static final String REPORT_FILE = REPORT_TYPE + "/Hotspots.csv";

    private static final String[] HEADER = {"ClassName", "Modules", "ChangeCount", "Complexity", "Rank"};

    private HotspotReports() {
    }

    /**
     * Writes the counted hotspots as the report of the workspace.
     *
     * @param workingDirectory the working directory of the workspace
     * @param hotspots         the ranked classes of the project
     */
    public static void write(Path workingDirectory, List<Hotspot> hotspots) {
        if (hotspots.isEmpty()) {
            // A report with nothing to report is still written: a missing file would look like an analysis
            // that never ran.
            CsvReportWriter.writeCsvHeader(workingDirectory, REPORT_FILE, HEADER);
            return;
        }

        List<String[]> rows = new ArrayList<>(hotspots.size());

        for (Hotspot hotspot : hotspots) {
            rows.add(new String[]{hotspot.className(),
                    String.join(" ", hotspot.modules()),
                    String.valueOf(hotspot.changeCount()),
                    String.valueOf(hotspot.complexity()),
                    String.valueOf(hotspot.rank())});
        }

        CsvReportWriter.writeCsv(workingDirectory, REPORT_FILE, HEADER, rows);
    }
}
