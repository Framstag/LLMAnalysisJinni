package com.framstag.llmaj.tools.file;

import com.framstag.llmaj.AnalysisContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;

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
                Paths.get("").toAbsolutePath(),
                Paths.get("").toAbsolutePath());

        fileTool = new FileTool(context);
    }

    @Test
    void GetAllFilesInDirWithoutSubdir() throws IOException {
        List<String> result = fileTool.getAllFilesInDir("src/main/java/com/framstag/llmaj");

        assertEquals(4, result.size());
    }

    @Test
    void GetAllFilesInDirWithSubdir() throws IOException {
        List<FilesInDirectory> result = fileTool.getAllFilesInDirRecursively("src/test/java/com/framstag/llmaj");

        assertEquals(2, result.size());
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
    void findMatchingFileInRootDir() throws IOException {
        List<FilesInDirectory> result = fileTool.getMatchingFilesInDirRecursively("",List.of("pom.xml"));

        assertEquals(1, result.size());
        assertEquals("", result.getFirst().directory());
        assertEquals("pom.xml", result.getFirst().files().getFirst());
    }

    @Test
    void findMatchingFileInSubDir() throws IOException {
        List<FilesInDirectory> result = fileTool.getMatchingFilesInDirRecursively("",List.of("Main.java"));

        assertEquals(1, result.size());
        assertEquals("src/main/java/com/framstag/llmaj", result.getFirst().directory());
        assertEquals("Main.java", result.getFirst().files().getFirst());
    }

    @Test
    void findExactlyMatchingFileInSubDir() throws IOException {
        List<FilesInDirectory> result = fileTool.getMatchingFilesInDirRecursively("src",List.of("logback.xml"));

        assertEquals(1, result.size());
        assertEquals("src/main/resources", result.getFirst().directory());
        assertEquals("logback.xml", result.getFirst().files().getFirst());
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

    @Test
    void getFileCountPerFileTypeAndDirectoryWildcard() throws IOException {
        List<CountPerWildcardAndDirectory> result = fileTool.fileCountPerFileTypeAndDirectory("src/main/resources", List.of("*.xml"));

        assertEquals(List.of(new CountPerWildcardAndDirectory("src/main/resources","*.xml",1)),result);

    }

    @Test
    void getFileCountPerFileTypeAndDirectoryFile() throws IOException {
        List<CountPerWildcardAndDirectory> result = fileTool.fileCountPerFileTypeAndDirectory(".", List.of("pom.xml"));

        assertEquals(List.of(new CountPerWildcardAndDirectory("","pom.xml",1)),result);

    }
}
