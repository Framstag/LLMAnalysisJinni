package com.framstag.llmaj.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "workspace", description = "workspace commands",
        subcommands = {
        WorkspaceInitCmd.class,
        CommandLine.HelpCommand.class
},
        mixinStandardHelpOptions = true)
public class WorkspaceCmd {
}
