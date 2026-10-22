package com.framstag.llmaj.lc4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation of one attempt of a task step.
 * <p>
 * The messages that carry the task are kept, and the conversation that follows them is bounded by a
 * window. A step that needs many tool rounds would otherwise push the task out of the context, and
 * the model would answer without knowing what it was asked to do.
 * <p>
 * The window is cut at a message boundary that leaves no tool result without the message that
 * asked for it, so the model never sees a result it cannot place.
 */
final class TaskChatMemory implements ChatMemory {

    private final int maxConversationMessages;
    private final List<ChatMessage> task = new ArrayList<>();
    private final List<ChatMessage> conversation = new ArrayList<>();
    private final Object id = new Object();

    TaskChatMemory(int maxConversationMessages) {
        this.maxConversationMessages = Math.max(1, maxConversationMessages);
    }

    /**
     * Adds the messages that carry the task. They are kept for the whole attempt.
     *
     * @param messages the system and user messages of the step
     */
    void addTask(List<ChatMessage> messages) {
        task.addAll(messages);
    }

    @Override
    public void add(ChatMessage message) {
        conversation.add(message);
    }

    /**
     * The messages the model is asked with: the task, and the tail of the conversation.
     */
    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> result = new ArrayList<>(task);
        result.addAll(conversationTail());

        return result;
    }

    /**
     * The whole conversation, without the window. It is what the chat log holds, so that a reader of
     * the log sees the attempt from its beginning.
     */
    List<ChatMessage> fullMessages() {
        List<ChatMessage> result = new ArrayList<>(task);
        result.addAll(conversation);

        return result;
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void clear() {
        task.clear();
        conversation.clear();
    }

    /**
     * The tail of the conversation that fits into the window, cut so that it does not start with a
     * tool result whose message was cut away.
     */
    private List<ChatMessage> conversationTail() {
        if (conversation.size() <= maxConversationMessages) {
            return conversation;
        }

        int from = conversation.size() - maxConversationMessages;

        while (from < conversation.size()
                && conversation.get(from).type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
            from++;
        }

        return conversation.subList(from, conversation.size());
    }
}
