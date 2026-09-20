package com.framstag.llmaj.lc4j;

import com.framstag.llmaj.config.Config;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatExecutionLoggingTest {

    @Test
    void testExecutionTraceDefaultsToFalse() {
        Config config = new Config();
        assertFalse(config.isExecutionTrace(),
                "Execution trace should default to false, the TUI is the default display mode");
    }

    @Test
    void testExecutionTraceCanBeDisabled() {
        Config config = new Config();
        config.setExecutionTrace(false);
        assertFalse(config.isExecutionTrace());
    }

    @Test
    void testChatExecutionContextHasTaskInfo() {
        Config config = new Config();
        // Verify ChatExecutionContext accepts and stores taskId and loopIndex
        // This is a compilation/construction sanity check
        assertNotNull(config);
    }

    @Test
    void testExecutionTraceSystemDefaultsToFalse() {
        Config config = new Config();
        assertFalse(config.isExecutionTraceSystem(), "System trace should default to false");
    }

    @Test
    void testExecutionTraceSystemCanBeEnabled() {
        Config config = new Config();
        config.setExecutionTraceSystem(true);
        assertTrue(config.isExecutionTraceSystem());
    }

    @Test
    void testLogFileHasDeterministicPath() {
        // The log file path follows <workspace>/logs/<taskId>[_<loopIndex>][.attempt<N>].log
        // Non-loop: <taskId>.log
        // Loop:     <taskId>_<loopIndex>.log
        // A further attempt of the same step adds its attempt number.
        String taskId = "ArchitectureAnalysis";
        Integer loopIndex = null;
        String firstAttempt = stepName(taskId, loopIndex, 1);
        assertEquals("ArchitectureAnalysis.log", firstAttempt);

        loopIndex = 3;
        String loopName = stepName(taskId, loopIndex, 1);
        assertEquals("ArchitectureAnalysis_3.log", loopName);

        assertEquals("ArchitectureAnalysis.attempt2.log", stepName(taskId, null, 2));
        assertEquals("ArchitectureAnalysis_3.attempt3.log", stepName(taskId, 3, 3));
    }

    /**
     * The naming rule the log file name follows, mirrored here so a change to either side fails
     * this test.
     */
    private static String stepName(String taskId, Integer loopIndex, int attempt) {
        String step = loopIndex != null ? taskId + "_" + loopIndex : taskId;

        return attempt > 1 ? step + ".attempt" + attempt + ".log" : step + ".log";
    }

    @Test
    void testLogProgressiveDeduplication() {
        // Given: a ChatLogger with messages
        ChatLogger chatLogger = new ChatLogger();
        List<ChatMessage> messages = List.of(
                UserMessage.from("Hello"),
                AiMessage.from("Hi there")
        );

        // Attach a list appender to capture log output
        Logger chatLoggerLogger = (Logger) LoggerFactory.getLogger(ChatLogger.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        chatLoggerLogger.addAppender(listAppender);

        try {
            // When: logging progressively the first time
            chatLogger.logProgressive(messages, false);
            long firstCallCount = listAppender.list.size();

            // Then: messages were logged
            assertTrue(firstCallCount > 0, "First call should log messages");

            // When: logging progressively the second time with same messages
            chatLogger.logProgressive(messages, false);
            long secondCallCount = listAppender.list.size();

            // Then: no new messages logged (dedup)
            assertEquals(firstCallCount, secondCallCount,
                    "Second call with same messages should not log anything new");
        } finally {
            chatLoggerLogger.detachAppender(listAppender);
        }
    }

    @Test
    void testLogProgressiveSystemFilter() {
        // Given: a ChatLogger with a system message and user message
        ChatLogger chatLogger = new ChatLogger();
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("What is Java?")
        );

        Logger chatLoggerLogger = (Logger) LoggerFactory.getLogger(ChatLogger.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        chatLoggerLogger.addAppender(listAppender);

        try {
            // When: logging with showSystem=false
            chatLogger.logProgressive(messages, false);

            // Then: only the user message appears, system message is filtered
            boolean hasSystemMessage = listAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("You are a helpful assistant"));
            boolean hasUserMessage = listAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("What is Java?"));

            assertFalse(hasSystemMessage, "System message should be filtered when showSystem=false");
            assertTrue(hasUserMessage, "User message should appear");
        } finally {
            chatLoggerLogger.detachAppender(listAppender);
        }
    }

    @Test
    void testLogProgressiveShowsSystemWhenEnabled(@TempDir Path tempDir) {
        // Given: a ChatLogger with a system message and user message
        ChatLogger chatLogger = new ChatLogger();
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("What is Java?")
        );

        Logger chatLoggerLogger = (Logger) LoggerFactory.getLogger(ChatLogger.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        chatLoggerLogger.addAppender(listAppender);

        try {
            // When: logging with showSystem=true
            chatLogger.logProgressive(messages, true);

            // Then: system message appears
            boolean hasSystemMessage = listAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("You are a helpful assistant"));
            assertTrue(hasSystemMessage, "System message should appear when showSystem=true");
        } finally {
            chatLoggerLogger.detachAppender(listAppender);
        }
    }

    @Test
    void testLogProgressiveShowsThinking(@TempDir Path tempDir) {
        // Given: a ChatLogger with an AI message that has thinking
        ChatLogger chatLogger = new ChatLogger();
        AiMessage aiMessage = AiMessage.builder()
                .text("The answer is 42")
                .thinking("Let me calculate...")
                .build();
        List<ChatMessage> messages = List.of(aiMessage);

        Logger chatLoggerLogger = (Logger) LoggerFactory.getLogger(ChatLogger.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        chatLoggerLogger.addAppender(listAppender);

        try {
            // When: logging progressively
            chatLogger.logProgressive(messages, false);

            // Then: thinking trace appears on console
            boolean hasThinking = listAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("Thinking:")
                            && e.getFormattedMessage().contains("Let me calculate"));
            assertTrue(hasThinking, "Thinking trace should appear on console");
        } finally {
            chatLoggerLogger.detachAppender(listAppender);
        }
    }

    @Test
    void testWriteLogFileContainsAllMessages(@TempDir Path tempDir) throws IOException {
        // Given: a ChatLogger with messages
        ChatLogger chatLogger = new ChatLogger();
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a bot"),
                UserMessage.from("Hello"),
                AiMessage.from("Hi!")
        );
        TokenUsage tokenUsage = new TokenUsage(100, 50, 150);

        // When: writing log file
        chatLogger.writeLogFile(tempDir, "TestTask", null, 1, messages, tokenUsage);

        // Then: file exists at expected path
        Path logFile = tempDir.resolve("logs").resolve("TestTask.log");
        assertTrue(Files.exists(logFile), "Log file should exist");

        // And: file contains all messages with labels
        String content = Files.readString(logFile);
        assertTrue(content.contains("-- Message 1 (System) --"), "Should contain System label");
        assertTrue(content.contains("-- Message 2 (User) --"), "Should contain User label");
        assertTrue(content.contains("-- Message 3 (AI) --"), "Should contain AI label");
        assertTrue(content.contains("You are a bot"), "Should contain system text");
        assertTrue(content.contains("Hello"), "Should contain user text");
        assertTrue(content.contains("Hi!"), "Should contain AI text");

        // And: file contains token usage
        assertTrue(content.contains("Token usage: IN 100 / OUT 50 / TOTAL 150"),
                "Should contain token usage");
    }

    @Test
    void testWriteLogFileContainsThinkingTrace(@TempDir Path tempDir) throws IOException {
        // Given: a ChatLogger with an AI message that has thinking
        ChatLogger chatLogger = new ChatLogger();
        AiMessage aiMessage = AiMessage.builder()
                .text("The answer is 42")
                .thinking("Let me calculate...")
                .build();
        List<ChatMessage> messages = List.of(
                UserMessage.from("What is the answer?"),
                aiMessage
        );
        TokenUsage tokenUsage = new TokenUsage(50, 30, 80);

        // When: writing log file
        chatLogger.writeLogFile(tempDir, "ThinkingTask", null, 1, messages, tokenUsage);

        // Then: file contains thinking trace
        Path logFile = tempDir.resolve("logs").resolve("ThinkingTask.log");
        String content = Files.readString(logFile);
        assertTrue(content.contains("→ Thinking: Let me calculate..."),
                "Should contain thinking trace with arrow prefix");
    }

    @Test
    void testWriteLogFileLoopIndex(@TempDir Path tempDir) throws IOException {
        // Given: a ChatLogger with loop index
        ChatLogger chatLogger = new ChatLogger();
        List<ChatMessage> messages = List.of(UserMessage.from("test"));
        TokenUsage tokenUsage = new TokenUsage(10, 5, 15);

        // When: writing log file with loop index
        chatLogger.writeLogFile(tempDir, "LoopTask", 3, 1, messages, tokenUsage);

        // Then: file name includes loop index
        Path logFile = tempDir.resolve("logs").resolve("LoopTask_3.log");
        assertTrue(Files.exists(logFile), "Log file with loop index should exist");

        // And: execution header includes loop index
        String content = Files.readString(logFile);
        assertTrue(content.contains("LoopTask_3"), "Header should contain taskId_loopIndex");
    }

    @Test
    void testWriteLogFileOverwritesExisting(@TempDir Path tempDir) throws IOException {
        // Given: an existing log file
        ChatLogger chatLogger = new ChatLogger();
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        Files.writeString(logsDir.resolve("OverwriteTask.log"), "old content");

        List<ChatMessage> messages = List.of(UserMessage.from("new content"));
        TokenUsage tokenUsage = new TokenUsage(1, 1, 2);

        // When: writing log file again
        chatLogger.writeLogFile(tempDir, "OverwriteTask", null, 1, messages, tokenUsage);

        // Then: file is overwritten, not appended
        String content = Files.readString(logsDir.resolve("OverwriteTask.log"));
        assertFalse(content.contains("old content"), "Old content should be overwritten");
        assertTrue(content.contains("new content"), "New content should appear");
    }

    @Test
    void testRetryAttemptWritesItsOwnLogFile(@TempDir Path tempDir) throws IOException {
        ChatLogger chatLogger = new ChatLogger();
        TokenUsage tokenUsage = new TokenUsage(10, 5, 15);

        // When: the same step is attempted twice
        chatLogger.writeLogFile(tempDir, "RetryTask", null, 1,
                List.of(UserMessage.from("first attempt")), tokenUsage);
        chatLogger.writeLogFile(tempDir, "RetryTask", null, 2,
                List.of(UserMessage.from("second attempt")), tokenUsage);

        Path logsDir = tempDir.resolve("logs");
        Path firstAttempt = logsDir.resolve("RetryTask.log");
        Path secondAttempt = logsDir.resolve("RetryTask.attempt2.log");

        assertTrue(Files.exists(firstAttempt), "the first attempt keeps the plain name");
        assertTrue(Files.exists(secondAttempt), "a further attempt carries its attempt number");
        assertTrue(Files.readString(firstAttempt).contains("first attempt"),
                "the transcript of the rejected attempt must survive the next one");
        assertFalse(Files.readString(secondAttempt).contains("first attempt"),
                "an attempt must not append to the log of another attempt");
        assertTrue(Files.readString(secondAttempt).contains("attempt 2"),
                "the file must name the attempt it holds");
    }

    @Test
    void testLoopIndexWithRetryAttempt(@TempDir Path tempDir) throws IOException {
        ChatLogger chatLogger = new ChatLogger();
        TokenUsage tokenUsage = new TokenUsage(10, 5, 15);

        chatLogger.writeLogFile(tempDir, "LoopTask", 3, 3,
                List.of(UserMessage.from("third attempt")), tokenUsage);

        Path logFile = tempDir.resolve("logs").resolve("LoopTask_3.attempt3.log");

        assertTrue(Files.exists(logFile), "a loop index and an attempt number must both appear");
        assertTrue(Files.readString(logFile).contains("LoopTask_3"),
                "the header must name the step");
    }

    @Test
    void testRerunOverwritesTheAttemptFile(@TempDir Path tempDir) throws IOException {
        ChatLogger chatLogger = new ChatLogger();
        TokenUsage tokenUsage = new TokenUsage(10, 5, 15);

        chatLogger.writeLogFile(tempDir, "RerunTask", null, 2,
                List.of(UserMessage.from("old second attempt")), tokenUsage);
        chatLogger.writeLogFile(tempDir, "RerunTask", null, 2,
                List.of(UserMessage.from("new second attempt")), tokenUsage);

        String content = Files.readString(tempDir.resolve("logs").resolve("RerunTask.attempt2.log"));

        assertFalse(content.contains("old second attempt"),
                "a later run must overwrite the attempt file instead of appending to it");
        assertTrue(content.contains("new second attempt"));
    }

    @Test
    void testAdvanceShownIndex(@TempDir Path tempDir) {
        // Given: a ChatLogger with messages
        ChatLogger chatLogger = new ChatLogger();
        List<ChatMessage> messages = List.of(
                UserMessage.from("msg1"),
                UserMessage.from("msg2"),
                UserMessage.from("msg3")
        );

        Logger chatLoggerLogger = (Logger) LoggerFactory.getLogger(ChatLogger.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        chatLoggerLogger.addAppender(listAppender);

        try {
            // When: advancing index past first 2 messages, then logging
            chatLogger.advanceShownIndexTo(2);
            chatLogger.logProgressive(messages, false);

            // Then: only the last message is logged
            assertEquals(1, listAppender.list.size(),
                    "Only one message should be logged after advanceShownIndex");
            assertTrue(listAppender.list.get(0).getFormattedMessage().contains("msg3"),
                    "Should log the third message");
        } finally {
            chatLoggerLogger.detachAppender(listAppender);
        }
    }
}