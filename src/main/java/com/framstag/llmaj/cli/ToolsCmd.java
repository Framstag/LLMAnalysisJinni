package com.framstag.llmaj.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "tools", description = "tools commands",
        subcommands = {
                ToolsDumpCmd.class,
                CommandLine.HelpCommand.class
        },
        mixinStandardHelpOptions = true)
public class ToolsCmd {
}
