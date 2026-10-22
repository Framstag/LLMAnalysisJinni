package com.framstag.llmaj.lc4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The conversation of one attempt: the messages that carry the task stay, and the conversation that
 * follows them is bounded by a window.
 * <p>
 * A step that needs many tool rounds must not push the task out of the context. The model would
 * answer without knowing what it was asked to do, and it would answer nothing.
 */
public class TaskChatMemoryTest {

    private static ToolExecutionResultMessage toolResult() {
        return ToolExecutionResultMessage.from(
                ToolExecutionRequest.builder()
                        .id("1")
                        .name("filesystem_file_exists")
                        .arguments("{\"file\":\"a\"}")
                        .build(),
                "true");
    }

    @Test
    void theTaskIsKeptWhenTheConversationFillsTheWindow() {
        TaskChatMemory memory = new TaskChatMemory(10);
        memory.addTask(List.of(SystemMessage.from("SYSTEM"), UserMessage.from("USER")));

        for (int i = 0; i < 30; i++) {
            memory.add(AiMessage.from("thinking"));
            memory.add(toolResult());
        }

        List<ChatMessage> messages = memory.messages();

        assertTrue(messages.stream().anyMatch(m -> m.type() == ChatMessageType.SYSTEM),
                "the system message must survive a long conversation");
        assertTrue(messages.stream().anyMatch(m -> m.type() == ChatMessageType.USER),
                "the task must survive a long conversation, or the model answers without knowing "
                        + "what it was asked to do");
    }

    @Test
    void theConversationIsBoundedByTheWindow() {
        TaskChatMemory memory = new TaskChatMemory(10);
        memory.addTask(List.of(SystemMessage.from("SYSTEM"), UserMessage.from("USER")));

        for (int i = 0; i < 30; i++) {
            memory.add(AiMessage.from("thinking"));
            memory.add(toolResult());
        }

        List<ChatMessage> messages = memory.messages();

        assertEquals(12, messages.size(),
                "the task is kept and the conversation is bounded by the window");
    }

    @Test
    void theWindowDoesNotStartWithAToolResultWithoutItsMessage() {
        TaskChatMemory memory = new TaskChatMemory(4);
        memory.addTask(List.of(SystemMessage.from("SYSTEM"), UserMessage.from("USER")));

        for (int i = 0; i < 10; i++) {
            memory.add(AiMessage.from("thinking"));
            memory.add(toolResult());
        }

        List<ChatMessage> messages = memory.messages();

        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
                assertTrue(i > 0 && messages.get(i - 1).type() == ChatMessageType.AI,
                        "a tool result must not be the first message of the window, or the model "
                                + "sees a result it cannot place");
                break;
            }
        }
    }

    @Test
    void theWholeConversationIsKeptForTheLog() {
        TaskChatMemory memory = new TaskChatMemory(10);
        memory.addTask(List.of(SystemMessage.from("SYSTEM"), UserMessage.from("USER")));

        for (int i = 0; i < 30; i++) {
            memory.add(AiMessage.from("thinking"));
            memory.add(toolResult());
        }

        List<ChatMessage> full = memory.fullMessages();

        assertEquals(62, full.size(), "the log holds the whole conversation, not the window");
        assertTrue(full.stream().anyMatch(m -> m.type() == ChatMessageType.USER),
                "the log holds the task, so a reader sees what the step was asked to do");
    }

    @Test
    void aShortConversationIsNotCut() {
        TaskChatMemory memory = new TaskChatMemory(50);
        memory.addTask(List.of(SystemMessage.from("SYSTEM"), UserMessage.from("USER")));
        memory.add(AiMessage.from("thinking"));

        assertEquals(3, memory.messages().size());
    }

    @Test
    void clearingRemovesTheTaskAsWell() {
        TaskChatMemory memory = new TaskChatMemory(10);
        memory.addTask(List.of(SystemMessage.from("SYSTEM"), UserMessage.from("USER")));
        memory.add(AiMessage.from("thinking"));

        memory.clear();

        assertTrue(memory.messages().isEmpty());
        assertTrue(memory.fullMessages().isEmpty());
    }
}
