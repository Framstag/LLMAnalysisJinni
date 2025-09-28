package com.framstag.llmaj.tools.file;

import com.framstag.llmaj.AnalysisContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;

public class FileTool {
    private static final Logger logger = LoggerFactory.getLogger(FileTool.class);

    private final AnalysisContext context;

    private boolean allowedAccess(Path root, Path path) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absoluteFilePath = root.resolve(path).toAbsolutePath().normalize();

        return absoluteFilePath.startsWith(absoluteRoot);
    }

    /**
     * Returns the depth of subPath relative to rootPath.
     * For example:
     * rootPath = /home/user
     * subPath  = /home/user/projects/java/app
     * Result   = 3 (projects -> java -> app)
     */
    private int getRelativeDepth(Path rootPath, Path subPath) {
        // Ensure both paths are absolute and normalized
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path normalizedSub = rootPath.resolve(subPath).toAbsolutePath().normalize();

        if (!normalizedSub.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("subPath must be inside rootPath");
        }

        return normalizedSub.getNameCount()- normalizedRoot.getNameCount();
    }

    public FileTool(AnalysisContext context) {
        this.context = context;
    }

    @Tool(name = "FindMatchingFiles",
            value =
                    """
                            Return all files in the project directory that match the given wildcard
                            """)
    public List<String> findMatchingFiles(@P("The wildcard expression, using '*' as placeholder") String wildcard) throws IOException {
        logger.info("## FindMatchingFiles('{}')", wildcard);
        String glob;

        if (wildcard == null || wildcard.isEmpty()) {
            glob = "glob:*";
        } else {
            glob = "glob:" + wildcard;
        }

        Path rootPath = Path.of(context.getProjectRoot());

        List<String> result = new LinkedList<>();

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
                FileSystem fs = FileSystems.getDefault();
                PathMatcher matcher = fs.getPathMatcher(glob);
                Path name = rootPath.relativize(file);
                if (matcher.matches(name)) {
                    result.add(name.toString());
                }
                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(rootPath, matcherVisitor);

        logger.info("# =>");
        result.forEach(file -> logger.info("# File: '{}", file));
        logger.info("# done.");

        return result;
    }

    @Tool(name = "GetFilesOverview",
            value =
                    """
                            Returns recursively a list of files in the given sub directory.
                            Files must match the given glob expression.
                            Directories will be descended up to the given depth.
                            """)
    public List<String> getFilesOverview(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only") String path,
                                         @P("The wildcard expression for the filename to match, use '*' to match all files") String wildcard,
                                         @P("Depth of directory hierarchy to show, should be greater than 0") int depth) throws IOException {
        logger.info("## GetFilesOverview('{}','{}',{})", path, wildcard, depth);
        String glob;

        if (wildcard == null || wildcard.isEmpty()) {
            glob = "glob:*";
        } else {
            glob = "glob:" + wildcard;
        }

        List<String> result = new LinkedList<>();

        Path rootPath = Path.of(context.getProjectRoot());
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!allowedAccess(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return result;
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                int relativeDepth = getRelativeDepth(startPath, dir);
                if ( relativeDepth > depth) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();
                PathMatcher matcher = fs.getPathMatcher(glob);
                Path name = rootPath.relativize(file);

                if (matcher.matches(file.getFileName())) {
                    result.add(name.toString());
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# =>");
        result.forEach(file -> logger.info("# File: '{}", file));
        logger.info("# done.");

        return result;
    }

    @Tool(name = "DoesFileExist",
            value =
                    """
                            Returns 'true' if the given filename exists. Else returns 'false'.
                            
                            In case of errors, 'ERROR' is returned.
                            """)
    public String fileExists(@P("The file name to check. The path should be relative to the project root directory") String file) {
        logger.info("## DoesFileExist('{}')", file);

        Path root = Path.of(context.getProjectRoot());
        Path filePath = Path.of(file);

        String result;

        if (!allowedAccess(root, filePath)) {
            result = "ERROR";
        } else if (filePath.toFile().exists()) {
            result = "true";
        } else {
            result = "false";
        }

        logger.info("## DoesFileExist() => '{}'", result);

        return result;
    }

    @Tool(name = "ReadFile",
            value =
                    """
                            Returns the content of the given file.
                            """)
    public String readFile(@P("The file fo which its contents should be returned. The path should be relative to the project root directory") String file) throws IOException {
        logger.info("## ReadFile('{}')", file);

        Path root = Path.of(context.getProjectRoot());
        Path filePath = Path.of(file);

        if (!allowedAccess(root, filePath)) {
            return "ERROR";
        }

        String fileContent = Files.readString(root.resolve(filePath));

        logger.info("## ReadFile() => '{}'", "<file content>");

        return fileContent;
    }
}
