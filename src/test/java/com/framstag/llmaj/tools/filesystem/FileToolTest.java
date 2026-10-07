package com.framstag.llmaj.tools.filesystem;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.tools.file.FileIOTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
                new FilesInDirectory("src/test/java/com/framstag/llmaj/cli", List.of("AnalyseCmdLoopTaskTest.java", "AnalyseCmdOptionParsingTest.java", "AnalyseCmdRetryTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/config", List.of("ConfigAnalysisReportBudgetsTest.java", "ConfigMaxToolRoundTripsTest.java", "ConfigOverridesTest.java", "ConfigRetriesTest.java", "ConfigStorerTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/display", List.of("DisplayDecisionTest.java", "DisplayManagerTest.java", "LoopWorkerRowTest.java", "ProgressDisplayTest.java", "SimpleOutputTest.java", "TaskRowTest.java")),

                new FilesInDirectory("src/test/java/com/framstag/llmaj/documentation", List.of("DocumentationTemplateTest.java", "IntraModuleDocumentationTemplateTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/json", List.of("JsonHelperSchemaDescriptionTest.java", "JsonHelperTest.java", "ResponsePayloadParserTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/lc4j", List.of("ChatExecutionLoggingTest.java", "ChatExecutorOutcomeTest.java", "ChatExecutorToolErrorTest.java", "ChatExecutorToolRoundBoundTest.java", "SchemaViolationReportTest.java", "TaskStepFailureTest.java", "TaskStepOutcomeTest.java", "TaskStepRetrierTest.java", "ToolArgumentsErrorHandlerTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/logging", List.of("EngineLogRoutingTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/smoke", List.of("JarSmokeIT.java", "JarSmokeProbe.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/state", List.of("StateManagerLoopCursorTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tasks", List.of("AnalysisResponseSchemaTest.java", "SoftwareArchitectureTaskConfigTest.java", "TaskDefinitionTest.java", "TaskManagerTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools", List.of("ToolServiceFactoryTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/common", List.of("CsvReportWriterTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/file", List.of("FileIOToolTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/filesystem", List.of("FileToolTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/java", List.of("ClassDependencyGraphToolTest.java", "ClassFileParserReferenceTest.java", "DependencyDiagramStatePersistenceTest.java", "DependencyDiagramToolTest.java", "ExistingMetricReportCompatibilityTest.java", "GodClassRankingToolTest.java", "JavaToolTest.java", "RawModuleReportFormatTest.java", "RegenerateWorkspaceReportsTest.java", "SplitCandidateToolTest.java")),
                new FilesInDirectory("src/test/java/com/framstag/llmaj/tools/java/graph", List.of("MaximumSpanningForestTest.java", "MinimumCutTest.java", "PercentilesTest.java", "RealModuleDiagramTest.java", "SeparationLadderTest.java")),
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
        List<FilesInDirectory> result = filesystemTool.getMatchingFilesInDirRecursively("",List.of("pom.xml"),true);

        assertEquals(1, result.size());
        assertEquals("", result.getFirst().directory());
        assertEquals("pom.xml", result.getFirst().files().getFirst());
    }

    @Test
    void findMatchingFileInSubDir() throws IOException {
        List<FilesInDirectory> result = filesystemTool.getMatchingFilesInDirRecursively("",List.of("Main.java"),true);

        assertEquals(1, result.size());
        assertEquals("src/main/java/com/framstag/llmaj", result.getFirst().directory());
        assertEquals("Main.java", result.getFirst().files().getFirst());
    }

    @Test
    void findExactlyMatchingFileInSubDir() throws IOException {
        List<FilesInDirectory> result = filesystemTool.getMatchingFilesInDirRecursively("src",List.of("logback.xml"),true);

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
    void getFileCountPerFileTypeAndDirectoryWildcard() throws IOException {
        List<CountPerWildcardAndDirectory> result = filesystemTool.fileCountPerFileTypeAndDirectory("src/main/resources", List.of("*.xml"));

        assertEquals(List.of(new CountPerWildcardAndDirectory("src/main/resources","*.xml",1)),result);

    }

    @Test
    void getFileCountPerFileTypeAndDirectoryFile() throws IOException {
        List<CountPerWildcardAndDirectory> result = filesystemTool.fileCountPerFileTypeAndDirectory(".", List.of("pom.xml"));

        assertEquals(List.of(new CountPerWildcardAndDirectory("","pom.xml",1)),result);

    }
}
