package com.framstag.llmaj.tools.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Counts the version history of the project directory.
 *
 * <p>The history is read with the git executable of the machine, so that the project adds no dependency for
 * the count. The counting needs one pass over the commits of the window: the per file counts, the dates, the
 * authors and the pairs of files that changed together all come out of that one pass.
 *
 * <p>The counted window is a number of commits and not a time span, so that two runs of the same project count
 * the same history. The result names the window it counted and states what the bound leaves out.
 *
 * <p>A history that cannot be read is a result and not a failure. The three cases that can happen are named in
 * the result: a project directory that is not a repository, a machine that holds no git executable and a copy
 * of the project whose history is truncated. A truncated copy is not an absent history: it holds the recent
 * commits, and those are counted as usual.
 */
public class HistoryTool {
    private static final Logger logger = LoggerFactory.getLogger(HistoryTool.class);

    /**
     * The name of the system property that names the git executable to run. The property exists so that a
     * machine whose git executable is not on the path, and a test that needs a different one, can name the
     * executable. Without the property the git executable of the path is run, so a normal run is not changed
     * by it.
     */
    public static final String GIT_EXECUTABLE_PROPERTY = "llmaj.tools.history.gitExecutable";

    /** The git executable that is run when the property does not name one. */
    public static final String DEFAULT_GIT_EXECUTABLE = "git";

    /** The number of commits the counted window holds. */
    public static final int DEFAULT_COMMIT_WINDOW = 500;

    /** The number of files a commit may touch before the pairs of that commit are not counted. */
    public static final int DEFAULT_MAX_FILES_PER_COMMIT = 50;

    private static final char COMMIT_SEPARATOR = '\u001e';
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final char ENTRY_SEPARATOR = '\0';

    private final AnalysisContext context;
    private final String gitExecutable;
    private final int commitWindow;
    private final int maxFilesPerCommit;

    public HistoryTool(AnalysisContext context) {
        this(context,
             System.getProperty(GIT_EXECUTABLE_PROPERTY, DEFAULT_GIT_EXECUTABLE),
             DEFAULT_COMMIT_WINDOW,
             DEFAULT_MAX_FILES_PER_COMMIT);
    }

    public HistoryTool(AnalysisContext context, String gitExecutable, int commitWindow, int maxFilesPerCommit) {
        this.context = context;
        this.gitExecutable = gitExecutable;
        this.commitWindow = commitWindow;
        this.maxFilesPerCommit = maxFilesPerCommit;
        logger.info("HistoryTool initialized, counting the last {} commits with '{}'.", commitWindow, gitExecutable);
    }

