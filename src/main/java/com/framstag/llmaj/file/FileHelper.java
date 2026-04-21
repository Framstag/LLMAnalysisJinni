package com.framstag.llmaj.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;

public class FileHelper {
    private static final Logger logger = LoggerFactory.getLogger(FileHelper.class);

    /**
     * Is th given directory "below" the root irectory and is it thus allowed to access it?
     *
     * @param root Root directory
     * @param path Other directory, that must be "below" the root directory for access to be allowed.
     * @return true, if access is allowed, else false
     */
    public static boolean accessAllowed(Path root, Path path) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absoluteFilePath = root.resolve(path).toAbsolutePath().normalize();

        return absoluteFilePath.startsWith(absoluteRoot);
    }

    /**
     * Count the number of lines in the file and return them.
     *
     * @param file file name
     * @return Number of lines
     * @throws IOException in case of errors
     */
    public static int getLineCount(Path file) throws IOException {
        int lines = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            while (reader.readLine() != null) {
                lines++;
            }
        }

        return lines;
    }

    public static boolean isDirectoryAndCanBeWrittenTo(Path path,
                                                       String lowerName,
                                                       String upperName) {
        if (!path.toFile().exists()) {
            logger.error("{} directory '{}', does not exist, please create it!",
                    upperName,
                    path);
            return false;
        }

        if (!path.toFile().isDirectory()) {
            logger.error("{} directory '{}' is not a directory!",
                    upperName,
                    path);
            return false;
        }

        if (!path.toFile().canWrite()) {
            logger.error("Cannot write to {} directory '{}', fix access rights!",
                    lowerName,
                    path);
            return false;
        }

        return true;
    }

    public static boolean isDirectoryAndCanBeReadFrom(Path path,
                                                      String lowerName,
                                                      String upperName) {
        if (!path.toFile().exists()) {
            logger.error("{} directory '{}', does not exist, please create it!",
                    upperName,
                    path);
            return false;
        }

        if (!path.toFile().isDirectory()) {
            logger.error("{} directory '{}' is not a directory!",
                    upperName,
                    path);
            return false;
        }

        if (!path.toFile().canRead()) {
            logger.error("Cannot read from {} directory '{}', fix access rights!",
                    lowerName,
                    path);
            return false;
        }

        return true;
    }

    public static List<Path> getAllMatchingFilesInDirectoryRecursively(List<String> wildcards, Path startPath) throws IOException {
        List<Path> files = new LinkedList<>();

        FileVisitor<Path> srcMatcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : wildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:" + wildcard);
                    if (matcher.matches(file.getFileName())) {
                        files.add(file);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, srcMatcherVisitor);

        return files;
    }
}
