package com.framstag.llmaj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The switch between one blocking transfer of the model's answer and a streamed one: its default,
 * its round trip through the config file, and the reporting of the key as loaded from the file.
 */
public class ConfigUseStreamingTest {

    @TempDir
    Path tempDir;

    private Config createConfig() throws IOException {
        Config config = new Config();
        config.setModelProvider(ModelProvider.OPENAI);
        config.setModelURL(URI.create("http://localhost:8001").toURL());
        config.setModelName("test-model");
        config.setProjectDirectory(Path.of("/tmp/project"));
        config.setAnalysisDirectory(Path.of("analysis/software-architecture"));

        return config;
    }

    private Path writeConfigFile(String streamingLine) throws IOException {
        Path configFile = tempDir.resolve("config.json");

        Files.writeString(configFile, """
                {
                  "modelProvider": "openai",
                  "modelURL": "http://localhost:8001",
                  "modelName": "test-model",
                  "projectDirectory": "/tmp/project",
                  "analysisDirectory": "analysis/software-architecture"%s
                }
                """.formatted(streamingLine));

        return configFile;
    }

    @Test
    void configWithoutTheKeyUsesTheDefaultOfFalse() {
        assertFalse(new Config().isUseStreaming(),
                "a config that does not carry the key must stream nothing, the transfer stays blocking");
    }

    @Test
    void configuredValueIsLoaded() throws IOException {
        Config config = ConfigLoader.loadFromPath(writeConfigFile(",\n  \"useStreaming\": true"));

        assertTrue(config.isUseStreaming());
        assertTrue(config.isProvidedInConfigFile("useStreaming"));
    }

    @Test
    void configuredFalseIsRecognisedAsLoaded() throws IOException {
        Config config = ConfigLoader.loadFromPath(writeConfigFile(",\n  \"useStreaming\": false"));

        assertFalse(config.isUseStreaming());
        assertTrue(config.isProvidedInConfigFile("useStreaming"),
                "an explicit false in the file is a decision, not the built-in default");
    }

    @Test
    void absentKeyIsNotReportedAsLoaded() throws IOException {
        Config config = ConfigLoader.loadFromPath(writeConfigFile(""));

        assertFalse(config.isUseStreaming());
        assertFalse(config.isProvidedInConfigFile("useStreaming"));
    }

    @Test
    void valueIsRoundTrippedThroughTheConfigFile() throws IOException {
        Config config = createConfig();
        config.setUseStreaming(true);

        ConfigStorer.save(config, tempDir);
        Config loaded = ConfigLoader.loadFromPath(tempDir.resolve("config.json"));

        assertTrue(loaded.isUseStreaming());
    }

    @Test
    void defaultIsWrittenToTheConfigFile() throws IOException {
        ConfigStorer.save(createConfig(), tempDir);

        String content = Files.readString(tempDir.resolve("config.json"));

        assertTrue(content.contains("\"useStreaming\" : false"),
                "the stored config must carry the default, got:\n" + content);
    }

    @Test
    void toStringCarriesTheValue() {
        Config config = new Config();
        config.setUseStreaming(true);

        assertEquals(true, config.toString().contains("useStreaming=true"));
    }
}
