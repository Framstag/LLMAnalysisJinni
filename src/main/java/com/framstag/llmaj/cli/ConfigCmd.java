package com.framstag.llmaj.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "config", description = "config commands",
        subcommands = {
                ConfigValidateCmd.class,
                CommandLine.HelpCommand.class
        },
        mixinStandardHelpOptions = true)
public class ConfigCmd {
}
