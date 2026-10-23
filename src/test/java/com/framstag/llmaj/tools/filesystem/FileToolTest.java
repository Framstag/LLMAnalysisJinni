package com.framstag.llmaj.tools.filesystem;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.tools.file.FileIOTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * We are using our own repository root for tests
 */
public class FileToolTest {

    private AnalysisContext context;
    private FilesystemTool filesystemTool;
    private FileIOTool fileIOTool;

    @BeforeEach
    void initializeContext() {
        context = new AnalysisContext(
                Paths.get("").toAbsolutePath(),
                Paths.get("").toAbsolutePath(),
                Collections.emptyMap(),
                null);

        filesystemTool = new FilesystemTool(context);
        fileIOTool = new FileIOTool(context);
    }

    @Test
    void GetAllFilesInDirWithoutSubdir() throws IOException {
        List<String> result = filesystemTool.getAllFilesInDir("src/main/java/com/framstag/llmaj");

        assertEquals(2, result.size());
    }

    @Test
    void GetAllFilesInDirWithSubdir() throws IOException {
        List<FilesInDirectory> result = filesystemTool.getAllFilesInDirRecursively("src/test/java/com/framstag/llmaj");

        assertEquals(List.of(
                new FilesInDirectory("src/test/java/com/framstag/llmaj/ci", List.of("ContinuousIntegrationGateTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/cli", List.of("AnalyseCmdLoopTaskTest.java", "AnalyseCmdOptionParsingTest.java", "AnalyseCmdRetryTest.java", "AnalyseCmdTokenUsageTest.java", "StateTokensCmdTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/config", List.of("ConfigAnalysisReportBudgetsTest.java", "ConfigMaxToolRoundTripsTest.java", "ConfigOverridesTest.java", "ConfigRetriesTest.java", "ConfigStorerTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/display", List.of("AnsiTest.java", "DisplayDecisionTest.java", "DisplayManagerTest.java", "LoopWorkerRowTest.java", "ProgressDisplayTest.java", "SimpleOutputTest.java", "TaskRowTest.java", "TokenUsageTrackerTest.java")),

                new FilesInDirectory("src/test/java/com/framstag/llmaj/documentation", List.of("AnalysisReferenceTest.java", "DocumentationTemplateTest.java", "IntraModuleDocumentationTemplateTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/json", List.of("JsonHelperSchemaDescriptionTest.java", "JsonHelperTest.java", "ResponsePayloadParserTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/lc4j", List.of("ChatExecutionLoggingTest.java", "ChatExecutorOutcomeTest.java", "ChatExecutorToolErrorTest.java", "ChatExecutorToolRoundBoundTest.java", "ModelCallFailureTest.java", "SchemaViolationReportTest.java", "TaskChatMemoryTest.java", "TaskStepFailureTest.java", "TaskStepOutcomeTest.java", "TaskStepRetrierTest.java", "ToolArgumentsErrorHandlerTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/logging", List.of("EngineLogRoutingTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/smoke", List.of("JarSmokeIT.java", "JarSmokeProbe.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/state", List.of("StateManagerLoopCursorTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tasks", List.of("AnalysisResponseSchemaTest.java", "HotspotEvaluationAnalysisTest.java", "ModernisationAnchorAnalysisTest.java", "ModernisationStrategyAnalysisTest.java", "ModernisationStrategySchemaTest.java", "SoftwareArchitectureTaskConfigTest.java", "StepTokenUsageTest.java", "TaskDefinitionTest.java", "TaskManagerTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools", List.of("ToolServiceFactoryTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/common", List.of("CsvReportWriterTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/file", List.of("FileIOToolTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/filesystem", List.of("FileToolTest.java", "MatchingFilesInDirToolTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/history", List.of("HistoryReportsTest.java", "HistoryToolTest.java", "ModuleHistoryAggregationTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/java", List.of("AnchorCandidateToolTest.java", "ClassDependencyGraphToolTest.java", "ClassFileParserReferenceTest.java", "ContextMapToolTest.java", "DependencyDiagramStatePersistenceTest.java", "DependencyDiagramToolTest.java", "DomainVocabularyToolTest.java", "DomainVocabularyWorkspaceTest.java", "EntryPointToolTest.java", "ExistingMetricReportCompatibilityTest.java", "GodClassRankingToolTest.java", "HotspotToolTest.java", "JavaToolTest.java", "RawModuleReportFormatTest.java", "RegenerateWorkspaceReportsTest.java", "SeamToolTest.java", "SplitCandidateToolTest.java", "TypeSolverCommentAttributionTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/java/graph", List.of("MaximumSpanningForestTest.java", "MinimumCutTest.java", "PercentilesTest.java", "RealModuleDiagramTest.java", "SeparationLadderTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/java/language", List.of("DomainLexiconTest.java", "IdentifierTokenizerTest.java", "ModuleVocabularyTest.java", "NamingCohesionTest.java", "ProseTextTest.java", "ShippedDomainLexiconTest.java", "TextAlignmentTest.java", "VocabularySharesTest.java", "VocabularyTestData.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/java/language/overview", List.of("ContextMapReportsTest.java", "LexicalOverlapTest.java", "OverviewTestData.java", "ProjectVocabularyTest.java", "QuadrantRowTest.java", "TermIntrusionTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/sbom", List.of("SBOMToolTest.java"))),
                result.stream()
                        .sorted(Comparator.comparing(FilesInDirectory::directory))
                        .map(entry -> new FilesInDirectory(entry.directory(),
                                entry.files().stream().sorted().toList()))
                        .toList());
    }

    @Test
    void fileExistsInRoot() {
        String result = filesystemTool.fileExists("pom.xml");

        assertEquals("true", result);
    }

    @Test
    void fileExistsInSub() {
        String result = filesystemTool.fileExists("src/main/resources/logback.xml");

        assertEquals("true", result);
    }

    @Test
    void fileExistsRelativeOutside() {
        String result = filesystemTool.fileExists("../bla.txt");

        assertEquals("ERROR", result);
    }

    @Test
    void findMatchingFileInRootDir() throws IOException {
        List<FilesInDirectory> result = filesystemTool.getMatchingFilesInDirRecursively("",List.of("pom.xml"),Optional.of(true));

        assertEquals(1, result.size());
        assertEquals("", result.getFirst().directory());
        assertEquals("pom.xml", result.getFirst().files().getFirst());
    }

    @Test
    void findMatchingFileInSubDir() throws IOException {
        List<FilesInDirectory> result = filesystemTool.getMatchingFilesInDirRecursively("",List.of("Main.java"),Optional.of(true));

        assertEquals(1, result.size());
        assertEquals("src/main/java/com/framstag/llmaj", result.getFirst().directory());
        assertEquals("Main.java", result.getFirst().files().getFirst());
    }

    @Test
    void findExactlyMatchingFileInSubDir() throws IOException {
        List<FilesInDirectory> result = filesystemTool.getMatchingFilesInDirRecursively("src",List.of("logback.xml"),Optional.of(true));

        assertEquals(1, result.size());
        assertEquals("src/main/resources", result.getFirst().directory());
        assertEquals("logback.xml", result.getFirst().files().getFirst());
    }

    @Test
    void readExistingFile() throws IOException {
        String fileContent = fileIOTool.readFile("pom.xml");

        assertFalse(fileContent.isEmpty());
    }

    @Test
    void readNonExistingFile() {
        String fileContent = fileIOTool.readFile("pommes.xml");

        assertTrue(fileContent.contains("pommes.xml"),
                "the tool result must name the file it could not read, got: " + fileContent);
        assertTrue(fileContent.contains("cannot be read"),
                "the tool result must name the condition, got: " + fileContent);
    }

    @Test
    void readFileOutsideRoot() throws IOException {
        String fileContent = fileIOTool.readFile("../pom.xml");

        assertTrue(fileContent.contains("../pom.xml"),
                "the tool result must name the path it refused, got: " + fileContent);
        assertTrue(fileContent.contains("not inside the project root"),
                "the tool result must name the condition, got: " + fileContent);
    }

    @Test
    void countFilesOutsideRoot() throws IOException {
        int matchingFilesCount = filesystemTool.getFilesCount("..","*.java");

        assertEquals(0,matchingFilesCount);
    }

    @Test
    void countFilesSubdir() throws IOException {
        int matchingFilesCount = filesystemTool.getFilesCount("src/main/resources","*.xml");

        assertEquals(1,matchingFilesCount);
    }

    @Test
    void countFilesSubdirNoMatch() throws IOException {
        int matchingFilesCount = filesystemTool.getFilesCount("src/main/resources","*.fun");

        assertEquals(0,matchingFilesCount);
    }

    @Test
    void getFileCountForFileTypeOneTypeWildcard() throws IOException {
        List<CountPerWildcard> result = filesystemTool.fileCountPerFileType("src/main/resources", List.of("*.xml"));

        assertEquals(List.of(new CountPerWildcard("*.xml",1)),result);
    }

    @Test
    void getFileCountForFileTypeOneTypeFile() throws IOException {
        List<CountPerWildcard> result = filesystemTool.fileCountPerFileType(".", List.of("pom.xml"));

        assertEquals(List.of(new CountPerWildcard("pom.xml",1)),result);
    }

    @Test
    void theFileCountPerFileTypeVisitorContinuesWhenAFileCannotBeVisited() {
        Map<String,Integer> counts = new HashMap<>();
        FileCountPerFileTypeVisitor visitor =
                new FileCountPerFileTypeVisitor(List.of("*.java"), counts);

        FileVisitResult result = visitor.visitFileFailed(Paths.get("vanished.java"), new NoSuchFileException("vanished.java"));

        assertEquals(FileVisitResult.CONTINUE, result);
        assertTrue(counts.isEmpty(), "a file that cannot be visited must not be counted");
    }

    @Test
    void getFileCountPerFileTypeCountsAFileAndASubdirectory(@TempDir Path projectRoot) throws IOException {
        Files.createFile(projectRoot.resolve("top.txt"));
        Files.createDirectories(projectRoot.resolve("sub"));
        Files.createFile(projectRoot.resolve("sub").resolve("nested.txt"));

        AnalysisContext tempContext = new AnalysisContext(projectRoot, projectRoot, Collections.emptyMap(), null);
        FilesystemTool tool = new FilesystemTool(tempContext);

        List<CountPerWildcard> result = tool.fileCountPerFileType("", List.of("*.txt"));

        assertEquals(List.of(new CountPerWildcard("*.txt", 2)), result);
    }

    @Test
    void getFileCountPerFileTypeSurvivesFilesThatChangeDuringTheScan(@TempDir Path projectRoot) throws Exception {
        Path scanDir = Files.createDirectories(projectRoot.resolve("scan"));
        Files.createFile(scanDir.resolve("stable.txt"));

        AnalysisContext tempContext = new AnalysisContext(projectRoot, projectRoot, Collections.emptyMap(), null);
        FilesystemTool tool = new FilesystemTool(tempContext);

        AtomicBoolean mutating = new AtomicBoolean(true);
        Thread mutator = new Thread(() -> {
            int counter = 0;
            while (mutating.get()) {
                Path transientFile = scanDir.resolve("transient" + (counter++ % 8) + ".txt");
                try {
                    Files.writeString(transientFile, "content");
                    Files.deleteIfExists(transientFile);
                } catch (IOException e) {
                    // the file vanished again, which is exactly the situation under test
                }
            }
        });
        mutator.start();

        try {
            for (int scan = 0; scan < 50; scan++) {
                assertDoesNotThrow(() -> tool.fileCountPerFileType("scan", List.of("*.txt")),
                        "the scan must survive files that vanish while it runs");
            }
        } finally {
            mutating.set(false);
            mutator.join();
        }
    }

    @Test
    void getFileCountPerFileTypeAndDirectoryWildcard() throws IOException {
        List<CountPerWildcardAndDirectory> result = filesystemTool.fileCountPerFileTypeAndDirectory("src/main/resources", List.of("*.xml"));

        assertEquals(List.of(new CountPerWildcardAndDirectory("src/main/resources","*.xml",1)),result);

    }

    @Test
    void getFileCountPerFileTypeAndDirectoryFile() throws IOException {
        List<CountPerWildcardAndDirectory> result = filesystemTool.fileCountPerFileTypeAndDirectory(".", List.of("pom.xml"));

        assertEquals(List.of(new CountPerWildcardAndDirectory("","pom.xml",1)),result);

    }

    @Test
    void theFileCountVisitorContinuesWhenAFileCannotBeVisited() {
        Map<DirectoryAndWildcard,Integer> counts = new HashMap<>();
        FileCountPerFileTypeAndDirectoryVisitor visitor =
                new FileCountPerFileTypeAndDirectoryVisitor(Paths.get("").toAbsolutePath(), List.of("*.java"), counts);

        FileVisitResult result = visitor.visitFileFailed(Paths.get("vanished.java"), new NoSuchFileException("vanished.java"));

        assertEquals(FileVisitResult.CONTINUE, result);
        assertTrue(counts.isEmpty(), "a file that cannot be visited must not be counted");
    }

    @Test
    void getFileCountPerFileTypeAndDirectoryCountsAFileAndASubdirectory(@TempDir Path projectRoot) throws IOException {
        Files.createFile(projectRoot.resolve("top.txt"));
        Files.createDirectories(projectRoot.resolve("sub"));
        Files.createFile(projectRoot.resolve("sub").resolve("nested.txt"));

        AnalysisContext tempContext = new AnalysisContext(projectRoot, projectRoot, Collections.emptyMap(), null);
        FilesystemTool tool = new FilesystemTool(tempContext);

        List<CountPerWildcardAndDirectory> result = tool.fileCountPerFileTypeAndDirectory("", List.of("*.txt"));

        assertEquals(2, result.size());
        assertTrue(result.contains(new CountPerWildcardAndDirectory("", "*.txt", 1)),
                "the file in the root directory must be counted, got: " + result);
        assertTrue(result.contains(new CountPerWildcardAndDirectory("sub", "*.txt", 1)),
                "the file in the subdirectory must be counted, got: " + result);
    }

    @Test
    void getFileCountPerFileTypeAndDirectorySurvivesFilesThatChangeDuringTheScan(@TempDir Path projectRoot) throws Exception {
        Path scanDir = Files.createDirectories(projectRoot.resolve("scan"));
        Files.createFile(scanDir.resolve("stable.txt"));

        AnalysisContext tempContext = new AnalysisContext(projectRoot, projectRoot, Collections.emptyMap(), null);
        FilesystemTool tool = new FilesystemTool(tempContext);

        AtomicBoolean mutating = new AtomicBoolean(true);
        Thread mutator = new Thread(() -> {
            int counter = 0;
            while (mutating.get()) {
                Path transientFile = scanDir.resolve("transient" + (counter++ % 8) + ".txt");
                try {
                    Files.writeString(transientFile, "content");
                    Files.deleteIfExists(transientFile);
                } catch (IOException e) {
                    // the file vanished again, which is exactly the situation under test
                }
            }
        });
        mutator.start();

        try {
            for (int scan = 0; scan < 50; scan++) {
                assertDoesNotThrow(() -> tool.fileCountPerFileTypeAndDirectory("scan", List.of("*.txt")),
                        "the scan must survive files that vanish while it runs");
            }
        } finally {
            mutating.set(false);
            mutator.join();
        }
    }
}
