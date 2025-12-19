package com.framstag.llmaj.cli;

import com.framstag.llmaj.state.StateManager;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "document", description = "Document the analysis result",
        mixinStandardHelpOptions = true)
public class DocumentCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(DocumentCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path to the directory with the analysis definition")
    String analysisDirectory;

    @CommandLine.Parameters(index = "1",description = "Path to the working directory where result of analysis is stored")
    String workingDirectory;

    @Override
    public Integer call() throws Exception {
        logger.info("Generating documentation for working directory '{}'...", workingDirectory);

        StateManager stateManager = StateManager.initializeState(Path.of(workingDirectory));

        TemplateLoader loader = new FileTemplateLoader(Path.of(analysisDirectory)
                .resolve("documentation")
                .toString(),
                ".hbs");

        var handlebars = new Handlebars()
                .with(EscapingStrategy.HTML_ENTITY)
                .with(loader);

        var template = handlebars.compile("Documentation.md");

        String templateResult = template.apply(stateManager.getStateObject());

        Path outputFile = Path.of(workingDirectory).resolve("Documentation.md");

        Files.writeString(outputFile,templateResult);

        logger.info("Documentation generated.");

        return 0;
    }
}
