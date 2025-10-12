package com.framstag.llmaj.tools.file;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.framstag.llmaj.AnalysisContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * We are using our own repository root for tests
 */
public class FileToolTest {

    private AnalysisContext context;
    private FileTool fileTool;

    @BeforeEach
    void initializeContext() {
        context = new AnalysisContext(
            "ArchitectureAnalysis",
            "1.0.0",
            Paths.get("").toAbsolutePath().toString());

        fileTool = new FileTool(context);
    }

    @Test
    void fileExistsInRoot() throws IOException {
        String result = fileTool.fileExists("pom.xml");

        assertEquals("true", result);
    }

    @Test
    void fileExistsInSub() throws IOException {
        String result = fileTool.fileExists("src/main/resources/logback.xml");

        assertEquals("true", result);
    }

    @Test
    void fileExistsRelativeOutside() throws IOException {
        String result = fileTool.fileExists("../bla.txt");

        assertEquals("ERROR", result);
    }

    @Test
    void findFileInRoot() throws IOException {
        List<String> result = fileTool.findMatchingFiles("pom.xml");

        assertEquals(List.of("pom.xml"), result);
    }

    @Test
    void findFileInSubDirWithColonGlobs() throws IOException {
        List<String> result = fileTool.findMatchingFiles("{,**/}Main.java");

        assertEquals(List.of("src/main/java/com/framstag/llmaj/Main.java"), result);
    }

    @Test
    void findFileInRootWithColonGlobs() throws IOException {
        List<String> result = fileTool.findMatchingFiles("{,**/}pom.xml");

        assertEquals(List.of("pom.xml"), result);
    }

    @Test
    void findSBOMInPathWithSimpleGlob() throws IOException {
        List<String> result = fileTool.findMatchingFiles("**/bom.json");

        assertEquals(List.of("target/bom.json"), result);
    }

    @Test
    void findFileInPathWithComplexGlob() throws IOException {
        List<String> result = fileTool.findMatchingFiles("src/**/logback.xml");

        assertEquals(List.of("src/main/resources/logback.xml"), result);
    }

    @Test
    void getOverviewInRootAbsolute() throws IOException {
        List<String> result = fileTool.getFilesOverview("/src/main/Java","*.java",0);

        assertEquals(List.of(), result);
    }

    @Test
    void getOverviewInRootWitheDepth0Match() throws IOException {
        List<String> result = fileTool.getFilesOverview("","*.xml",0);

        assertEquals(List.of("pom.xml"), result);
    }

    @Test
    void getOverviewInSrcWitheDepth1NoMatch() throws IOException {
        List<String> result = fileTool.getFilesOverview("src","*.xml",1);

        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void getOverviewInSrcWitheDepth2() throws IOException {
        List<String> result = fileTool.getFilesOverview("src","*.xml",2);

        assertEquals(List.of("src/main/resources/logback.xml"), result);
    }

    @Test
    void readExistingFile() throws IOException {
        String fileContent = fileTool.readFile("pom.xml");

        assertFalse(fileContent.isEmpty());
    }

    @Test
    void readNonExistingFile() throws IOException {
        assertThrows(NoSuchFileException.class, () -> {
            String fileContent = fileTool.readFile("pommes.xml");
        });
    }

    @Test
    void readFileOutsideRoot() throws IOException {
        String fileContent = fileTool.readFile("../pom.xml");

        assertEquals("ERROR",fileContent);
    }

    @Test
    void countFilesOutsideRoot() throws IOException {
        int matchingFilesCount = fileTool.getFilesCount("..","*.java");

        assertEquals(0,matchingFilesCount);
    }

    @Test
    void countFilesSubdir() throws IOException {
        int matchingFilesCount = fileTool.getFilesCount("src/main/resources","*.xml");

        assertEquals(1,matchingFilesCount);
    }

    @Test
    void countFilesSubdirNoMatch() throws IOException {
        int matchingFilesCount = fileTool.getFilesCount("src/main/resources","*.fun");

        assertEquals(0,matchingFilesCount);
    }

    @Test
    void getFileCountForFileTypeOneTypeWildcard() throws IOException {
        List<CountPerWildcard> result = fileTool.fileCountPerFileType("src/main/resources", List.of("*.xml"));

        assertEquals(List.of(new CountPerWildcard("*.xml",1)),result);
    }

    @Test
    void getFileCountForFileTypeOneTypeFile() throws IOException {
        List<CountPerWildcard> result = fileTool.fileCountPerFileType(".", List.of("pom.xml"));

        assertEquals(List.of(new CountPerWildcard("pom.xml",1)),result);
    }
}
