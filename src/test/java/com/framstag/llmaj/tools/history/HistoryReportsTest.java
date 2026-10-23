package com.framstag.llmaj.tools.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.ToolFactory;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The reports of the counted history are tested over a real repository that is created in a temporary
 * directory, so that the rows of the reports are the ones of a real history.
 */
public class HistoryReportsTest {
    private static final int WINDOW = 100;
    private static final int MAX_FILES_PER_COMMIT = 50;
    private static final String TOOL_NAME = "history_read_version_history";

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void createWorkspace() throws IOException {
        workspace = Files.createDirectories(tempDir.resolve("workspace"));
    }

    @Test
    void theThreeReportsHoldOneRowPerCountedFact() throws Exception {
        Path repository = createRepository("reports");
        createHistory(repository);

        toolFor(repository).readVersionHistory();

        List<List<String>> files = readCsv(workspace.resolve(HistoryReports.FILES_REPORT));
        assertEquals(List.of("File", "CommitCount", "LinesAdded", "LinesRemoved", "FirstChangeDate",
                "LastChangeDate", "Authors"), files.getFirst(), "the file view names its columns");
        assertEquals(List.of(
                List.of("core/A.java", "2", "2", "0", "2024-01-01T10:00:00Z", "2024-01-02T10:00:00Z", "Alice Bob"),
                List.of("core/B.java", "1", "1", "0", "2024-01-01T10:00:00Z", "2024-01-01T10:00:00Z", "Alice"),
                List.of("legacy/Old.java", "1", "1", "0", "2024-01-03T10:00:00Z", "2024-01-03T10:00:00Z", "Alice")),
                files.subList(1, files.size()),
                "the file view holds one row per counted file");

        List<List<String>> modules = readCsv(workspace.resolve(HistoryReports.MODULES_REPORT));
        assertEquals(List.of("Module", "ChangedFileCount", "Churn", "LastChangeDate", "AuthorCount", "Authors"),
                modules.getFirst(), "the module view names its columns");
        assertEquals(List.of(
                List.of("core", "2", "3", "2024-01-02T10:00:00Z", "2", "Alice:2 Bob:1"),
                List.of("legacy", "1", "1", "2024-01-03T10:00:00Z", "1", "Alice:1")),
                modules.subList(1, modules.size()),
                "the module view holds one row per counted module");

        List<List<String>> pairs = readCsv(workspace.resolve(HistoryReports.FILE_PAIRS_REPORT));
        assertEquals(List.of("FirstFile", "SecondFile", "SharedCommits"), pairs.getFirst(),
                "the pair view names its columns");
        assertEquals(List.of(List.of("core/A.java", "core/B.java", "1")), pairs.subList(1, pairs.size()),
                "the pair view holds one row per counted pair");
    }

    @Test
    void theViewsAreThreeFilesAndNotOneMergedFile() throws Exception {
        Path repository = createRepository("views");
        createHistory(repository);

        toolFor(repository).readVersionHistory();

        Path reportDirectory = workspace.resolve(HistoryReports.REPORT_TYPE);
        assertTrue(Files.isDirectory(reportDirectory), "the report directory of the history exists, got: "
                + reportDirectory);
        assertEquals(List.of("FilePairs.csv", "Files.csv", "Modules.csv"), fileNamesOf(reportDirectory),
                "the three views are three files and not one file with unioned columns");

        List<String> filesHeader = readCsv(reportDirectory.resolve("Files.csv")).getFirst();
        List<String> modulesHeader = readCsv(reportDirectory.resolve("Modules.csv")).getFirst();
        List<String> pairsHeader = readCsv(reportDirectory.resolve("FilePairs.csv")).getFirst();

        assertFalse(filesHeader.contains("Module"), "the file view holds no column of the module view");
        assertFalse(filesHeader.contains("SharedCommits"), "the file view holds no column of the pair view");
        assertFalse(modulesHeader.contains("CommitCount"), "the module view holds no column of the file view");
        assertFalse(modulesHeader.contains("FirstFile"), "the module view holds no column of the pair view");
        assertFalse(pairsHeader.contains("Authors"), "the pair view holds no column of the other views");
        assertFalse(pairsHeader.contains("Churn"), "the pair view holds no column of the other views");
    }

    @Test
    void aHistoryThatCannotBeReadHoldsNoReport() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("not-a-repository"));

        HistoryResult result = toolFor(directory).readVersionHistory();

        assertEquals(HistoryAbsence.NOT_A_REPOSITORY, result.absence());
        assertFalse(Files.exists(workspace.resolve(HistoryReports.REPORT_TYPE)),
                "a history that cannot be read holds no counts and writes no report, got: "
                        + workspace.resolve(HistoryReports.REPORT_TYPE));
    }

    @Test
    void theToolIsRegisteredInTheToolFactoryUnderItsOwnName() {
        AnalysisContext context = contextFor(tempDir);

        List<Object> tools = ToolFactory.getToolInstanceList(context);

        assertTrue(tools.stream().anyMatch(tool -> tool instanceof HistoryTool),
                "the tool factory registers the history tool, got: " + tools);

        ToolService toolService = new ToolService();
        toolService.tools(tools);

        ToolSpecification specification = toolService.toolSpecifications().stream()
                .filter(candidate -> candidate.name().equals(TOOL_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the tool specification '" + TOOL_NAME + "' is not there, got: "
                        + toolService.toolSpecifications().stream().map(ToolSpecification::name).toList()));

        assertTrue(specification.parameters() == null,
                "the tool reads the whole project and takes no parameter, got: " + specification.parameters());
    }

    /**
     * The history the reports are written from: two files of the module core that changed together, one of
     * them changed by a second author, and one file of the module legacy.
     */
    private void createHistory(Path repository) throws Exception {
        writeFile(repository, "core/A.java", "a1\n");
        writeFile(repository, "core/B.java", "b1\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-01T10:00:00+00:00", "first");

        writeFile(repository, "core/A.java", "a1\na2\n");
        commit(repository, "Bob", "bob@example.com", "2024-01-02T10:00:00+00:00", "second");

        writeFile(repository, "legacy/Old.java", "old\n");
        commit(repository, "Alice", "alice@example.com", "2024-01-03T10:00:00+00:00", "third");
    }

    private HistoryTool toolFor(Path projectRoot) {
        return new HistoryTool(contextFor(projectRoot), "git", WINDOW, MAX_FILES_PER_COMMIT);
    }

    private AnalysisContext contextFor(Path projectRoot) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode moduleNodes = state.putObject("modules").putArray("modules");

        addModule(moduleNodes, "core", "core");
        addModule(moduleNodes, "legacy", "legacy");

        return new AnalysisContext(projectRoot, workspace, Map.of(), state);
    }

    private void addModule(ArrayNode moduleNodes, String name, String path) {
        ObjectNode moduleNode = moduleNodes.addObject();
        moduleNode.put("name", name);
        moduleNode.put("path", path);
    }

    private List<String> fileNamesOf(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private List<List<String>> readCsv(Path file) throws IOException {
        assertTrue(Files.exists(file), "the report file is not there: " + file);

        List<List<String>> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) {
                continue;
            }
            rows.add(Arrays.stream(line.split(",", -1))
                    .map(value -> value.replace("\"", ""))
                    .toList());
        }

        assertFalse(rows.isEmpty(), "the report file holds no header: " + file);
        return rows;
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
