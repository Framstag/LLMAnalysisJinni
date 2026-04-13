package com.framstag.llmaj.cli;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "validate", description = "Loads and validates the config file and print it",
        mixinStandardHelpOptions = true)
public class ConfigValidateCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ConfigValidateCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path of the config file to validate")
    Path configFilePath;

    @Override
    public Integer call() throws Exception {
        logger.info("Validating config file '{}'", configFilePath);
        Config config;

        try {
            logger.info("Loading config from file '{}'...", configFilePath);
            config = ConfigLoader.loadFromPath(configFilePath);

            config.dumpToLog();
        } catch (IOException e) {
            logger.error("Cannot load config file", e);
            return 1;
        }

        return 0;
    }
}
