package com.framstag.llmaj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ConfigLoader {

    public static final String CONFIG_FILE_NAME = "config.json";

    public static Config load(Path workingDirectory) throws IOException {
        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

        return mapper.readValue(workingDirectory.resolve(CONFIG_FILE_NAME).toFile(), Config.class);
    }
}
