package com.framstag.llmaj.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "LLMAnalysisJinni",
        subcommands = {
                AnalyseCmd.class,
                DocumentCmd.class,
                JavaAnalysisCmd.class,
                StateCmd.class,
                WorkspaceCmd.class,
                CommandLine.HelpCommand.class
        },
        mixinStandardHelpOptions = true)
public class CLIExecutor {
}
