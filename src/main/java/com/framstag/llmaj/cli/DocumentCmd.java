package com.framstag.llmaj.cli;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.state.StateManager;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "document", description = "Document the analysis result",
        mixinStandardHelpOptions = true)
public class DocumentCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(DocumentCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    Path workingDirectory;

    @Override
    public Integer call() throws Exception {
        logger.info("Generating documentation for analysis result in directory '{}'...", workingDirectory);

        Config config;

        try {
            logger.info("Loading config from workspace '{}'...", workingDirectory);
            config = ConfigLoader.load(workingDirectory);
            config.dumpToLog();
        } catch (IOException e) {
            logger.error("Cannot load config file", e);
            return 1;
        }

        StateManager stateManager = StateManager.initializeState(workingDirectory);

        TemplateLoader loader = new FileTemplateLoader(config.getAnalysisDirectory()
                .resolve("documentation")
                .toString(),
                ".hbs");

        var handlebars = HandlebarsFactory.create()
                .with(loader);

        var template = handlebars.compile("Documentation.md");

        String templateResult = template.apply(stateManager.getStateObject());

        Path outputFile = workingDirectory.resolve("Documentation.md");

        logger.info("Storing result in '{}'...", outputFile);

        Files.writeString(outputFile,templateResult);

        logger.info("Documentation generated.");

        return 0;
    }
}
