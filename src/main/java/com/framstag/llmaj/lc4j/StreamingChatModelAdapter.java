package com.framstag.llmaj.lc4j;

import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Presents the streamed answer of a chat model as the one blocking transfer the engine's task
 * steps are built around.
 * <p>
 * The engine calls the model through the synchronous {@link ChatModel} contract, one
 * {@link ChatResponse} per call, and nothing in it can observe the chunks in between. The adapter
 * therefore bridges the gap: it invokes the streaming model, completes a future from the
 * {@link StreamingChatResponseHandler} the streaming model calls back, and blocks on that future
 * until the complete answer has arrived. Partial chunks are discarded entirely, so a streamed
 * answer is used exactly like a blocking one and none of the engine's surrounding behaviour —
 * tool execution, payload parsing, schema validation, logging, token usage — changes with it.
 * <p>
 * The streaming model calls back on a reactor thread, never on the calling thread, so the adapter
 * rethrows whatever the handler's {@code onError} was given, unrewrapped, when the call fails.
 * That is what keeps a {@link RetriableException} a retriable error, and every other error the
 * error it was before the adapter.
 */
public class StreamingChatModelAdapter implements ChatModel {

    private final OpenAiStreamingChatModel streamingModel;
    private final Duration timeout;

    /**
     * @param streamingModel the streamed chat model the calls are handed to
     * @param timeout        the maximum time one call may take, the same bound the blocking model
     *                       the engine would otherwise use gets from its request timeout
     */
    public StreamingChatModelAdapter(OpenAiStreamingChatModel streamingModel, Duration timeout) {
        this.streamingModel = streamingModel;
        this.timeout = timeout;
    }

    /**
     * The defaults a provider-specific chat model merges into every request it receives — and the
     * ones the adapter's own call path needs — are the streamed model's, so they are the adapter's.
     */
    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return streamingModel.defaultRequestParameters();
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        final CompletableFuture<ChatResponse> answer = new CompletableFuture<>();

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                answer.complete(chatResponse);
            }

            @Override
            public void onError(Throwable throwable) {
                answer.completeExceptionally(throwable);
            }
        });

        return await(answer);
    }

    /**
     * Blocks until the streamed answer has arrived, the call failed, or the call's time is up.
     * <p>
     * The streaming model bounds the transfer of its own, so this waits a bounded time even when
     * neither completion nor an error ever arrives, as it does when the stream is abandoned
     * without a final chunk.
     * </p>
     */
    private ChatResponse await(CompletableFuture<ChatResponse> answer) {
        try {
            return answer.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            answer.cancel(true);
            throw new RetriableException("The streamed response did not complete within "
                    + timeout.toMinutes() + " minute(s)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetriableException("Interrupted while waiting for the streamed response", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new RetriableException("The streamed model call failed: " + cause.getMessage(), cause);
        }
    }
}
