package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tasks.StepTokenUsage;
import com.framstag.llmaj.tasks.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Reports the token usage of a workspace: the total over all tasks, the usage of every task, and
 * the usage of every loop index of a loop task.
 * <p>
 * The figures are the ones recorded in the execution state, so they are the usage of the last
 * execution of each step, not the usage of the last run of the workspace. A step that was not
 * executed again keeps the figure of its last execution.
 */
@Command(name = "tokens", description = "Report the token usage of a workspace",
        mixinStandardHelpOptions = true)
public class StateTokensCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(StateTokensCmd.class);
    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @CommandLine.Parameters(index = "0", description = "Path to the working directory")
    Path workingDirectory;

    @CommandLine.Option(names = "--json", description = "Write the report as JSON")
    boolean json;

    @Override
    public Integer call() throws Exception {
        Config config;

        try {
            config = ConfigLoader.loadFromWorkingDirectory(workingDirectory);
        } catch (IOException e) {
            logger.error("Cannot load config file", e);
            return 1;
        }

        TaskManager taskManager = TaskManager.initializeTasks(config.getAnalysisDirectory(),
                workingDirectory,
                Collections.emptySet());

        if (taskManager == null) {
            logger.error("Cannot retrieve task list, aborting.");
            return 1;
        }

        Map<String, StepTokenUsage> usages = taskManager.getTokenUsages();

        if (json) {
            System.out.println(writeJson(usages, taskManager));
        } else {
            System.out.print(writeTable(usages, taskManager));
        }

        return 0;
    }

    /**
     * The report as JSON: the total over all tasks, and every task with its usage and the usage of
     * its loop indices. The field names are the ones the execution state uses, so a reader that
     * knows the state file knows this report.
     */
    private String writeJson(Map<String, StepTokenUsage> usages, TaskManager taskManager) {
        ObjectNode root = mapper.createObjectNode();
        root.put("workspace", workingDirectory.toAbsolutePath().normalize().toString());

        StepTokenUsage total = sum(usages.values());
        root.set("total", usageNode(total));

        ArrayNode tasks = root.putArray("tasks");
        for (Map.Entry<String, StepTokenUsage> entry : sortedByTaskId(usages).entrySet()) {
            ObjectNode task = tasks.addObject();
            task.put("taskId", entry.getKey());
            task.set("tokenUsage", usageNode(entry.getValue()));

            Map<Integer, StepTokenUsage> indices = sortedByIndex(taskManager.getIndexTokenUsage(entry.getKey()));

            if (!indices.isEmpty()) {
                ArrayNode indexArray = task.putArray("indexTokenUsage");

                for (Map.Entry<Integer, StepTokenUsage> index : indices.entrySet()) {
                    ObjectNode indexNode = indexArray.addObject();
                    indexNode.put("index", index.getKey());
                    indexNode.set("tokenUsage", usageNode(index.getValue()));
                }
            }
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (IOException e) {
            logger.error("Cannot write the token report as JSON", e);
            return "{}";
        }
    }

    /**
     * The report as a table: one line per task, one line per loop index below its task, and the
     * total over the task lines.
     */
    private String writeTable(Map<String, StepTokenUsage> usages, TaskManager taskManager) {
        StringBuilder sb = new StringBuilder();

        sb.append("Token usage of workspace '")
                .append(workingDirectory.toAbsolutePath().normalize())
                .append("'\n\n");

        if (usages.isEmpty()) {
            sb.append("No token usage is recorded for this workspace.\n");
            sb.append("The execution state holds the usage of the tasks that were executed.\n");

            return sb.toString();
        }

        sb.append(String.format(Locale.ROOT, "%-32s %14s %14s %14s%n", "TASK", "INPUT", "OUTPUT", "TOTAL"));
        sb.append("-".repeat(78)).append('\n');

        for (Map.Entry<String, StepTokenUsage> entry : sortedByTaskId(usages).entrySet()) {
            StepTokenUsage usage = entry.getValue();

            sb.append(String.format(Locale.ROOT, "%-32s %14s %14s %14s%n",
                    entry.getKey(),
                    format(usage.inputTokens()),
                    format(usage.outputTokens()),
                    format(usage.totalTokens())));

            for (Map.Entry<Integer, StepTokenUsage> index
                    : sortedByIndex(taskManager.getIndexTokenUsage(entry.getKey())).entrySet()) {
                StepTokenUsage indexUsage = index.getValue();

                sb.append(String.format(Locale.ROOT, "  %-30s %14s %14s %14s%n",
                        "index " + index.getKey(),
                        format(indexUsage.inputTokens()),
                        format(indexUsage.outputTokens()),
                        format(indexUsage.totalTokens())));
            }
        }

        sb.append("-".repeat(78)).append('\n');

        StepTokenUsage total = sum(usages.values());

        sb.append(String.format(Locale.ROOT, "%-32s %14s %14s %14s%n",
                "TOTAL",
                format(total == null ? null : total.inputTokens()),
                format(total == null ? null : total.outputTokens()),
                format(total == null ? null : total.totalTokens())));
        sb.append('\n');
        sb.append("The index lines are the breakdown of the task line above them.\n");
        sb.append("The total counts every task line once.\n");

        return sb.toString();
    }

    /**
     * The recorded usages, ordered by task id. The tasks of a workspace are recorded in the order
     * they finished, so the order they are stored in is not the order a reader expects.
     */
    private static Map<String, StepTokenUsage> sortedByTaskId(Map<String, StepTokenUsage> usages) {
        return usages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    /**
     * The usages of the loop indices of a task, ordered by index. The indices of a loop task finish
     * in the order the workers complete them, so the order they are recorded in is not the order a
     * reader expects.
     */
    private static Map<Integer, StepTokenUsage> sortedByIndex(Map<Integer, StepTokenUsage> usages) {
        return usages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    private static ObjectNode usageNode(StepTokenUsage usage) {
        ObjectNode node = mapper.createObjectNode();

        if (usage == null) {
            // A workspace without any recorded usage has no counts at all, and a count that is not
            // known is not a count of zero.
            node.putNull("inputTokens");
            node.putNull("outputTokens");
            node.putNull("totalTokens");

            return node;
        }

        node.put("inputTokens", usage.inputTokens());
        node.put("outputTokens", usage.outputTokens());
        node.put("totalTokens", usage.totalTokens());

        return node;
    }

    private static StepTokenUsage sum(Iterable<StepTokenUsage> usages) {
        StepTokenUsage total = null;

        for (StepTokenUsage usage : usages) {
            total = StepTokenUsage.sum(total, usage);
        }

        return total;
    }

    /**
     * A count the provider did not report is not a count of zero, so it is shown as a dash.
     */
    private static String format(Integer count) {
        return count == null ? "-" : String.format(Locale.ROOT, "%,d", count);
    }
}
