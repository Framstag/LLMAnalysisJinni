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

    JsonNode           loopPos;

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

    public int getLoopArraySize() {
        if (loopPos == null) {
            return 0;
        }

        return loopPos.size();
    }

    public JsonNode loopAtIndex(int index) {
        return loopPos.get(index);
    }

    public boolean startLoop(String loopOn) {
        if (loopPos != null) {
            logger.error("Loop already started");
            return false;
        }

        loopPos = analysisState.at(loopOn);

        if (loopPos.isNull()) {
            logger.error("Cannot loop on '{}', target does not exist", loopOn);
            loopPos = null;
            return false;
        }

        if (!loopPos.isArray()) {
            logger.error("Cannot loop on '{}', since it is not an array", loopOn);
            loopPos = null;
            return false;
        }

        // No sequential iteration state needed (parallel loop execution)

        return true;
    }


    public void updateLoopState(int index, String path, JsonNode value) {
        ((ObjectNode) loopPos.get(index)).set(path, value);
    }

    public void endLoop() {
        if (loopPos == null) {
            logger.error("Not in loop");
            return;
        }

        loopPos = null;
        analysisState.remove("loopIndex");

    }

    public void updateState(String path, JsonNode value) {
        analysisState.set(path, value);
    }

    public synchronized void saveState() {
        Path stateFilePath=getStateFilePath(workingDirectory);
        logger.info("Writing current analysis state  to '{}'...",stateFilePath);
        writeStateToFile(analysisState, stateFilePath);
    }
}
