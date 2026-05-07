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

public class FileIOTool {
    private static final Logger logger = LoggerFactory.getLogger(FileIOTool.class);

    private final AnalysisContext context;

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
            return "ERROR";
        }

        String fileContent;

        try {
            fileContent = Files.readString(root.resolve(filePath));

            logger.info("## ReadFile() => '{}'", "<file content>");

            return fileContent;
        }
        catch (IOException e) {
            logger.error("Error while reading file",e);

            String errorText="ERROR: "+e.getClass().getName();
            logger.info("## ReadFile() => '{}'", errorText);
            return errorText;
        }
    }
}
