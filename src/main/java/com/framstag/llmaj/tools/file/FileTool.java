package com.framstag.llmaj.tools.file;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.file.FileHelper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FileTool {
    private static final Logger logger = LoggerFactory.getLogger(FileTool.class);

    private final AnalysisContext context;

    public FileTool(AnalysisContext context) {
        this.context = context;
        logger.info("FileTool initialized.");
    }

    @Tool(name = "GetAllFilesInDir",
            value =
                    """
                            Returns a list of files in the given sub directory.
                            Directories will NOT get recursively visited,
                            """)
    public List<String> getAllFilesInDir(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only") String path) throws IOException {
        logger.info("## GetAllFilesInDir('{}')", path);

        List<String> result = new LinkedList<>();

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return result;
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir == startPath) {
                    return FileVisitResult.CONTINUE;
                }

                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();
                Path name = rootPath.relativize(file);

                result.add(name.toString());

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# =>");
        result.forEach(file -> logger.info("# File: '{}", file));
        logger.info("# done.");

        return result;
    }

    @Tool(name = "GetAllFilesInDirRecursively",
            value =
                    """
                            Returns a list of files in the given sub directory and recursively all of its sub directories.
                            """)
    public List<FilesInDirectory> getAllFilesInDirRecursively(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only") String path) throws IOException {
        logger.info("## GetAllFilesInDirRecursively('{}')", path);

        Map<String,List<String>> result = new HashMap<>();

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return Collections.emptyList();
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                Path directory = rootPath.relativize(file.getParent());
                Path filename = file.getFileName();

                if (!result.containsKey(directory.toString())) {
                    result.put(directory.toString(), new LinkedList<>());
                }

                result.get(directory.toString()).add(filename.toString());

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# =>");
        result.forEach((key, value) -> logger.info("# Directory: '{}' -> '{}'", key, value));
        logger.info("# done.");

        return result.entrySet().stream()
                .map(entry -> new FilesInDirectory(entry.getKey(),entry.getValue()))
                .toList();
    }

    @Tool(name = "GetMatchingFilesInDirRecursively",
            value =
                    """
                            Returns a list of files in the given sub directory and recursively all of its sub directories
                            that match one of the list of the given wildcards.
                            """)
    public List<FilesInDirectory> getMatchingFilesInDirRecursively(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only") String path,
                                                                   @P("A list of wildcards to scan for") List<String> wildcards) throws IOException {
        logger.info("## GetMatchingFilesInDirRecursively('{}', {})", path, wildcards);

        Map<String,Set<String>> result = new HashMap<>();

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return Collections.emptyList();
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                Path directory = rootPath.relativize(file.getParent());
                Path filename = file.getFileName();
                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : wildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:"+wildcard);
                    if (matcher.matches(file.getFileName())) {

                        if (!result.containsKey(directory.toString())) {
                            result.put(directory.toString(), new HashSet<>());
                        }

                        result.get(directory.toString()).add(filename.toString());
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# =>");
        result.forEach((key, value) -> logger.info("# Directory: '{}' -> '{}'", key, value));
        logger.info("# done.");

        return result.entrySet().stream()
                .map(entry -> new FilesInDirectory(entry.getKey(),entry.getValue().stream().toList()))
                .toList();
    }

    @Tool(name = "GetFilesCount",
            value =
                    """
                            Returns the number of files below the given sub directory
                            matching the given glob expression.
                            """)
    public int getFilesCount(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only") String path,
                             @P("The wildcard expression for the filename to match, use '*' to match all files") String wildcard) throws IOException {
        logger.info("## GetFilesCount('{}','{}')", path, wildcard);
        String glob;

        if (wildcard == null || wildcard.isEmpty()) {
            glob = "glob:*";
        } else {
            glob = "glob:" + wildcard;
        }

        AtomicInteger matchingFilesCount= new AtomicInteger(0);

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return matchingFilesCount.get();
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();
                PathMatcher matcher = fs.getPathMatcher(glob);
                Path name = rootPath.relativize(file);

                if (matcher.matches(file.getFileName())) {
                    matchingFilesCount.getAndIncrement();
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# => {}",matchingFilesCount.get());

        return matchingFilesCount.get();
    }

    @Tool(name = "DoesFileExist",
            value =
                    """
                            Returns 'true' if the given filename exists. Else returns 'false'.
                            
                            In case of errors, 'ERROR' is returned.
                            """)
    public String fileExists(@P("The file name to check. The path should be relative to the project root directory") String file) {
        logger.info("## DoesFileExist('{}')", file);

        Path root = context.getProjectRoot();
        Path filePath = Path.of(file);

        String result;

        if (!FileHelper.accessAllowed(root, filePath)) {
            result = "ERROR";
        } else if (filePath.toFile().exists()) {
            result = "true";
        } else {
            result = "false";
        }

        logger.info("## DoesFileExist() => '{}'", result);

        return result;
    }

    @Tool(name = "FileCountPerFileType",
            value =
                    """
                               Returns the number of files per wildcard given
                            """)
    public List<CountPerWildcard> fileCountPerFileType(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only\"") String path,
                                                       @P("A list of wildcards to scan for") List<String> wildcards) throws IOException {
        logger.info("## FileCountPerFileType('{}', '{}')", path, wildcards);

        final Map<String,Integer> result = new HashMap<>();

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return Collections.emptyList();
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : wildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:"+wildcard);
                    Path name = rootPath.relativize(file);

                    if (matcher.matches(file.getFileName())) {
                        result.merge(wildcard, 1, Integer::sum);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# => {}",result);

        return result.entrySet().stream()
                .map(entry -> new CountPerWildcard(entry.getKey(), entry.getValue()))
                .toList();

    }

    @Tool(name = "FileCountPerFileTypeAndDirectory",
            value =
                    """
                               Returns the number of files per wildcard and scanned directory
                            """)
    public List<CountPerWildcardAndDirectory> fileCountPerFileTypeAndDirectory(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only\"") String path,
                                                                               @P("A list of wildcards to scan for") List<String> wildcards) throws IOException {
        logger.info("## FileCountPerFileTypeAndDirectory('{}', '{}')", path, wildcards);

        final Map<DirectoryAndWildcard,Integer> result = new HashMap<>();

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return Collections.emptyList();
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : wildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:"+wildcard);
                    if (matcher.matches(file.getFileName())) {
                        result.merge(new DirectoryAndWildcard(rootPath.relativize(file.getParent()).toString(),
                                wildcard), 1, Integer::sum);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# => {}",result);

        return result.entrySet().stream()
                .map(entry -> new CountPerWildcardAndDirectory(entry.getKey().directory(),
                        entry.getKey().wildcard(),
                        entry.getValue()))
                .toList();

    }

    @Tool(name = "ReadFile",
            value =
                    """
                            Returns the content of the given file.
                            """)
    public String readFile(@P("The file fo which its contents should be returned. The path should be relative to the project root directory") String file) throws IOException {
        logger.info("## ReadFile('{}')", file);

        Path root = context.getProjectRoot();
        Path filePath = Path.of(file);

        if (!FileHelper.accessAllowed(root, filePath)) {
            return "ERROR";
        }

        String fileContent = Files.readString(root.resolve(filePath));

        logger.info("## ReadFile() => '{}'", "<file content>");

        return fileContent;
    }
}
