package com.framstag.llmaj.lc4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the tool argument error handling API of the langchain4j version the engine is built against.
 * <p>
 * The engine needs a handler that returns the error text to the model instead of ending the step,
 * and it needs to know whether a handler was configured explicitly. Both are newer than the
 * version this project used before, so they are asserted here rather than left to a compile-time
 * accident.
 */
public class ToolArgumentsErrorHandlerTest {

    private static final ToolExecutionRequest REQUEST = ToolExecutionRequest.builder()
            .id("1")
            .name("some_tool")
            .arguments("{}")
            .build();

    private static ToolErrorContext errorContext() {
        return ToolErrorContext.builder()
                .toolExecutionRequest(REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    @Test
    void errorTextIsHandedToTheModel() {
        ToolArgumentsErrorHandler handler = ToolArgumentsErrorHandler.sendExceptionMessageToLlm();

        ToolErrorHandlerResult result = handler.handle(new IllegalArgumentException("bad argument"), errorContext());

        assertTrue(result.text().contains("bad argument"),
                "the model must be told what was wrong, got: " + result.text());
    }

    @Test
    void configuredHandlerIsReportedAsConfigured() {
        ToolService toolService = new ToolService();

        assertFalse(toolService.hasExplicitArgumentsErrorHandler(),
                "a fresh service must not claim an explicitly configured handler");

        toolService.argumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm());

        assertTrue(toolService.hasExplicitArgumentsErrorHandler(),
                "a configured handler must be visible to the engine");
    }

    @Test
    void defaultHandlerEndsTheInvocation() {
        ToolArgumentsErrorHandler defaultHandler = new ToolService().argumentsErrorHandler();

        assertThrows(RuntimeException.class,
                () -> defaultHandler.handle(new IllegalArgumentException("bad argument"), errorContext()),
                "the default must still end the step, which is why the engine configures its own");
    }

    @Test
    void configuredHandlerIsTheOneThatIsReturned() {
        ToolService toolService = new ToolService();
        ToolArgumentsErrorHandler handler = ToolArgumentsErrorHandler.sendExceptionMessageToLlm();

        toolService.argumentsErrorHandler(handler);

        assertEquals(handler, toolService.argumentsErrorHandler(),
                "the service must hand back the handler it was configured with");
    }
}
