package com.framstag.llmaj.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public class StateManager {
    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);
    private static final ObjectMapper mapper;

    final Path         workingDirectory;
    final ObjectNode   analysisState;

    JsonNode           loopPos;
    Iterator<JsonNode> loopIterator;
    int                loopIndex;
    JsonNode           loopValue;

    static {
        mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
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

    public Object getStateObject() {
        return new JsonNodeModelWrapper(analysisState);
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

        loopIndex = -1;
        loopIterator = loopPos.iterator();

        return true;
    }

    public boolean canLoop() {
        if (loopPos == null) {
            logger.error("Not in loop");
            return false;
        }

        return loopIterator.hasNext();
    }

    public void loopNext() {
        if (loopPos == null) {
            logger.error("Not in loop");
            return;
        }

        if (loopValue == null) {
            loopIndex = 0;
        }
        else {
            loopIndex++;
        }

        analysisState.put("loopIndex",loopIndex);
        loopValue = loopIterator.next();
    }

    public int getLoopIndex() {
        if (loopPos == null) {
            logger.error("Not in loop");
            return 0;
        }

        return loopIndex;
    }

    public void updateLoopState(String path,JsonNode value) {
        ((ObjectNode)loopValue).set(path, value);
    }

    public void endLoop() {
        if (loopPos == null) {
            logger.error("Not in loop");
            return;
        }

        loopPos = null;
        loopIterator = null;
        loopValue = null;
        analysisState.remove("loopIndex");

    }

    public void updateState(String path, JsonNode value) {
        analysisState.set(path, value);
    }

    public void saveState() {
        Path stateFilePath=getStateFilePath(workingDirectory);
        logger.info("Writing current analysis state  to '{}'...",stateFilePath);
        writeStateToFile(analysisState, stateFilePath);
    }
}
