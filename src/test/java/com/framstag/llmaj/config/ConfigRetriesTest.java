package com.framstag.llmaj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The retry budget of a task step: its default, its round trip through the config file, and the
 * rejection of a value that would allow no attempt at all.
 */
public class ConfigRetriesTest {

    @TempDir
    Path tempDir;

    private Config createConfig() throws IOException {
        Config config = new Config();
        config.setModelURL(URI.create("http://localhost:11434").toURL());
        config.setModelName("qwen2.5:7b");
        config.setProjectDirectory(Path.of("/some/project"));
        config.setAnalysisDirectory(Path.of("analysis/software-architecture"));

        return config;
    }

    private Path writeConfigFile(String retriesLine) throws IOException {
        Path configFile = tempDir.resolve("config.json");

        Files.writeString(configFile, """
                {
                  "modelProvider": "ollama",
                  "modelURL": "http://localhost:11434",
                  "modelName": "test-model",
                  "projectDirectory": "/tmp/project",
                  "analysisDirectory": "analysis/software-architecture"%s
                }
                """.formatted(retriesLine));

        return configFile;
    }

    @Test
    void configWithoutTheKeyUsesTheDefaultOfThree() {
        assertEquals(3, new Config().getRetries(),
                "a config that does not carry the key must use the default of 3 attempts");
    }

    @Test
    void configuredValueIsLoaded() throws IOException {
        Config config = ConfigLoader.loadFromPath(writeConfigFile(",\n  \"retries\": 5"));

        assertEquals(5, config.getRetries());
        assertTrue(config.isProvidedInConfigFile("retries"));
    }

    @Test
    void valueOfZeroIsRejectedInsteadOfClamped() {
        IOException exception = assertThrows(IOException.class,
                () -> ConfigLoader.loadFromPath(writeConfigFile(",\n  \"retries\": 0")),
                "0 attempts would allow no attempt at all, so the value must be rejected");

        assertEquals("File contains structure errors", exception.getMessage());
    }

    @Test
    void valueIsRoundTrippedThroughTheConfigFile() throws IOException {
        Config config = createConfig();
        config.setRetries(7);

        ConfigStorer.save(config, tempDir);
        Config loaded = ConfigLoader.loadFromPath(tempDir.resolve("config.json"));

        assertEquals(7, loaded.getRetries());
    }

    @Test
    void defaultIsWrittenToTheConfigFile() throws IOException {
        ConfigStorer.save(createConfig(), tempDir);

        String content = Files.readString(tempDir.resolve("config.json"));

        assertTrue(content.contains("\"retries\" : 3"),
                "the stored config must carry the default, got:\n" + content);
    }
}
