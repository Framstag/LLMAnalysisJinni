package com.framstag.llmaj.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ConfigStorer {

    public static final String CONFIG_FILE_NAME = "config.json";

    public static void save(Config config,
                            Path workingDirectory) throws IOException {
        // Serialize with NON_NULL so optional fields with null values (e.g. apiKey when
        // no --apiKey was given at workspace init) are omitted instead of written as null.
        // The config-file schema types apiKey as string, so a null value would fail
        // validation on the next load. See fix-init-config-null-apikey change.
        ObjectMapper mapper = JsonMapper.builder()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();

        mapper.writeValue(workingDirectory.resolve(CONFIG_FILE_NAME).toFile(),
                config);
    }
}
