package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;

public record TaskState(String taskId,
                        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                        ZonedDateTime lastExecution,
                        boolean success) {

    public static TaskState[] loadTaskState(Path path) throws IOException {
        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.findAndRegisterModules();

        YAMLParser parser = yamlFactory.createParser(path.toFile());
        return mapper.readValue(parser,TaskState[].class);
    }
}
