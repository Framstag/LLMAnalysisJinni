package com.framstag.llmaj.lc4j;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.List;

public class ChatModelFactory {

    public static ChatModel getChatModel(Config config) {
        if (config.getModelProvider() == ModelProvider.OLLAMA) {
            return OllamaChatModel.builder()
                    .modelName(config.getModelName())
                    .baseUrl(config.getModelURL().toString())
                    .timeout(Duration.ofMinutes(config.getRequestTimeout()))
                    .temperature(0.0)
                    .think(false)
                    .returnThinking(false)
                    .listeners(List.of(new ChatListener()))
                    .logRequests(config.isLogRequests())
                    .logResponses(config.isLogResponses())
                    .numCtx(config.getMaximumTokens())
                    .build();
        }
        else if (config.getModelProvider() == ModelProvider.OPENAI) {

            return OpenAiChatModel.builder()
                    .modelName(config.getModelName())
                    .baseUrl(config.getModelURL().toString())
                    .apiKey(config.getApiKey())
                    .timeout(Duration.ofMinutes(config.getRequestTimeout()))
                    .temperature(0.0)
                    .topP(0.9)
                    .sendThinking(false)
                    .returnThinking(false)
                    .listeners(List.of(new ChatListener()))
                    .logRequests(config.isLogRequests())
                    .logResponses(config.isLogResponses())
                    .maxTokens(config.getMaximumTokens())
                    .build();
        }

        return null;
    }

}
