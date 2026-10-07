package com.framstag.llmaj.state;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the loop cursor of one loop task execution. The cursor replaced a single field on the
 * {@link StateManager}, which made a second loop task fail with "Loop already started" - the condition
 * that produced the aborted starts and the log flood of the Maven run.
 */
public class StateManagerLoopCursorTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    private static final String STATE_WITH_TWO_MODULES = """
            {"modules":{"modules":[{"name":"first"},{"name":"second"}]},"modules_name":"a string"}
            """;

    @TempDir
    Path workspace;

    private StateManager stateManagerWith(String analysisState) throws IOException {
        Files.writeString(workspace.resolve("analysis.json"), analysisState);

        return StateManager.initializeState(workspace);
    }

    /**
     * Collects the records of one logger, so a test can assert on the diagnostic of a broken loop
     * target without reading the engine log file.
     */
    private static List<String> recordsOf(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(StateManager.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            action.run();

            List<String> messages = new ArrayList<>();

            for (ILoggingEvent event : appender.list) {
                messages.add(event.getFormattedMessage());
            }

            return messages;
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testTwoCursorsOverTheSameArrayCanBeCreated() throws IOException {
        StateManager stateManager = stateManagerWith(STATE_WITH_TWO_MODULES);

        List<String> records = recordsOf(() -> {
            LoopCursor first = stateManager.startLoop("/modules/modules");
            LoopCursor second = stateManager.startLoop("/modules/modules");

            assertNotNull(first, "the first loop task must obtain a cursor");
            assertNotNull(second,
                    "a loop task must not be refused because another loop task is executing");
            assertEquals(2, first.size(), "the cursor must see the entries of the loop target");
            assertEquals(2, second.size(), "each execution gets its own view of the target");
            assertEquals("first", first.at(0).path("name").asText());
            assertEquals("second", first.at(1).path("name").asText());
            assertEquals("/modules/modules", first.getLoopOn(),
                    "the cursor must name the path it was created for");
        });

        assertFalse(records.stream().anyMatch(message -> message.contains("already started")),
                "no execution may be refused because a loop is running, got: " + records);
    }

    @Test
    public void testMissingLoopTargetIsReportedForThatTaskOnly() throws IOException {
        StateManager stateManager = stateManagerWith(STATE_WITH_TWO_MODULES);

        List<String> records = recordsOf(() -> {
            assertNull(stateManager.startLoop("/modules/doesNotExist"),
                    "a loop target that does not exist yields no cursor");

            LoopCursor valid = stateManager.startLoop("/modules/modules");

            assertNotNull(valid,
                    "a broken loop target of one task must not prevent another task from looping");
        });

        assertEquals(1, records.size(),
                "the missing target must be reported exactly once, got: " + records);
        assertTrue(records.getFirst().contains("/modules/doesNotExist"),
                "the report must name the path it could not iterate, got: " + records);
    }

    @Test
    public void testNonArrayLoopTargetIsReportedForThatTaskOnly() throws IOException {
        StateManager stateManager = stateManagerWith(STATE_WITH_TWO_MODULES);

        List<String> records = recordsOf(() -> {
            assertNull(stateManager.startLoop("/modules"),
                    "a loop target that is not an array yields no cursor");

            assertNotNull(stateManager.startLoop("/modules/modules"),
                    "the failure must not affect another loop task");
        });

        assertEquals(1, records.size(), "the broken target must be reported once, got: " + records);
        assertTrue(records.getFirst().contains("/modules"),
                "the report must name the path, got: " + records);
    }

    @Test
    public void testConcurrentWriteIntoTheSameEntryKeepsBothProperties() throws Exception {
        StateManager stateManager = stateManagerWith(STATE_WITH_TWO_MODULES);

        LoopCursor firstTask = stateManager.startLoop("/modules/modules");
        LoopCursor secondTask = stateManager.startLoop("/modules/modules");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);

        try {
            writers.submit(() -> {
                if (!awaitStart(start)) {
                    return;
                }

                for (int index = 0; index < 200; index++) {
                    stateManager.updateLoopState(firstTask, 0, "purpose",
                            mapper.createObjectNode().put("answer", "purpose " + index));
                }
            });

            writers.submit(() -> {
                if (!awaitStart(start)) {
                    return;
                }

                for (int index = 0; index < 200; index++) {
                    stateManager.updateLoopState(secondTask, 0, "architecture",
                            mapper.createObjectNode().put("answer", "architecture " + index));
                }
            });

            start.countDown();
            writers.shutdown();
            assertTrue(writers.awaitTermination(30, TimeUnit.SECONDS),
                    "the concurrent writers must finish");

            stateManager.saveState();

            JsonNode stored = mapper.readTree(workspace.resolve("analysis.json").toFile());
            JsonNode entry = stored.path("modules").path("modules").get(0);

            assertTrue(entry.has("purpose"),
                    "the property of the first loop task must survive the writes of the second");
            assertTrue(entry.has("architecture"),
                    "the property of the second loop task must survive the writes of the first");
            assertEquals("architecture 199", entry.path("architecture").path("answer").asText());
            assertEquals("purpose 199", entry.path("purpose").path("answer").asText());
        } finally {
            writers.shutdownNow();
        }
    }

    private static boolean awaitStart(CountDownLatch start) {
        try {
            start.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    public void testCursorIsIndependentOfTheNextExecution() throws IOException {
        StateManager stateManager = stateManagerWith(STATE_WITH_TWO_MODULES);

        LoopCursor executing = stateManager.startLoop("/modules/modules");
        stateManager.updateLoopState(executing, 0, "purpose",
                mapper.createObjectNode().put("answer", "of the first run"));

        // The first execution ends by dropping its cursor; the next execution obtains its own.
        LoopCursor later = stateManager.startLoop("/modules/modules");

        assertNotNull(later, "a later execution must be able to obtain its own cursor");
        stateManager.updateLoopState(later, 1, "purpose",
                mapper.createObjectNode().put("answer", "of the later run"));

        JsonNode entries = stateManager.getAnalysisState().path("modules").path("modules");

        assertEquals("of the first run", entries.get(0).path("purpose").path("answer").asText());
        assertEquals("of the later run", entries.get(1).path("purpose").path("answer").asText());
    }
}
