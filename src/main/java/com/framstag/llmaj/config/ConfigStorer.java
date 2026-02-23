package com.framstag.llmaj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ConfigStorer {

    public static final String CONFIG_FILE_NAME = "config.json";

    public static void save(Config config,
                            Path workingDirectory) throws IOException {
        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

        mapper.writeValue(workingDirectory.resolve(CONFIG_FILE_NAME).toFile(),
                config);
    }
}
