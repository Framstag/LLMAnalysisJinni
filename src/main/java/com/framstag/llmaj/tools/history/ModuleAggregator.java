package com.framstag.llmaj.tools.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates the counted history of the files into the counted history of the build modules.
 *
 * <p>The aggregation is derived from the per file counts and not counted from a second pass over the history:
 * a file belongs to the module whose path is a prefix of the path of the file, and the figures of the module
 * are the sum of the figures of its files. A file that belongs to no module path belongs to no module.
 *
 * <p>The commit count of an author of a module is the sum of the commits of that author over the changed
 * files of the module.
 */
public final class ModuleAggregator {
    private static final Logger logger = LoggerFactory.getLogger(ModuleAggregator.class);

    private ModuleAggregator() {
    }

    /**
     * Aggregates the counted files into the counted modules.
     *
     * @param files the counted history of the files of the project
     * @param modules the build modules of the project, as the analysis state knows them
     * @return the counted history of the modules that hold at least one changed file
     */
    public static List<ModuleHistory> aggregate(List<FileHistory> files, List<ModuleDescriptor> modules) {
        if (modules.isEmpty()) {
            return List.of();
        }

        Map<ModuleDescriptor, ModuleAccumulator> accumulators = new LinkedHashMap<>();
        for (ModuleDescriptor module : modules) {
            accumulators.put(module, new ModuleAccumulator(module));
        }

        for (FileHistory file : files) {
            ModuleDescriptor owner = ownerOf(file.path(), modules);
            if (owner == null) {
                logger.info("The file '{}' belongs to no module of the analysis state.", file.path());
                continue;
            }
            accumulators.get(owner).add(file);
        }

        return accumulators.values().stream()
                .map(ModuleAccumulator::toModuleHistory)
                .filter(history -> history.changedFileCount() > 0)
                .sorted(Comparator.comparing(ModuleHistory::name))
                .toList();
    }

    /**
     * @return the module the file belongs to, which is the module with the longest path that is a prefix of
     * the path of the file, or null when no module path is a prefix of the path of the file
     */
    private static ModuleDescriptor ownerOf(String filePath, List<ModuleDescriptor> modules) {
        ModuleDescriptor owner = null;
        String ownerPath = "";

        for (ModuleDescriptor module : modules) {
            String path = normalize(module.path());
            if (!isPrefixOf(path, filePath)) {
                continue;
            }
            if (owner == null || path.length() > ownerPath.length()) {
                owner = module;
                ownerPath = path;
            }
        }

        return owner;
    }

    /**
     * @return true when the module path is a prefix of the file path; a module that holds the whole project
     * matches every file
     */
    private static boolean isPrefixOf(String modulePath, String filePath) {
        if (modulePath.isEmpty()) {
            return true;
        }
        return filePath.equals(modulePath) || filePath.startsWith(modulePath + "/");
    }

    /**
     * @return the path of a module without a leading "./" and without a trailing "/"; a path that names the
     * whole project becomes an empty path
     */
    private static String normalize(String path) {
        if (path == null) {
            return "";
        }

        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.equals(".")) {
            return "";
        }

        return normalized;
    }

    /**
     * The counted history of one module, while the files of the module are added.
     */
    private static final class ModuleAccumulator {
        private final ModuleDescriptor module;
        private final Map<String, Integer> authorCommits = new TreeMap<>();
        private int changedFileCount;
        private int linesAdded;
        private int linesRemoved;
        private String lastChangeDate;

        private ModuleAccumulator(ModuleDescriptor module) {
            this.module = module;
        }

        private void add(FileHistory file) {
            changedFileCount++;
            linesAdded += file.linesAdded();
            linesRemoved += file.linesRemoved();

            for (Map.Entry<String, Integer> author : file.authorCommitCounts().entrySet()) {
                authorCommits.merge(author.getKey(), author.getValue(), Integer::sum);
            }

            updateLastChange(file.lastChangeDate());
        }

        /**
         * Keeps the date of the last change of the module, which is the latest date of the last change of
         * its files.
         */
        private void updateLastChange(String date) {
            if (date == null || date.isEmpty()) {
                return;
            }
            if (lastChangeDate == null) {
                lastChangeDate = date;
                return;
            }

            Instant candidate = parseDate(date);
            Instant current = parseDate(lastChangeDate);

            if (candidate == null) {
                if (date.compareTo(lastChangeDate) > 0) {
                    lastChangeDate = date;
                }
            } else if (current == null || candidate.isAfter(current)) {
                lastChangeDate = date;
            }
        }

        private static Instant parseDate(String date) {
            try {
                return Instant.parse(date);
            } catch (DateTimeParseException e) {
                return null;
            }
        }

        private ModuleHistory toModuleHistory() {
            List<ModuleAuthor> authors = new ArrayList<>();
            for (Map.Entry<String, Integer> author : authorCommits.entrySet()) {
                authors.add(new ModuleAuthor(author.getKey(), author.getValue()));
            }
            authors.sort(Comparator.comparingInt(ModuleAuthor::commitCount).reversed()
                    .thenComparing(ModuleAuthor::name));

            return new ModuleHistory(module.name(),
                                     module.path(),
                                     changedFileCount,
                                     linesAdded,
                                     linesRemoved,
                                     linesAdded + linesRemoved,
                                     lastChangeDate,
                                     authors.size(),
                                     Collections.unmodifiableList(authors));
        }
    }
}
