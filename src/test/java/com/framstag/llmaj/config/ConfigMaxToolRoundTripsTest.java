package com.framstag.llmaj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound on the tool-call rounds of one step attempt: its default, its round trip through the
 * config file, and the rejection of a value that would stop the model before it could call a tool.
 */
public class ConfigMaxToolRoundTripsTest {

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

    private Path writeConfigFile(String boundLine) throws IOException {
        Path configFile = tempDir.resolve("config.json");

        Files.writeString(configFile, """
                {
                  "modelProvider": "ollama",
                  "modelURL": "http://localhost:11434",
                  "modelName": "test-model",
                  "projectDirectory": "/tmp/project",
                  "analysisDirectory": "analysis/software-architecture"%s
                }
                """.formatted(boundLine));

        return configFile;
    }

    @Test
    void configWithoutTheKeyUsesTheDefaultOfTen() {
        assertEquals(10, new Config().getMaxToolRoundTrips(),
                "a config that does not carry the key must use the default of 10 tool rounds");
    }

    @Test
    void configuredValueIsLoaded() throws IOException {
        Config config = ConfigLoader.loadFromPath(writeConfigFile(",\n  \"maxToolRoundTrips\": 25"));

        assertEquals(25, config.getMaxToolRoundTrips());
        assertTrue(config.isProvidedInConfigFile("maxToolRoundTrips"));
    }

    @Test
    void valueOfZeroIsRejectedInsteadOfClamped() {
        IOException exception = assertThrows(IOException.class,
                () -> ConfigLoader.loadFromPath(writeConfigFile(",\n  \"maxToolRoundTrips\": 0")),
                "0 rounds would stop the model before it could call a tool, so the value must be rejected");

        assertEquals("File contains structure errors", exception.getMessage());
    }

    @Test
    void valueIsRoundTrippedThroughTheConfigFile() throws IOException {
        Config config = createConfig();
        config.setMaxToolRoundTrips(4);

        ConfigStorer.save(config, tempDir);
        Config loaded = ConfigLoader.loadFromPath(tempDir.resolve("config.json"));

        assertEquals(4, loaded.getMaxToolRoundTrips());
    }

    @Test
    void defaultIsWrittenToTheConfigFile() throws IOException {
        ConfigStorer.save(createConfig(), tempDir);

        String content = Files.readString(tempDir.resolve("config.json"));

        assertTrue(content.contains("\"maxToolRoundTrips\" : 10"),
                "the stored config must carry the default, got:\n" + content);
    }
}
