package com.framstag.llmaj.tools.file;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.file.FileHelper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileIOTool {
    private static final Logger logger = LoggerFactory.getLogger(FileIOTool.class);

    private final AnalysisContext context;

    /**
     * Conditions already reported for this run. The model can ask for a path that does not exist, and
     * it can ask again; one record per repetition would fill the log with a condition that needs one
     * line.
     */
    private final Set<String> reportedConditions = ConcurrentHashMap.newKeySet();

    public FileIOTool(AnalysisContext context) {
        this.context = context;
        logger.info("FileIOTool initialized.");
    }

    @Tool(name = "fileio_read_file",
            value =
                    """
                        Returns the content of the given file.
                    """)
    public String readFile(@P("The file fo which its contents should be returned. The path should be relative to the project root directory") String file) {
        logger.info("## ReadFile('{}')", file);

        Path root = context.getProjectRoot();
        Path filePath = Path.of(file);

        if (!FileHelper.accessAllowed(root, filePath)) {
            // A path outside the project is something the model can correct, so the result names it.
            reportCondition("outside the project root", file, null);

            return "ERROR: the path '" + file + "' is not inside the project root and cannot be read.";
        }

        String fileContent;

        try {
            fileContent = Files.readString(root.resolve(filePath));

            logger.info("## ReadFile() => '{}'", "<file content>");

            return fileContent;
        }
        catch (IOException e) {
            // A file the model asked for that does not exist is a condition the model can act on: the
            // error reaches it as the tool result, and the log keeps one line for it instead of a stack
            // trace per call. The Maven run of 2026-10-06 logged such a call at ERROR with a stack trace
            // for a file the model guessed.
            reportCondition("cannot be read", file, e);

            String errorText = "ERROR: the file '" + file + "' cannot be read (" + e.getMessage() + ")";
            logger.info("## ReadFile() => '{}'", errorText);

            return errorText;
        }
    }

    /**
     * Reports a condition the model can act on at most once per run above DEBUG, without a stack trace.
     */
    private void reportCondition(String condition, String file, IOException cause) {
        if (reportedConditions.add(condition + " " + file)) {
            logger.warn("The file '{}' {}; the condition is returned to the model as the tool result",
                    file, condition);
        } else {
            logger.debug("The file '{}' {}, reported before in this run", file, condition, cause);
        }
    }
}
