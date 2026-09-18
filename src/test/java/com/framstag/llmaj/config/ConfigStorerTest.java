package com.framstag.llmaj.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigStorerTest {

    @TempDir
    Path tempDir;

    private Config createConfigWithoutApiKey() throws MalformedURLException {
        Config config = new Config();
        config.setModelURL(URI.create("http://localhost:11434").toURL());
        config.setModelName("qwen2.5:7b");
        config.setProjectDirectory(Path.of("/some/project"));
        config.setAnalysisDirectory(Path.of("analysis/software-architecture"));
        return config;
    }

    @Test
    void saveOmitsNullApiKey() throws IOException {
        ConfigStorer.save(createConfigWithoutApiKey(), tempDir);

        String content = Files.readString(tempDir.resolve("config.json"));

        Assertions.assertFalse(content.contains("apiKey"),
                "config.json must not contain an apiKey field when it is null");
    }

    @Test
    void savedConfigWithoutApiKeyLoadsSuccessfully() throws IOException {
        ConfigStorer.save(createConfigWithoutApiKey(), tempDir);

        Config loaded = ConfigLoader.loadFromPath(tempDir.resolve("config.json"));

        Assertions.assertNotNull(loaded);
        Assertions.assertNull(loaded.getApiKey());
        Assertions.assertEquals("qwen2.5:7b", loaded.getModelName());
    }

    @Test
    void saveKeepsApiKeyWhenSet() throws IOException {
        Config config = createConfigWithoutApiKey();
        config.setApiKey("secret-key");

        ConfigStorer.save(config, tempDir);

        String content = Files.readString(tempDir.resolve("config.json"));

        Assertions.assertTrue(content.contains("secret-key"));
    }
}
