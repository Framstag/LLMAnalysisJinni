package com.framstag.llmaj.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "state", description = "state management commands",
        subcommands = {
        StateClearCmd.class,
        CommandLine.HelpCommand.class
},
        mixinStandardHelpOptions = true)
public class StateCmd {
}
