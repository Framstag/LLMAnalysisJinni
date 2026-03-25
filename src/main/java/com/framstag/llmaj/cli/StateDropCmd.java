package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tasks.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.framstag.llmaj.tasks.TaskManager.STATE_JSON_FILENAME;

@Command(name = "drop", description = "Drops the execution state of one or more tasks",
        mixinStandardHelpOptions = true)
public class StateDropCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(StateDropCmd.class);
    private static final ObjectMapper mapper;

    @CommandLine.Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    Path workingDirectory;

    @CommandLine.Parameters(index = "1",arity = "1..*", description = "List of task name to drop from execution state file")
    List<String> taskNames;

    static {
        mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
    }

    @Override
    public Integer call() throws Exception {
        logger.info("Dropping task execution state(s) for working directory '{}'...", workingDirectory);

        Path stateFilePath = workingDirectory.resolve(STATE_JSON_FILENAME);
        File stateFile = stateFilePath.toFile();

        Map<String, TaskState> taskStateMap;

        if (stateFile.exists() && stateFile.isFile()) {
            taskStateMap = Arrays.stream(TaskState.loadTaskState(stateFilePath))
                    .collect(Collectors.toMap(TaskState::taskId, Function.identity()));
        }
        else {
            taskStateMap = new HashMap<>();
        }

        for (String taskName : taskNames) {
            if (taskStateMap.containsKey(taskName)) {
                taskStateMap.remove(taskName);
                logger.info("Removed task '{}' from execution state", taskName);
            }
            else {
                logger.error("Task '{}' does not exist in execution state", taskName);
            }
        }

        logger.info("Writing execution state  to '{}'...",stateFilePath);

        try {
            TaskState[] taskStates = taskStateMap.values().toArray(TaskState[]::new);
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, taskStates);
        } catch (IOException e) {
            logger.error("Exception while writing result to file", e);
        }

        logger.info("done.");

        return 0;
    }
}
