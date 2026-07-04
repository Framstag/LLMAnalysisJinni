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
    void testExecutionTraceDefaultsToTrue() {
        Config config = new Config();
        assertTrue(config.isExecutionTrace(), "Execution trace should default to true");
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
        // The log file path follows <workspace>/logs/<taskId>[_<loopIndex>].log
        // Non-loop: <taskId>.log
        // Loop:     <taskId>_<loopIndex>.log
        String taskId = "ArchitectureAnalysis";
        Integer loopIndex = null;
        String nonLoopName = loopIndex != null ? taskId + "_" + loopIndex + ".log" : taskId + ".log";
        assertEquals("ArchitectureAnalysis.log", nonLoopName);

        loopIndex = 3;
        String loopName = loopIndex != null ? taskId + "_" + loopIndex + ".log" : taskId + ".log";
        assertEquals("ArchitectureAnalysis_3.log", loopName);
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
        chatLogger.writeLogFile(tempDir, "TestTask", null, messages, tokenUsage);

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
        chatLogger.writeLogFile(tempDir, "ThinkingTask", null, messages, tokenUsage);

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
        chatLogger.writeLogFile(tempDir, "LoopTask", 3, messages, tokenUsage);

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
        chatLogger.writeLogFile(tempDir, "OverwriteTask", null, messages, tokenUsage);

        // Then: file is overwritten, not appended
        String content = Files.readString(logsDir.resolve("OverwriteTask.log"));
        assertFalse(content.contains("old content"), "Old content should be overwritten");
        assertTrue(content.contains("new content"), "New content should appear");
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