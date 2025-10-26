package com.framstag.llmaj.file;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class FileHelper {

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
}
