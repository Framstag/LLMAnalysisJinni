package com.framstag.llmaj.tools.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;

import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * Counts the files of a directory tree per wildcard.
 * <p>
 * A file or a directory that cannot be visited, e.g. because it vanished while the tree is walked,
 * does not stop the walk: the visitor continues with the remaining entries and keeps the counts
 * gathered so far.
 */
class FileCountPerFileTypeVisitor extends SimpleFileVisitor<Path> {
    private static final Logger logger = LoggerFactory.getLogger(FileCountPerFileTypeVisitor.class);

    private final List<String> wildcards;
    private final Map<String, Integer> result;

    FileCountPerFileTypeVisitor(List<String> wildcards,
                                Map<String, Integer> result) {
        this.wildcards = wildcards;
        this.result = result;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
        for (String wildcard : wildcards) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + wildcard);
            if (matcher.matches(file.getFileName())) {
                result.merge(wildcard, 1, Integer::sum);
            }
        }

        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        logger.warn("Cannot visit '{}', it may have vanished during the scan: {}", file, exc.getMessage());

        return CONTINUE;
    }
}
