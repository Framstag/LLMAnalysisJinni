package com.framstag.llmaj.lc4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles console and file logging of LLM chat interactions.
 * <p>
 * Each instance owns its own de-duplication state for progressive console output.
 * Thread-safe by instance isolation — one ChatLogger per ChatExecutor execution.
 */
public class ChatLogger {
    private static final Logger logger = LoggerFactory.getLogger(ChatLogger.class);

    private int consoleShownIndex = 0;

    /**
     * Log messages to console progressively.
     * Only messages not yet shown (since last call) are logged.
     *
     * @param messages  the full message list from chat memory
     * @param showSystem whether to include SystemMessage entries
     */
    public void logProgressive(List<ChatMessage> messages, boolean showSystem) {
        for (int i = consoleShownIndex; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            switch (msg.type()) {
                case SYSTEM -> {
                    if (!showSystem) break;
                    var sm = (dev.langchain4j.data.message.SystemMessage) msg;
                    logger.info("##> {}", sm.text());
                }
                case USER -> {
                    var um = (dev.langchain4j.data.message.UserMessage) msg;
                    if (um.hasSingleText()) {
                        logger.info("> {}", um.singleText());
                    } else {
                        logger.info("> {}", um);
                    }
                }
                case AI -> {
                    var am = (dev.langchain4j.data.message.AiMessage) msg;
                    if (am.hasToolExecutionRequests()) {
                        // Tool calls are logged inline in ChatExecutor before execution
                        break;
                    }
                    if (am.text() != null && !am.text().isEmpty()) {
                        logger.info("< {}", am.text());
                    }
                    if (am.thinking() != null && !am.thinking().isEmpty()) {
                        logger.info("> Thinking: {}", am.thinking());
                    }
                }
                case TOOL_EXECUTION_RESULT -> {
                    // Tool results are logged inline in ChatExecutor after execution
                }
                default -> logger.info("??? {}", msg);
            }
        }
        consoleShownIndex = messages.size();
    }

    /**
     * Advance the console shown index to the given position.
     * Used when tool-related messages are logged inline in ChatExecutor.
     */
    public void advanceShownIndexTo(int index) {
        this.consoleShownIndex = index;
    }

    /**
     * Write the full conversation to a log file.
     * Always includes all messages, thinking traces, and token usage.
     *
     * @param workspacePath  root workspace directory (logs/ subdirectory created here)
     * @param taskId         task identifier for filename
     * @param loopIndex      loop index for filename (null for non-loop tasks)
     * @param messages       complete list of chat messages
     * @param tokenUsage     aggregate token usage
     * @throws IOException if file writing fails
     */
    public void writeLogFile(Path workspacePath, String taskId, Integer loopIndex,
                             List<ChatMessage> messages, TokenUsage tokenUsage) throws IOException {
        String filename;
        if (loopIndex != null) {
            filename = taskId + "_" + loopIndex + ".log";
        } else {
            filename = taskId + ".log";
        }

        Path logsDir = workspacePath.resolve("logs");
        Files.createDirectories(logsDir);

        Path logFile = logsDir.resolve(filename);

        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        sb.append("=== Execution: ").append(taskId);
        if (loopIndex != null) {
            sb.append("_").append(loopIndex);
        }
        sb.append(" (").append(timestamp).append(") ===\n\n");

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String label = switch (msg.type()) {
                case SYSTEM -> "System";
                case USER -> "User";
                case AI -> "AI";
                case TOOL_EXECUTION_RESULT -> "ToolResult";
                default -> "Unknown";
            };

            sb.append("-- Message ").append(i + 1).append(" (").append(label).append(") --\n");

            switch (msg.type()) {
                case SYSTEM -> {
                    var sm = (dev.langchain4j.data.message.SystemMessage) msg;
                    sb.append(sm.text()).append("\n");
                }
                case USER -> {
                    var um = (dev.langchain4j.data.message.UserMessage) msg;
                    if (um.hasSingleText()) {
                        sb.append(um.singleText()).append("\n");
                    } else {
                        sb.append(um).append("\n");
                    }
                }
                case AI -> {
                    var am = (dev.langchain4j.data.message.AiMessage) msg;
                    if (am.text() != null && !am.text().isEmpty()) {
                        sb.append(am.text()).append("\n");
                    }
                    if (am.thinking() != null && !am.thinking().isEmpty()) {
                        sb.append("→ Thinking: ").append(am.thinking()).append("\n");
                    }
                    if (am.hasToolExecutionRequests()) {
                        for (var req : am.toolExecutionRequests()) {
                            sb.append("  → Tool ").append(req.name()).append(": ").append(req.arguments()).append("\n");
                        }
                    }
                }
                case TOOL_EXECUTION_RESULT -> {
                    var tm = (dev.langchain4j.data.message.ToolExecutionResultMessage) msg;
                    sb.append("Tool: ").append(tm.toolName()).append("\n");
                    sb.append(tm.text()).append("\n");
                }
                default -> sb.append(msg).append("\n");
            }

            sb.append("\n");
        }

        sb.append("-- Token usage: IN ").append(tokenUsage.inputTokenCount())
                .append(" / OUT ").append(tokenUsage.outputTokenCount())
                .append(" / TOTAL ").append(tokenUsage.totalTokenCount())
                .append(" --\n");

        Files.writeString(logFile, sb.toString());
    }
}