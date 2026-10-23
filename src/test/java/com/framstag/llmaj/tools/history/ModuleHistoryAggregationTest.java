package com.framstag.llmaj.tools.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The aggregation of the counted history per build module and the report of an absent history are tested
 * over real repositories that are created in a temporary directory, so that the counts, the dates and the
 * authors are the ones of a real history and not the ones of a recorded output.
 */
public class ModuleHistoryAggregationTest {
    private static final int WINDOW = 100;
    private static final int MAX_FILES_PER_COMMIT = 50;
    private static final String MISSING_EXECUTABLE = "/does/not/exist/git";

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    @Test
    void moduleHistoryIsAggregatedFromTheFilesOfItsDirectories() throws Exception {
        Path repository = createRepository("modules");

        writeFile(repository, "core/A.java", "a\n");
        writeFile(repository, "core/B.java", "b\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "core/C.java", "c1\nc2\n");
        commit(repository, "Bob", "bob@example.com", "2024-01-02T10:00:00+00:00", "second");
        writeFile(repository, "core/A.java", "a changed\n");
        writeFile(repository, "core/D.java", "d\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-03T10:00:00+00:00", "third");

        HistoryResult result = toolFor(repository, Map.of("core", "core")).readVersionHistory();

        assertEquals(4, result.files().size(), "the history holds a row per changed file");

        ModuleHistory core = moduleOf(result, "core");
        assertEquals(4, core.changedFileCount(), "the module holds the four changed files of its directory");
        assertEquals(6, core.linesAdded());
        assertEquals(1, core.linesRemoved());
        assertEquals(7, core.churn(), "the churn of the module is the added and the removed lines");
        assertEquals("2024-01-03T10:00:00Z", core.lastChangeDate());
        assertEquals(2, core.authorCount(), "the module was changed by two authors");
    }

    @Test
    void authorWithMostCommitsIsNamedWithItsCount() throws Exception {
        Path repository = createRepository("authorship");

        for (int commitNumber = 1; commitNumber <= 8; commitNumber++) {
            writeFile(repository, "legacy/Old.java", "line" + commitNumber + "\n");
            commit(repository, "Alice", "alice@example.com",
                    "2024-01-0" + (commitNumber % 9 + 1) + "T10:00:00+00:00", "change " + commitNumber);
        }
        writeFile(repository, "legacy/Old.java", "line9\n");
        commit(repository, "Bob", "bob@example.com", "2024-02-01T10:00:00+00:00", "the other author");

        HistoryResult result = toolFor(repository, Map.of("legacy", "legacy")).readVersionHistory();

        ModuleHistory legacy = moduleOf(result, "legacy");
        assertEquals(1, legacy.changedFileCount(), "the module holds the one file that changed");

        int commitsOfTheAuthors = legacy.authors().stream()
                .mapToInt(ModuleAuthor::commitCount)
                .sum();
        assertEquals(9, commitsOfTheAuthors,
                "the module was changed in nine commits, the commits of its authors add up to them");

        ModuleAuthor alice = authorOf(legacy, "Alice");
        assertEquals(8, alice.commitCount(), "the author of most of the commits is named with its count");
        assertEquals(1, authorOf(legacy, "Bob").commitCount());
        assertEquals(2, legacy.authorCount());
    }

    @Test
    void fileOutsideEveryModulePathBelongsToNoModule() throws Exception {
        Path repository = createRepository("outside");

        writeFile(repository, "core/X.java", "x\n");
        writeFile(repository, "docs/readme.md", "readme\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        HistoryResult result = toolFor(repository, Map.of("core", "core")).readVersionHistory();

        assertEquals(2, result.files().size(), "the history holds a row per changed file");

        ModuleHistory core = moduleOf(result, "core");
        assertEquals(1, core.changedFileCount(),
                "only the file under the path of the module counts for the module");

        long countedFiles = result.modules().stream()
                .mapToLong(ModuleHistory::changedFileCount)
                .sum();
        assertEquals(1, countedFiles,
                "the file outside every module path belongs to no module, so it is counted for no module");
    }

    @Test
    void modulePathsStayRightWhenTheProjectDirectoryIsASubdirectoryOfTheRepository() throws Exception {
        Path repository = createRepository("subdirectory-modules");

        writeFile(repository, "sub/core/A.java", "a\n");
        writeFile(repository, "sub/other/B.java", "b\n");
        writeFile(repository, "outside/C.java", "c\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        Path project = repository.resolve("sub");
        HistoryResult result = toolFor(project, Map.of("core", "core")).readVersionHistory();

        assertEquals(2, result.files().size(),
                "only the files of the project directory are counted, got: " + result.files());
        ModuleHistory core = moduleOf(result, "core");
        assertEquals(1, core.changedFileCount(),
                "the file of the module of the project directory counts for the module");
    }

    @Test
    void projectThatIsNotARepositoryIsReportedAsAbsentWithoutCounts() throws Exception {
        Path directory = tempDir.resolve("not-a-repository");
        Files.createDirectories(directory);

        HistoryResult result = toolFor(directory, Map.of("core", "core")).readVersionHistory();

        assertEquals(HistoryAbsence.NOT_A_REPOSITORY, result.absence());
        assertTrue(result.reason().contains("not a repository"),
                "the result names the case, got: " + result.reason());
        assertTrue(result.files().isEmpty(), "an absent history holds no counts");
        assertTrue(result.pairs().isEmpty(), "an absent history holds no counts");
        assertTrue(result.modules().isEmpty(), "an absent history holds no counts");
    }

    @Test
    void machineWithoutGitExecutableIsReportedAsAbsentWithoutCounts() throws Exception {
        Path repository = createRepository("without-git");

        writeFile(repository, "core/A.java", "a\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        HistoryResult result = new HistoryTool(contextFor(repository, Map.of("core", "core")),
                MISSING_EXECUTABLE, WINDOW, MAX_FILES_PER_COMMIT).readVersionHistory();

        assertEquals(HistoryAbsence.GIT_EXECUTABLE_MISSING, result.absence());
        assertTrue(result.reason().contains("not available"),
                "the result names the case, got: " + result.reason());
        assertTrue(result.files().isEmpty(), "an absent history holds no counts");
        assertTrue(result.pairs().isEmpty(), "an absent history holds no counts");
        assertTrue(result.modules().isEmpty(), "an absent history holds no counts");
    }

    @Test
    void truncatedHistoryIsReportedAsAbsentForTheMissingOlderPart() throws Exception {
        Path repository = createRepository("truncated-source");

        writeFile(repository, "core/A.java", "a\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");
        writeFile(repository, "core/A.java", "a changed\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-02T10:00:00+00:00", "second");
        writeFile(repository, "core/B.java", "b\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-03T10:00:00+00:00", "third");

        Path clone = tempDir.resolve("truncated-clone");
        git(tempDir, Map.of(), "clone", "-q", "--depth", "1",
                "file://" + repository.toAbsolutePath(), clone.toString());

        HistoryResult result = toolFor(clone, Map.of("core", "core")).readVersionHistory();

        assertEquals(HistoryAbsence.HISTORY_TRUNCATED, result.absence());
        assertTrue(result.reason().contains("truncated"),
                "the result names the case, got: " + result.reason());
        assertTrue(result.window().excluded().contains("are missing"),
                "the part of the history the copy does not hold is named, got: " + result.window().excluded());
        assertEquals(1, result.window().countedCommits(),
                "the counts of the missing older part of the history are not held, the recent commit is");
    }

    private HistoryTool toolFor(Path projectRoot, Map<String, String> modules) {
        return new HistoryTool(contextFor(projectRoot, modules), "git", WINDOW, MAX_FILES_PER_COMMIT);
    }

    private AnalysisContext contextFor(Path projectRoot, Map<String, String> modules) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode moduleNodes = state.putObject("modules").putArray("modules");

        for (Map.Entry<String, String> module : modules.entrySet()) {
            ObjectNode moduleNode = moduleNodes.addObject();
            moduleNode.put("name", module.getKey());
            moduleNode.put("path", module.getValue());
        }

        return new AnalysisContext(projectRoot, projectRoot, Map.of(), state);
    }

    private ModuleHistory moduleOf(HistoryResult result, String name) {
        return result.modules().stream()
                .filter(module -> module.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the history holds no module named '" + name + "', got: "
                        + result.modules()));
    }

    private ModuleAuthor authorOf(ModuleHistory module, String name) {
        ModuleAuthor author = module.authors().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElse(null);
        assertNotNull(author, "the module holds no author named '" + name + "', got: " + module.authors());
        return author;
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
