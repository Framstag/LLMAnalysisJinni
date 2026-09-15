package com.framstag.llmaj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigOverridesTest {

    @TempDir
    Path tempDirectory;

    private static Config configWithProvidedProperties(String... providedProperties) {
        Config config = new Config();
        config.setProvidedProperties(Set.of(providedProperties));

        return config;
    }

    @Test
    public void testExplicitOptionBeatsConfigFile() {
        Config config = configWithProvidedProperties("logResponses");
        config.setLogResponses(false);

        new ConfigOverrides(null, true, null, null, null).applyTo(config);

        assertTrue(config.isLogResponses());
    }

    @Test
    public void testConfigFileBeatsBuiltInDefault() {
        Config config = configWithProvidedProperties("logResponses");
        config.setLogResponses(true);

        ConfigOverrides.NONE.applyTo(config);

        assertTrue(config.isLogResponses());
    }

    @Test
    public void testAbsentOptionLeavesConfigurationUntouched() {
        Config config = configWithProvidedProperties("logResponses", "executionTrace");
        config.setLogResponses(true);
        config.setExecutionTrace(true);

        new ConfigOverrides(false, null, null, null, null).applyTo(config);

        assertTrue(config.isLogResponses(), "an option that was not passed must not change the config");
        assertTrue(config.isExecutionTrace(), "an option that was not passed must not change the config");
        assertFalse(config.isLogRequests());
    }

    @Test
    public void testExplicitValueEqualToDefaultStillOverrides() {
        Config config = configWithProvidedProperties("logRequests");
        config.setLogRequests(true);

        new ConfigOverrides(false, null, null, null, null).applyTo(config);

        assertFalse(config.isLogRequests(), "passing the default value explicitly is still an override");
    }

    @Test
    public void testSettingsResolveIndependently() {
        Config config = configWithProvidedProperties("logRequests",
                "logResponses",
                "executionTrace",
                "executionTraceSystem",
                "taskParallelism");
        config.setLogRequests(true);
        config.setLogResponses(true);
        config.setExecutionTrace(true);
        config.setExecutionTraceSystem(true);
        config.setTaskParallelism(8);

        new ConfigOverrides(null, null, null, null, 4).applyTo(config);

        assertEquals(4, config.getTaskParallelism());
        assertTrue(config.isLogRequests());
        assertTrue(config.isLogResponses());
        assertTrue(config.isExecutionTrace());
        assertTrue(config.isExecutionTraceSystem());
    }

    @Test
    public void testResolutionsReportValueAndSource() {
        Config config = configWithProvidedProperties("logResponses", "taskParallelism");
        config.setLogResponses(true);
        config.setTaskParallelism(8);

        ConfigOverrides overrides = new ConfigOverrides(null, false, null, null, null);
        overrides.applyTo(config);

        assertEquals("false", resolutionOf(overrides, config, "logResponses").value());
        assertEquals(ConfigOverrides.Source.COMMAND_LINE,
                resolutionOf(overrides, config, "logResponses").source());

        assertEquals("8", resolutionOf(overrides, config, "taskParallelism").value());
        assertEquals(ConfigOverrides.Source.CONFIG_FILE,
                resolutionOf(overrides, config, "taskParallelism").source());

        assertEquals("false", resolutionOf(overrides, config, "executionTrace").value());
        assertEquals(ConfigOverrides.Source.DEFAULT,
                resolutionOf(overrides, config, "executionTrace").source());
    }

    @Test
    public void testLoaderRecordsProvidedProperties() throws IOException {
        Path configFile = tempDirectory.resolve("config.json");
        Files.writeString(configFile, """
                {
                  "modelProvider": "ollama",
                  "modelURL": "http://localhost:11434",
                  "modelName": "test-model",
                  "projectDirectory": "/tmp/project",
                  "analysisDirectory": "analysis/software-architecture",
                  "logResponses": true,
                  "taskParallelism": 5
                }
                """);

        Config config = ConfigLoader.loadFromPath(configFile);

        assertTrue(config.isProvidedInConfigFile("logResponses"));
        assertTrue(config.isProvidedInConfigFile("taskParallelism"));
        assertFalse(config.isProvidedInConfigFile("logRequests"),
                "a property that the file does not contain must not be reported as provided");
        assertTrue(config.isLogResponses());
        assertEquals(5, config.getTaskParallelism());
    }

    private static ConfigOverrides.Resolution resolutionOf(ConfigOverrides overrides,
                                                          Config config,
                                                          String setting) {
        return overrides.resolutions(config).stream()
                .filter(resolution -> resolution.setting().equals(setting))
                .findFirst()
                .orElseThrow();
    }
}
