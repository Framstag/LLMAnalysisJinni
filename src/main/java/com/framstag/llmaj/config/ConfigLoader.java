package com.framstag.llmaj.config;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.regex.JoniRegularExpressionFactory;
import com.networknt.schema.serialization.DefaultNodeReader;
import com.networknt.schema.utils.JsonNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConfigLoader {

    public static final String CONFIG_FILE_NAME = "config.json";

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    public static Config loadFromWorkingDirectory(Path workingDirectory) throws IOException {
        Path configPath = workingDirectory.resolve(CONFIG_FILE_NAME);

        return loadFromPath(configPath);
    }

    public static Config loadFromPath(Path configFilePath) throws IOException {
        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

        String configFileContent = Files.readString(configFilePath);

        SchemaRegistryConfig schemaRegistryConfig = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance()).build();

        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft202012(),
                builder -> {
                    builder.nodeReader(DefaultNodeReader.Builder::locationAware);
                    builder.schemaRegistryConfig(schemaRegistryConfig)
                            .schemaIdResolvers(schemaIdResolvers -> schemaIdResolvers
                                    .mapPrefix("https://www.framstag.com/llmanalysisjinni/schema", "classpath:schema"));
                });
        Schema schema = schemaRegistry.getSchema(SchemaLocation.of("https://www.framstag.com/llmanalysisjinni/schema/config-file.json"));

        List<Error> errors = schema.validate(configFileContent, InputFormat.JSON, executionContext ->
                executionContext.executionConfig(executionConfig -> executionConfig.formatAssertionsEnabled(true))
        );

        if (!errors.isEmpty()) {
            logger.error("There were errors in the response schema at '{}'", configFilePath);
            for (Error error : errors) {
                JsonLocation location = JsonNodes.tokenStreamLocationOf(error.getInstanceNode());

                logger.error("ERROR: {},{}: {}", location.getLineNr(),location.getColumnNr(),error.getMessage());
            }

            throw new IOException("File contains structure errors");
        }

        return mapper.readValue(configFileContent, Config.class);
    }
}
