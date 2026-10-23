package com.framstag.llmaj.tools.history;

import com.framstag.llmaj.AnalysisContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The history reader is tested over real repositories that are created in a temporary directory, so that the
 * counts, the dates and the authors are the ones of a real history and not the ones of a recorded output.
 */
public class HistoryToolTest {
    private static final int WINDOW = 100;
    private static final int MAX_FILES_PER_COMMIT = 50;
    private static final String MISSING_EXECUTABLE = "/does/not/exist/git";

    @TempDir
    Path tempDir;

    @Test
    void fileChangedInThreeCommitsByTwoAuthorsIsReported() throws Exception {
        Path repository = createRepository("three-commits");

        writeFile(repository, "src/Main.java", "line1\nline2\nline3\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "src/Main.java", "line1\nline2changed\nline3\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-02T10:00:00+00:00", "second");
        writeFile(repository, "src/Main.java", "line1\nline2changed\nline3\nline4\n");
        commit(repository, "Bob", "bob@example.com", "2024-01-03T10:00:00+00:00", "third");

        HistoryResult result = toolFor(repository, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertEquals(HistoryAbsence.NONE, result.absence());
        assertTrue(result.isAvailable());

        FileHistory history = fileOf(result, "src/Main.java");
        assertEquals(3, history.commitCount());
        assertEquals(5, history.linesAdded());
        assertEquals(1, history.linesRemoved());
        assertEquals("2024-01-01T10:00:00Z", history.firstChangeDate());
        assertEquals("2024-01-03T10:00:00Z", history.lastChangeDate());
        assertEquals(List.of("Alice", "Bob"), history.authors());
    }

    @Test
    void fileThatWasNeverCommittedHoldsNoRow() throws Exception {
        Path repository = createRepository("never-committed");

        writeFile(repository, "committed.txt", "content\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "uncommitted.txt", "content\n");

        HistoryResult result = toolFor(repository, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertTrue(result.files().stream().noneMatch(file -> file.path().equals("uncommitted.txt")),
                "a file the history does not know holds no row, got: " + result.files());
        assertEquals(1, result.files().size());
    }

    @Test
    void filesThatAlwaysChangeTogetherAreCountedAsAPair() throws Exception {
        Path repository = createRepository("always-together");

        writeFile(repository, "a.txt", "1\n");
        writeFile(repository, "b.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "a.txt", "2\n");
        writeFile(repository, "b.txt", "2\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-02T10:00:00+00:00", "second");
        writeFile(repository, "a.txt", "3\n");
        writeFile(repository, "b.txt", "3\n");
        commit(repository, "Bob", "bob@example.com", "2024-01-03T10:00:00+00:00", "third");

        HistoryResult result = toolFor(repository, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        FilePair pair = pairOf(result, "a.txt", "b.txt");
        assertEquals(3, pair.sharedCommits());
    }

    @Test
    void pairWithOneSharedCommitIsReported() throws Exception {
        Path repository = createRepository("one-shared-commit");

        writeFile(repository, "a.txt", "1\n");
        writeFile(repository, "b.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "a.txt", "2\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-02T10:00:00+00:00", "second");

        HistoryResult result = toolFor(repository, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        FilePair pair = pairOf(result, "a.txt", "b.txt");
        assertEquals(1, pair.sharedCommits(),
                "a pair that shared one commit is reported and not dropped as noise");
    }

    @Test
    void windowIsNamedAndAFileOlderThanTheWindowHoldsNoRow() throws Exception {
        Path repository = createRepository("window");

        writeFile(repository, "old.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "newer.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-02T10:00:00+00:00", "second");
        writeFile(repository, "newest.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-03T10:00:00+00:00", "third");

        HistoryResult result = toolFor(repository, 2, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertEquals(2, result.window().requestedCommits());
        assertEquals(2, result.window().countedCommits());
        assertTrue(result.window().description().contains("2"),
                "the result names the window it counted, got: " + result.window().description());
        assertTrue(result.window().excluded().contains("left out of the window"),
                "the result states what the window leaves out, got: " + result.window().excluded());
        assertTrue(result.files().stream().noneMatch(file -> file.path().equals("old.txt")),
                "a file that was last changed before the window holds no row, got: " + result.files());
        assertTrue(result.files().stream().anyMatch(file -> file.path().equals("newest.txt")),
                "a file that was changed inside the window holds a row, got: " + result.files());
    }

    @Test
    void commitThatTouchesMoreFilesThanTheBoundIsNotCountedForPairs() throws Exception {
        Path repository = createRepository("many-files");

        for (int file = 1; file <= 5; file++) {
            writeFile(repository, "file" + file + ".txt", "1\n");
        }
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "many files");

        HistoryResult result = toolFor(repository, WINDOW, 3).readVersionHistory();

        assertEquals(3, result.maxFilesPerCommit());
        assertEquals(1, result.commitsSkippedForPairCount());
        assertTrue(result.pairs().isEmpty(),
                "the pairs of a commit that touches more files than the bound are not counted, got: " + result.pairs());
        assertEquals(5, result.files().size());
    }

    @Test
    void projectDirectoryMayBeASubdirectoryOfTheRepository() throws Exception {
        Path repository = createRepository("subdirectory");

        writeFile(repository, "root.txt", "root\n");
        writeFile(repository, "sub/core/A.java", "a\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        Path project = repository.resolve("sub");
        HistoryResult result = toolFor(project, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertEquals(1, result.files().size(),
                "only the files of the project directory are counted, got: " + result.files());
        FileHistory file = fileOf(result, "core/A.java");
        assertEquals(1, file.commitCount(), "the file of the project directory is counted once");
        assertEquals(1, file.linesAdded());
        assertEquals("2024-01-01T10:00:00Z", file.lastChangeDate());
    }

    @Test
    void projectDirectoryThatIsNotARepositoryIsReportedAsAbsent() throws Exception {
        Path directory = tempDir.resolve("not-a-repository");
        Files.createDirectories(directory);

        HistoryResult result = toolFor(directory, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertEquals(HistoryAbsence.NOT_A_REPOSITORY, result.absence());
        assertTrue(result.reason().contains("not a repository"),
                "the result names the case, got: " + result.reason());
        assertTrue(result.files().isEmpty());
        assertTrue(result.pairs().isEmpty());
    }

    @Test
    void machineWithoutGitExecutableIsReportedAsAbsent() throws Exception {
        Path repository = createRepository("without-git");

        writeFile(repository, "a.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        HistoryResult result = toolFor(repository, WINDOW, MAX_FILES_PER_COMMIT, MISSING_EXECUTABLE)
                .readVersionHistory();

        assertEquals(HistoryAbsence.GIT_EXECUTABLE_MISSING, result.absence());
        assertTrue(result.reason().contains("not available"),
                "the result names the case, got: " + result.reason());
        assertTrue(result.files().isEmpty());
        assertTrue(result.pairs().isEmpty());
    }

    @Test
    void shallowCloneIsReportedAsTruncatedAndHoldsTheRecentCommits() throws Exception {
        Path repository = createRepository("truncated-source");

        writeFile(repository, "a.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "a.txt", "2\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-02T10:00:00+00:00", "second");
        writeFile(repository, "b.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-03T10:00:00+00:00", "third");

        Path clone = tempDir.resolve("truncated-clone");
        git(tempDir, Map.of(), "clone", "-q", "--depth", "1",
                "file://" + repository.toAbsolutePath(), clone.toString());

        HistoryResult result = toolFor(clone, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertEquals(HistoryAbsence.HISTORY_TRUNCATED, result.absence());
        assertTrue(result.isTruncated());
        assertTrue(result.reason().contains("truncated"),
                "the result names the case, got: " + result.reason());

        FileHistory recent = fileOf(result, "b.txt");
        assertEquals(1, recent.commitCount());
        assertEquals(1, result.window().countedCommits(),
                "the shallow clone holds the recent commit only");
        assertTrue(result.window().excluded().contains("are missing"),
                "the part of the history that the copy does not hold is named, got: " + result.window().excluded());
    }

    @Test
    void gitExecutableIsConfigurableByProperty() throws Exception {
        Path repository = createRepository("property");

        writeFile(repository, "a.txt", "1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        String previous = System.getProperty(HistoryTool.GIT_EXECUTABLE_PROPERTY);
        System.setProperty(HistoryTool.GIT_EXECUTABLE_PROPERTY, MISSING_EXECUTABLE);
        try {
            HistoryResult result = new HistoryTool(contextFor(repository)).readVersionHistory();

            assertEquals(HistoryAbsence.GIT_EXECUTABLE_MISSING, result.absence());
        } finally {
            if (previous == null) {
                System.clearProperty(HistoryTool.GIT_EXECUTABLE_PROPERTY);
            } else {
                System.setProperty(HistoryTool.GIT_EXECUTABLE_PROPERTY, previous);
            }
        }
    }

    private HistoryTool toolFor(Path projectRoot, int window, int maxFilesPerCommit) {
        return new HistoryTool(contextFor(projectRoot), "git", window, maxFilesPerCommit);
    }

    private HistoryTool toolFor(Path projectRoot, int window, int maxFilesPerCommit, String gitExecutable) {
        return new HistoryTool(contextFor(projectRoot), gitExecutable, window, maxFilesPerCommit);
    }

    private AnalysisContext contextFor(Path projectRoot) {
        return new AnalysisContext(projectRoot, projectRoot, Collections.emptyMap(), null);
    }

    private FileHistory fileOf(HistoryResult result, String path) {
        return result.files().stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the history holds no row for '" + path + "', got: "
                        + result.files()));
    }

    private FilePair pairOf(HistoryResult result, String first, String second) {
        return result.pairs().stream()
                .filter(pair -> pair.firstFile().equals(first) && pair.secondFile().equals(second))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the history holds no pair for '" + first + "' and '"
                        + second + "', got: " + result.pairs()));
    }

    private Path createRepository(String name) throws Exception {
        assumeTrue(gitIsAvailable(), "the git executable is not available on this machine");

        Path repository = tempDir.resolve(name);
        Files.createDirectories(repository);

        git(repository, Map.of(), "init", "-q", ".");
        git(repository, Map.of(), "config", "user.name", "Test Author");
        git(repository, Map.of(), "config", "user.email", "test.author@example.com");
        git(repository, Map.of(), "config", "commit.gpgsign", "false");

        return repository;
    }

    private void commit(Path repository,
                        String authorName,
                        String authorEmail,
                        String date,
                        String message) throws Exception {
        Map<String, String> environment = new HashMap<>();
        environment.put("GIT_AUTHOR_NAME", authorName);
        environment.put("GIT_AUTHOR_EMAIL", authorEmail);
        environment.put("GIT_AUTHOR_DATE", date);
        environment.put("GIT_COMMITTER_NAME", authorName);
        environment.put("GIT_COMMITTER_EMAIL", authorEmail);
        environment.put("GIT_COMMITTER_DATE", date);

        git(repository, environment, "add", "-A");
        git(repository, environment, "commit", "-q", "-m", message);
    }

    private void writeFile(Path repository, String path, String content) throws IOException {
        Path file = repository.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void git(Path directory, Map<String, String> environment, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(arguments));

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().remove("GIT_DIR");
        builder.environment().remove("GIT_WORK_TREE");
        builder.environment().remove("GIT_INDEX_FILE");
        builder.environment().putAll(environment);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "git " + String.join(" ", arguments) + " failed: " + output);
    }

    private boolean gitIsAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