    @Tool(name = "history_read_version_history",
            value =
                    """
                        Returns the version history of the project directory.

                        For every file the history knows: the number of commits that changed it, the lines added
                        and the lines removed over those commits, the date of the first and the date of the last
                        change and the authors that changed it. A file the history does not know holds no row.

                        Beside that: the pairs of files that were changed in the same commit, with the number of
                        commits the pair shared, the history of every build module of the project, with the
                        number of its changed files, its churn, the date of its last change and the number
                        of commits every author of it made, and the window of commits that was counted.

                        A history that cannot be read is reported as absent, with the reason named.

                        The counted evidence is written as the CSV reports "VersionHistory/Files.csv",
                        "VersionHistory/Modules.csv" and "VersionHistory/FilePairs.csv" of the workspace, one
                        file per view, because the views do not share their columns.
                        """)
    public HistoryResult readVersionHistory() {
        Path projectRoot = context.getProjectRoot();
        GitRunner git = new GitRunner(gitExecutable, projectRoot);

        logger.info("## ReadVersionHistory('{}')", projectRoot);

        Optional<GitRunner.Result> insideWorkTree = git.run(List.of("rev-parse", "--is-inside-work-tree"));
        if (insideWorkTree.isEmpty()) {
            return absentHistory(HistoryAbsence.GIT_EXECUTABLE_MISSING, gitExecutableMissingReason());
        }

        if (insideWorkTree.get().exitCode() != 0 || !"true".equals(insideWorkTree.get().output().trim())) {
            return absentHistory(HistoryAbsence.NOT_A_REPOSITORY,
                    "the project directory '" + projectRoot + "' is not a repository");
        }

        Optional<GitRunner.Result> head = git.run(List.of("rev-parse", "--verify", "HEAD"));
        if (head.isEmpty()) {
            return absentHistory(HistoryAbsence.GIT_EXECUTABLE_MISSING, gitExecutableMissingReason());
        }

        if (head.get().exitCode() != 0) {
            // A repository without a commit holds no history to count. The history is readable and empty, so
            // this is not one of the absent cases.
            return new HistoryResult(HistoryAbsence.NONE,
                    "the repository holds no commit yet",
                    new HistoryWindow(commitWindow, 0, windowDescription(),
                            "the window holds no commit, the repository is empty"),
                    maxFilesPerCommit,
                    0,
                    List.of(),
                    List.of(),
                    List.of());
        }

        boolean truncated = isTruncated(git);
        String projectPrefix = projectPrefix(git, projectRoot);

        List<String> arguments = List.of("log",
                                         "-n", String.valueOf(commitWindow),
                                         "--no-renames",
                                         "--numstat",
                                         "-z",
                                         "--pretty=format:%x1e%H%x1f%an%x1f%aI");

        Optional<GitRunner.Result> log = git.run(arguments);
        if (log.isEmpty()) {
            return absentHistory(HistoryAbsence.GIT_EXECUTABLE_MISSING, gitExecutableMissingReason());
        }

        if (log.get().exitCode() != 0) {
            return absentHistory(HistoryAbsence.NOT_A_REPOSITORY,
                    "the project directory '" + projectRoot + "' is not a repository, the history cannot be read: "
                            + errorMessage(log.get()));
        }

        List<CommitChange> commits = parseLog(log.get().output());
        CountedHistory counted = countHistory(commits, projectPrefix);

        String oldestDate = commits.isEmpty() ? "" : commits.getLast().date();

        String reason;
        String excluded;
        HistoryAbsence absence;

        if (truncated) {
            absence = HistoryAbsence.HISTORY_TRUNCATED;
            reason = "the history is truncated, the commits before the oldest commit the copy holds"
                     + (oldestDate.isEmpty() ? "" : " (" + oldestDate + ")") + " are missing";
            excluded = "the commits before the oldest commit the copy holds"
                       + (oldestDate.isEmpty() ? "" : " (" + oldestDate + ")") + " are missing";
        } else if (counted.commits() < commitWindow) {
            absence = HistoryAbsence.NONE;
            reason = "the history of the repository was counted";
            excluded = "the window holds the whole history of the repository, no commit is left out";
        } else {
            absence = HistoryAbsence.NONE;
            reason = "the history of the repository was counted";
            excluded = "the commits before the oldest counted commit"
                       + (oldestDate.isEmpty() ? "" : " (" + oldestDate + ")") + " are left out of the window";
        }

        HistoryResult result = new HistoryResult(absence,
                                                 reason,
                                                 new HistoryWindow(commitWindow, counted.commits(),
                                                         windowDescription(), excluded),
                                                 maxFilesPerCommit,
                                                 counted.skippedCommits(),
                                                 counted.files(),
                                                 counted.pairs(),
                                                 ModuleAggregator.aggregate(counted.files(), buildModules()));

        logger.info("# => {} files, {} pairs, {} modules, window of {} commits", counted.files().size(),
                counted.pairs().size(), result.modules().size(), counted.commits());

        HistoryReports.write(context.getWorkingDirectory(), result);

        return result;
    }

    /**
     * @return the reason of a machine that holds no git executable
     */
    private String gitExecutableMissingReason() {
        return "the git executable '" + gitExecutable + "' is not available on this machine";
    }

    /**
     * @return the name of the window that is counted
     */
    private String windowDescription() {
        return "the most recent " + commitWindow + " commits of the repository";
    }

    /**
     * @return true when the copy of the project holds only the recent commits
     */
    private boolean isTruncated(GitRunner git) {
        Optional<GitRunner.Result> shallow = git.run(List.of("rev-parse", "--is-shallow-repository"));
        return shallow.isPresent() && "true".equals(shallow.get().output().trim());
    }

