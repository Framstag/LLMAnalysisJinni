package com.framstag.llmaj.tools.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the git executable of the machine over the project directory.
 *
 * <p>The git executable is not part of the build, so it is not on every machine. A run that cannot start the
 * process is reported as a missing executable instead of an exception, because a machine without git holds no
 * history to count, and that is a fact about the machine rather than a failure of the run.
 */
class GitRunner {
    private static final Logger logger = LoggerFactory.getLogger(GitRunner.class);

    private final String executable;
    private final Path workingDirectory;

    GitRunner(String executable, Path workingDirectory) {
        this.executable = executable;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Runs the git executable with the given arguments over the project directory.
     *
     * @return the result of the run, or empty when the executable cannot be run
     */
    Optional<Result> run(List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-C");
        command.add(workingDirectory.toAbsolutePath().toString());
        command.addAll(arguments);

        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(processDirectory())
                    .redirectErrorStream(false)
                    .start();
        } catch (IOException e) {
            logger.info("The git executable '{}' cannot be run: {}", executable, e.getMessage());
            return Optional.empty();
        }

        StringBuilder errors = new StringBuilder();
        Thread errorReader = new Thread(() -> readAll(process.getErrorStream(), errors));
        errorReader.start();

        String output = "";
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.info("The output of '{}' cannot be read: {}", executable, e.getMessage());
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
            errorReader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        return Optional.of(new Result(exitCode, output, errors.toString()));
    }

    /**
     * @return the directory the process runs in: the project directory when it exists, the directory of the
     * run otherwise, so that a missing project directory is reported by git and not by the process
     */
    private File processDirectory() {
        if (Files.isDirectory(workingDirectory)) {
            return workingDirectory.toFile();
        }
        return new File(".");
    }

    private static void readAll(InputStream stream, StringBuilder target) {
        try (InputStream input = stream) {
            target.append(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.info("The error output of the git executable cannot be read: {}", e.getMessage());
        }
    }

    /**
     * The outcome of one run of the git executable.
     *
     * @param exitCode the exit code of the process
     * @param output the standard output of the process
     * @param error the error output of the process
     */
    record Result(int exitCode, String output, String error) {
    }
}
