package com.framstag.llmaj.lc4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge from the streamed answer back to the one blocking transfer: a success passes the
 * handler's answer through unchanged, a failure keeps the error the streaming model reported, and
 * a stream that never ends degrades to a bounded, retriable failure instead of hanging.
 */
public class StreamingChatModelAdapterTest {

    /**
     * A streaming model whose behaviour is supplied by the test, so the adapter can be observed
     * without a model to answer it.
     */
    static class StubStreamingModel extends OpenAiStreamingChatModel {
        private final BiConsumer<ChatRequest, StreamingChatResponseHandler> behaviour;

        StubStreamingModel(BiConsumer<ChatRequest, StreamingChatResponseHandler> behaviour) {
            super(OpenAiStreamingChatModel.builder()
                    .baseUrl("http://localhost:8001")
                    .apiKey("test")
                    .modelName("stub"));

            this.behaviour = behaviour;
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            behaviour.accept(request, handler);
        }
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("Say hello"))
                .build();
    }

    private static ChatResponse answer(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    @Test
    void streamedSuccessReturnsTheAnswerTheHandlerCompleted() {
        ChatResponse expected = answer("the complete streamed answer");

        StreamingChatModelAdapter adapter = new StreamingChatModelAdapter(
                new StubStreamingModel((request, handler) -> {
                    handler.onPartialResponse("the complete");
                    handler.onPartialResponse(" streamed");
                    handler.onPartialResponse(" answer");
                    handler.onCompleteResponse(expected);
                }),
                Duration.ofMinutes(5));

        ChatResponse actual = adapter.chat(request());

        assertSame(expected, actual, "the answer must be exactly what the streaming handler completed");
        assertEquals("the complete streamed answer", actual.aiMessage().text());
    }

    @Test
    void streamedFailureKeepsTheErrorTheHandlerReported() {
        RetriableException retriable = new RetriableException("429: too many requests");

        StreamingChatModelAdapter adapter = new StreamingChatModelAdapter(
                new StubStreamingModel((request, handler) -> {
                    handler.onPartialResponse("half an answer");
                    handler.onError(retriable);
                }),
                Duration.ofMinutes(5));

        RetriableException thrown = assertThrows(RetriableException.class,
                () -> adapter.chat(request()),
                "the error the streaming handler reported must reach the caller unrewrapped");

        assertSame(retriable, thrown, "the identical error object is what the retrier classifies");
    }

    @Test
    void errorWrappingKeepsTheOriginalErrorInTheCauseChain() {
        Exception checkedError = new Exception("something the reactor surfaced");

        StreamingChatModelAdapter adapter = new StreamingChatModelAdapter(
                new StubStreamingModel((request, handler) -> handler.onError(checkedError)),
                Duration.ofMinutes(5));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> adapter.chat(request()));

        assertTrue(thrown instanceof RetriableException,
                "an error that is not a runtime exception is a streamed call that failed: " + thrown.getClass());
        assertSame(checkedError, thrown.getCause());
    }

    @Test
    void abandonedStreamFailsAfterTheBoundedTimeInsteadOfHanging() {
        StreamingChatModelAdapter adapter = new StreamingChatModelAdapter(
                new StubStreamingModel((request, handler) -> {
                    // no callback at all: the stream is abandoned without a final chunk or an error
                }),
                Duration.ofMillis(300));

        long started = System.nanoTime();

        RetriableException thrown = assertThrows(RetriableException.class,
                () -> adapter.chat(request()),
                "an abandoned stream must fail as a matter of time and be retriable");

        long waitedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertTrue(thrown.getMessage().contains("did not complete"), "got: " + thrown.getMessage());
        assertTrue(waitedMillis >= 300, "the bound must actually have been waited out, waited: " + waitedMillis);
        assertTrue(waitedMillis < 2000, "the call must be stopped at the bound, waited: " + waitedMillis);
    }

    @Test
    void theRequestIsPassedToTheStreamingModelUnchanged() {
        ChatRequest request = request();
        ChatRequest[] received = new ChatRequest[1];

        StreamingChatModelAdapter adapter = new StreamingChatModelAdapter(
                new StubStreamingModel((sent, handler) -> {
                    received[0] = sent;
                    handler.onCompleteResponse(answer("ok"));
                }),
                Duration.ofMinutes(5));

        adapter.chat(request);

        assertArrayEquals(request.messages().toArray(), received[0].messages().toArray());
    }

    @Test
    void theStreamingModelReceivesItsProviderSpecificMergedParameters() {
        ChatRequest[] received = new ChatRequest[1];

        StreamingChatModelAdapter adapter = new StreamingChatModelAdapter(
                new StubStreamingModel((sent, handler) -> {
                    received[0] = sent;
                    handler.onCompleteResponse(answer("ok"));
                }),
                Duration.ofMinutes(5));

        adapter.chat(ChatRequest.builder()
                .messages(UserMessage.from("Say hello"))
                .parameters(ChatRequestParameters.builder().build())
                .build());

        assertTrue(received[0].parameters() instanceof OpenAiChatRequestParameters,
                "the streamed model merges its own defaults into the request it receives and "
                + "expects its provider-specific parameters type back: got "
                        + received[0].parameters().getClass());
    }
}