    /**
     * @return the path of the project directory relative to the root of the repository, or an empty string
     * when the project directory is the root of the repository
     */
    private String projectPrefix(GitRunner git, Path projectRoot) {
        Optional<GitRunner.Result> topLevel = git.run(List.of("rev-parse", "--show-toplevel"));
        if (topLevel.isEmpty() || topLevel.get().exitCode() != 0) {
            return "";
        }

        String topLevelPath = topLevel.get().output().trim();
        if (topLevelPath.isEmpty()) {
            return "";
        }

        try {
            Path repositoryRoot = Path.of(topLevelPath).toRealPath();
            Path project = projectRoot.toRealPath();

            if (project.equals(repositoryRoot)) {
                return "";
            }

            return repositoryRoot.relativize(project).toString().replace('\\', '/');
        } catch (IOException e) {
            logger.info("The root of the repository cannot be read: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Parses the machine readable output of the git log.
     *
     * <p>Every commit is written as a record separator, the hash, the author and the date of the commit and
     * the files it changed with the lines they added and removed.
     */
    private List<CommitChange> parseLog(String output) {
        List<CommitChange> commits = new ArrayList<>();

        String[] chunks = output.split(String.valueOf(COMMIT_SEPARATOR), -1);
        for (int i = 1; i < chunks.length; i++) {
            String[] entries = chunks[i].split(String.valueOf(ENTRY_SEPARATOR), -1);

            String header = entries[0];
            String firstEntry = null;

            int lineEnd = header.indexOf('\n');
            if (lineEnd >= 0) {
                firstEntry = header.substring(lineEnd + 1);
                header = header.substring(0, lineEnd);
            }

            String[] fields = header.split(String.valueOf(FIELD_SEPARATOR), 3);
            if (fields.length < 3) {
                logger.info("A commit of the history cannot be read: '{}'", header);
                continue;
            }

            List<FileChange> changes = new ArrayList<>();
            if (firstEntry != null && !firstEntry.isEmpty()) {
                changes.addAll(parseFileChanges(firstEntry));
            }
            for (int e = 1; e < entries.length; e++) {
                if (!entries[e].isEmpty()) {
                    changes.addAll(parseFileChanges(entries[e]));
                }
            }

            commits.add(new CommitChange(fields[0], fields[1], fields[2], changes));
        }

        return commits;
    }

    /**
     * Parses the files a commit changed: the lines they added, the lines they removed and their path.
     */
    private List<FileChange> parseFileChanges(String entry) {
        List<FileChange> changes = new ArrayList<>();

        String[] fields = entry.split("\t", 3);
        if (fields.length < 3) {
            return changes;
        }

        changes.add(new FileChange(fields[2], countLines(fields[0]), countLines(fields[1])));

        return changes;
    }

    /**
     * @return the number of lines, which git writes as a dash for a file it cannot count
     */
    private int countLines(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Counts the per file history and the pairs of files that changed together out of the commits of the
     * window.
     */
    private CountedHistory countHistory(List<CommitChange> commits, String projectPrefix) {
        Map<String, FileCounter> counters = new LinkedHashMap<>();
        Map<PairKey, Integer> pairs = new LinkedHashMap<>();
        int skippedCommits = 0;

        for (CommitChange commit : commits) {
            // A commit changes a file once, so the files of the commit are held by their path. A path that
            // the commit names twice is one file of the commit: the commit counts once for the file and the
            // lines of both entries count.
            Map<String, FileChange> changedFiles = new LinkedHashMap<>();

            for (FileChange change : commit.changes()) {
                String path = projectPath(change.path(), projectPrefix);
                if (path == null) {
                    continue;
                }

                FileChange known = changedFiles.get(path);
                if (known == null) {
                    changedFiles.put(path, change);
                } else {
                    changedFiles.put(path, new FileChange(path,
                                                           known.added() + change.added(),
                                                           known.removed() + change.removed()));
                }
            }

            for (Map.Entry<String, FileChange> changed : changedFiles.entrySet()) {
                counters.computeIfAbsent(changed.getKey(), p -> new FileCounter())
                        .add(commit.author(), commit.date(), changed.getValue().added(),
                                changed.getValue().removed());
            }

            List<String> paths = List.copyOf(changedFiles.keySet());
            if (paths.size() > maxFilesPerCommit) {
                // A commit that touches an unusual number of files would produce a quadratic number of pairs,
                // so the pairs of that commit are not counted and the commit is named in the result.
                skippedCommits++;
                continue;
            }

            for (int i = 0; i < paths.size(); i++) {
                for (int j = i + 1; j < paths.size(); j++) {
                    pairs.merge(PairKey.of(paths.get(i), paths.get(j)), 1, Integer::sum);
                }
            }
        }

        List<FileHistory> files = counters.entrySet().stream()
                .map(entry -> entry.getValue().toFileHistory(entry.getKey()))
                .sorted(Comparator.comparingInt(FileHistory::commitCount).reversed()
                        .thenComparing(FileHistory::path))
                .toList();

        List<FilePair> filePairs = pairs.entrySet().stream()
                .map(entry -> new FilePair(entry.getKey().first(), entry.getKey().second(), entry.getValue()))
                .sorted(Comparator.comparingInt(FilePair::sharedCommits).reversed()
                        .thenComparing(FilePair::firstFile)
                        .thenComparing(FilePair::secondFile))
                .toList();

        return new CountedHistory(commits.size(), skippedCommits, files, filePairs);
    }

    /**
     * @return the path of the file relative to the project directory, or null when the file is outside of it
     */
    private String projectPath(String path, String projectPrefix) {
        if (projectPrefix.isEmpty()) {
            return path;
        }

        String prefix = projectPrefix + "/";
        if (!path.startsWith(prefix)) {
            return null;
        }

        return path.substring(prefix.length());
    }

    /**
     * @return the message of the git executable, or its exit code when it wrote no message
     */
    private String errorMessage(GitRunner.Result result) {
        String error = result.error().trim();
        if (error.isEmpty()) {
            error = "the git executable returned the exit code " + result.exitCode();
        }
        return error;
    }

    private HistoryResult absentHistory(HistoryAbsence absence, String reason) {
        logger.info("# => history absent: {}", reason);
        return new HistoryResult(absence,
                                 reason,
                                 new HistoryWindow(commitWindow, 0, windowDescription(),
                                         "no commit was counted, the history could not be read"),
                                 maxFilesPerCommit,
                                 0,
                                 List.of(),
                                 List.of(),
                                 List.of());
    }

    /**
     * Reads the build modules of the project from the analysis state.
     *
     * @return the modules of the analysis state, or an empty list when the analysis state holds none
     */
    private List<ModuleDescriptor> buildModules() {
        ObjectNode analysisState = context.getAnalysisState();
        if (analysisState == null) {
            return List.of();
        }

        JsonNode modulesSection = analysisState.get("modules");
        if (modulesSection == null || modulesSection.isNull()) {
            return List.of();
        }

        JsonNode modulesArray = modulesSection.get("modules");
        if (modulesArray == null || !modulesArray.isArray()) {
            logger.info("The analysis state holds no module list.");
            return List.of();
        }

        List<ModuleDescriptor> modules = new ArrayList<>();
        for (JsonNode moduleNode : modulesArray) {
            String path = textOf(moduleNode, "path");
            String name = textOf(moduleNode, "name");
            if (name.isBlank()) {
                name = path;
            }
            modules.add(new ModuleDescriptor(name, path));
        }

        return modules;
    }

    /**
     * @return the text of a field of the analysis state, or an empty string when the field is not there
     */
    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    /**
     * The counted history of one file, while the commits of the window are counted.
     */
    private static final class FileCounter {
        private int commitCount;
        private int linesAdded;
        private int linesRemoved;
        private String firstChangeDate;
        private String lastChangeDate;
        private Instant firstChange;
        private Instant lastChange;
        private final Map<String, Integer> authorCommits = new TreeMap<>();

        void add(String author, String date, int added, int removed) {
            commitCount++;
            linesAdded += added;
            linesRemoved += removed;
            authorCommits.merge(author, 1, Integer::sum);

            Instant moment = parseDate(date);
            if (moment == null) {
                if (firstChangeDate == null) {
                    firstChangeDate = date;
                }
                if (lastChangeDate == null) {
                    lastChangeDate = date;
                }
                return;
            }

            if (firstChange == null || moment.isBefore(firstChange)) {
                firstChange = moment;
                firstChangeDate = date;
            }
            if (lastChange == null || moment.isAfter(lastChange)) {
                lastChange = moment;
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

        FileHistory toFileHistory(String path) {
            return new FileHistory(path,
                                  commitCount,
                                  linesAdded,
                                  linesRemoved,
                                  firstChangeDate,
                                  lastChangeDate,
                                  List.copyOf(authorCommits.keySet()),
                                  Collections.unmodifiableMap(new TreeMap<>(authorCommits)));
        }
    }

    /**
     * A pair of files, in the order that names them the same way in every run.
     */
    private record PairKey(String first, String second) {
        static PairKey of(String one, String other) {
            if (one.compareTo(other) <= 0) {
                return new PairKey(one, other);
            }
            return new PairKey(other, one);
        }
    }

    /**
     * A commit of the window with the files it changed.
     */
    private record CommitChange(String hash, String author, String date, List<FileChange> changes) {
    }

    /**
     * A file a commit changed, with the lines it added and removed.
     */
    private record FileChange(String path, int added, int removed) {
    }

    /**
     * The counted history of the window.
     */
    private record CountedHistory(int commits,
                                  int skippedCommits,
                                  List<FileHistory> files,
                                  List<FilePair> pairs) {
    }
}
