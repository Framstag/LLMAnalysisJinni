package com.framstag.llmaj.tools.common;

import de.siegmar.fastcsv.writer.CsvWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CsvReportWriter {

    public static void writeCsv(Path workingDir, String filename, String[] header, List<String[]> rows) {
        if (rows == null || rows.isEmpty()) return;
        Path csvPath = workingDir.resolve(filename);
        try (CsvWriter csv = CsvWriter.builder().build(csvPath)) {
            csv.writeRecord(header);
            for (String[] row : rows) {
                csv.writeRecord(row);
            }
        } catch (IOException e) {
            System.err.println("Error writing CSV " + filename + ": " + e.getMessage());
        }
    }

    public static void writeMapCsv(Path workingDir, String filename, Map<String, Integer> data) {
        List<String[]> rows = new LinkedList<>();
        for (String k : data.keySet().stream().sorted().toList()) {
            rows.add(new String[]{k, String.valueOf(data.get(k))});
        }
        writeCsv(workingDir, filename, new String[]{"Key", "Count"}, rows);
    }

    public static void writeMultiMapCsv(Path workingDir, String filename, String[] categories,
                                         Map<String, Integer>... maps) {
        List<String[]> rows = new LinkedList<>();
        for (int i = 0; i < maps.length && i < categories.length; i++) {
            for (String k : maps[i].keySet().stream().sorted().toList()) {
                rows.add(new String[]{categories[i], k, String.valueOf(maps[i].get(k))});
            }
        }
        writeCsv(workingDir, filename, new String[]{"Category", "Key", "Count"}, rows);
    }

    public static void writeIntMapCsv(Path workingDir, String filename, Map<Integer, Integer> data) {
        List<String[]> rows = new LinkedList<>();
        for (int k : data.keySet().stream().sorted().toList()) {
            rows.add(new String[]{String.valueOf(k), String.valueOf(data.get(k))});
        }
        writeCsv(workingDir, filename, new String[]{"Value", "Count"}, rows);
    }

    public static void writeMultiIntMapCsv(Path workingDir, String filename, String[] categories,
                                            Map<Integer, Integer>... maps) {
        List<String[]> rows = new LinkedList<>();
        for (int i = 0; i < maps.length && i < categories.length; i++) {
            for (int k : maps[i].keySet().stream().sorted().toList()) {
                rows.add(new String[]{categories[i], String.valueOf(k), String.valueOf(maps[i].get(k))});
            }
        }
        writeCsv(workingDir, filename, new String[]{"Category", "Value", "Count"}, rows);
    }

    public static void writeListCsv(Path workingDir, String filename, String header, List<String> data) {
        List<String[]> rows = new LinkedList<>();
        for (String item : data) {
            rows.add(new String[]{item});
        }
        writeCsv(workingDir, filename, new String[]{header}, rows);
    }
}