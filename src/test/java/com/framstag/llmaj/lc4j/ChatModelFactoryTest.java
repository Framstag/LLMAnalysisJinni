package com.framstag.llmaj.lc4j;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.localai.LocalAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.MalformedURLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the factory builds per provider and the streaming setting: with streaming enabled the
 * OpenAI provider yields the adapter over the streamed client, every other provider refuses the
 * run before it can start, and with streaming disabled the synchronous clients come back
 * untouched.
 */
public class ChatModelFactoryTest {

    private Config configFor(ModelProvider provider) throws MalformedURLException {
        Config config = new Config();
        config.setModelProvider(provider);
        config.setModelURL(URI.create("http://localhost:8001").toURL());
        config.setModelName("test-model");
        config.setApiKey("test-key");

        return config;
    }

    @Test
    void streamingOnOpenAiYieldsTheAdapter() throws IOException {
        Config config = configFor(ModelProvider.OPENAI);
        config.setUseStreaming(true);

        ChatModel model = ChatModelFactory.getChatModel(config);

        assertInstanceOf(StreamingChatModelAdapter.class, model,
                "the streamed answer is bridged to the blocking contract through the adapter");
    }

    @Test
    void streamingOnOllamaRefusesToStart() throws MalformedURLException {
        Config config = configFor(ModelProvider.OLLAMA);
        config.setUseStreaming(true);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ChatModelFactory.getChatModel(config),
                "the refusal must happen at the start of the run, before any task step executes");

        assertTrue(thrown.getMessage().contains("ollama"),
                "the error must name the provider that cannot stream: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Streaming is not supported"),
                "the error must say that streaming is the unsupported part: " + thrown.getMessage());
    }

    @Test
    void streamingOnLocalAiRefusesToStart() throws MalformedURLException {
        Config config = configFor(ModelProvider.LOCALAI);
        config.setUseStreaming(true);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ChatModelFactory.getChatModel(config));

        assertTrue(thrown.getMessage().contains("localai"),
                "the error must name the provider that cannot stream: " + thrown.getMessage());
    }

    @Test
    void streamingDisabledKeepsTheSynchronousClients() throws MalformedURLException {
        assertEquals(OllamaChatModel.class,
                ChatModelFactory.getChatModel(configFor(ModelProvider.OLLAMA)).getClass());
        assertEquals(OpenAiChatModel.class,
                ChatModelFactory.getChatModel(configFor(ModelProvider.OPENAI)).getClass());
        assertEquals(LocalAiChatModel.class,
                ChatModelFactory.getChatModel(configFor(ModelProvider.LOCALAI)).getClass());
    }

    @Test
    void streamingDisabledIsTheDefaultForAnUnconfiguredConfig() throws MalformedURLException {
        Config config = configFor(ModelProvider.OPENAI);

        ChatModel model = ChatModelFactory.getChatModel(config);

        assertEquals(OpenAiChatModel.class, model.getClass(),
                "a workspace that never set the streaming setting must get exactly what it got before");
    }
}
