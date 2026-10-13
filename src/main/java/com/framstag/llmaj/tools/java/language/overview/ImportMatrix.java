package com.framstag.llmaj.tools.java.language.overview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The import counts between the modules, read from the matrix that the inter-module dependency analysis wrote.
 *
 * <p>The matrix is read rather than recomputed. It is the structural side of the quadrant, and a second
 * computation of the same number would eventually disagree with the first one, which would make the quadrant
 * compare two numbers that came from different rules.
 *
 * <p>A matrix that is absent is not the same as a matrix that holds no import: the first one means the
 * structural side of the quadrant is unknown, the second one means it is zero. The two are kept apart.
 */
public record ImportMatrix(Map<String, Integer> importCounts, String file, boolean available) {

    public static final String REPORT_DIRECTORY = "InterModuleDependencyMatrix";

    public static final String REPORT_FILE = REPORT_DIRECTORY + "/" + REPORT_DIRECTORY + ".csv";

    private static final String NOT_AVAILABLE = "";

    public static ImportMatrix absent() {
        return new ImportMatrix(Map.of(), NOT_AVAILABLE, false);
    }

    /**
     * @return the matrix of the working directory, or an absent matrix when the inter-module dependency
     * analysis has not written one
     */
    public static ImportMatrix read(Path workingDirectory) {
        Path path = workingDirectory.resolve(REPORT_FILE);

        if (!Files.isRegularFile(path)) {
            return new ImportMatrix(Map.of(), path.toString(), false);
        }

        Map<String, Integer> importCounts = new LinkedHashMap<>();

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            for (String line : lines) {
                String[] fields = line.split(",");

                if (fields.length < 3 || "From".equals(fields[0])) {
                    continue;
                }

                try {
                    importCounts.merge(key(fields[0], fields[1]), Integer.parseInt(fields[2].trim()), Integer::sum);
                } catch (NumberFormatException e) {
                    // A row that does not carry a count is not a row of the matrix; the rest of the file is.
                }
            }
        } catch (IOException e) {
            return new ImportMatrix(Map.of(), path.toString(), false);
        }

        return new ImportMatrix(Map.copyOf(importCounts), path.toString(), true);
    }

    /**
     * @return the number of imports from one module to the other in either direction, or null when the matrix
     * is not available
     */
    public Integer importCountBetween(String firstModule, String secondModule) {
        if (!available) {
            return null;
        }

        int forwards = importCounts.getOrDefault(key(firstModule, secondModule), 0);
        int backwards = importCounts.getOrDefault(key(secondModule, firstModule), 0);

        return forwards + backwards;
    }

    private static String key(String from, String to) {
        return from + ">" + to;
    }
}
