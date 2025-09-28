package com.framstag.llmaj;

import java.io.IOException;

import com.framstag.llmaj.cli.CLIExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        int exitCode = new CommandLine(new CLIExecutor()).execute(args);
        System.exit(exitCode);
    }
}
