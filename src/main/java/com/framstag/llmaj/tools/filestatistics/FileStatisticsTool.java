package com.framstag.llmaj.tools.filestatistics;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.file.FileHelper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileStatisticsTool {
    private static final Logger logger = LoggerFactory.getLogger(FileStatisticsTool.class);

    private final AnalysisContext context;

    public FileStatisticsTool(AnalysisContext context) {
        this.context = context;
        logger.info("FileStatisticsTool initialized.");

    }

    @Tool(name = "GetStatisticsForMatchingFilesInDirRecursively",
            value =
                    """
                            Returns file statistics for each passed wildcard.
                            """)
    public List<FileStatistics> getStatisticsForMatchingFilesInDirRecursively(@P("The relative path in the project to scan, use '' for the root directory. Make sure to pass *relative* paths only") String path,
                                                                   @P("A list of wildcards to scan for") List<String> wildcards) throws IOException {
        logger.info("## GetStatisticsForMatchingFilesInDirRecursively('{}', {})", path, wildcards);

        Map<String, FileStatistics> result = new HashMap<>();

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(path);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", path,rootPath,relativePath);
            return Collections.emptyList();
        }

        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) throws IOException {

                Path directory = rootPath.relativize(file.getParent());
                Path filename = file.getFileName();
                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : wildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:"+wildcard);
                    if (matcher.matches(file.getFileName())) {
                        if (!result.containsKey(wildcard)) {
                            result.put(wildcard, new FileStatistics(wildcard));
                        }

                        result.get(wildcard).addAFile(FileHelper.getLineCount(file));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, matcherVisitor);

        logger.info("# =>");
        result.forEach((key, value) -> logger.info("# Directory: '{}' -> '{}'", key, value));
        logger.info("# done.");

        return result.values().stream()
                .toList();
    }

}
