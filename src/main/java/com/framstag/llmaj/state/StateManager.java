package com.framstag.llmaj.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class StateManager {
    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);
    private static final ObjectMapper mapper;

    final Path         workingDirectory;
    final ObjectNode   analysisState;

    static {
        mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
    }

    private StateManager(Path workingDirectory,
                         ObjectNode analysisState) {
        this.workingDirectory = workingDirectory;
        this.analysisState = analysisState;
    }

    private static Path getStateFilePath(Path workingDirectory) {
        return workingDirectory.resolve("analysis.json");
    }

    private static JsonNode readStateFromFile(Path path) {
        try {
            return StateManager.mapper.readTree(path.toFile());
        } catch (IOException e) {
            logger.error("Exception while writing result to file", e);
        }

        return null;
    }

    private static void writeStateToFile(JsonNode result, Path path) {
        try {
            File file = path.toFile();
            StateManager.mapper.writerWithDefaultPrettyPrinter().writeValue(file, result);
        } catch (IOException e) {
            logger.error("Exception while writing result to file", e);
        }
    }

    public static StateManager initializeState(Path workingDirectory) {
        ObjectNode analysisState = mapper.createObjectNode();

        Path stateFilePath = getStateFilePath(workingDirectory);
        File stateFile = stateFilePath.toFile();

        if (stateFile.exists() && stateFile.isFile()) {
            logger.info("Loading current analysis state from '{}'...", stateFilePath);

            JsonNode fileContent = readStateFromFile(stateFilePath);

            if (fileContent instanceof ObjectNode) {
                analysisState = (ObjectNode) fileContent;
            } else {
                logger.error("Analyse state is not an Json Object, ignore content, continue with empty state");
            }
        }

        return new StateManager(workingDirectory, analysisState);
    }

    public ObjectNode getAnalysisState() {
        return analysisState;
    }

    public Object getStateObject() {
        return new JsonNodeModelWrapper(analysisState);
    }

    /**
     * Creates the loop cursor of one loop task execution, or returns null when the requested loop
     * target cannot be iterated. The cursor belongs to the caller's execution only: the state manager
     * keeps no loop state, so a failure here affects that one task and no other.
     *
     * @param loopOn JSON path of the array to iterate
     * @return the cursor of the execution, or null when the target does not exist or is not an array
     */
    public synchronized LoopCursor startLoop(String loopOn) {
        JsonNode loopPos = analysisState.at(loopOn);

        if (loopPos.isNull()) {
            logger.error("Cannot loop on '{}', target does not exist", loopOn);
            return null;
        }

        if (!loopPos.isArray()) {
            logger.error("Cannot loop on '{}', since it is not an array", loopOn);
            return null;
        }

        return new LoopCursor(loopOn, loopPos);
    }

    /**
     * Stores a result in the entry the cursor points at. Serialized against every other state
     * mutation, because two loop tasks can write different properties of the same entry.
     */
    public synchronized void updateLoopState(LoopCursor cursor, int index, String path, JsonNode value) {
        ((ObjectNode) cursor.at(index)).set(path, value);
    }

    public synchronized void updateState(String path, JsonNode value) {
        analysisState.set(path, value);
    }

    public synchronized void saveState() {
        Path stateFilePath=getStateFilePath(workingDirectory);
        logger.info("Writing current analysis state  to '{}'...",stateFilePath);
        writeStateToFile(analysisState, stateFilePath);
    }
}
