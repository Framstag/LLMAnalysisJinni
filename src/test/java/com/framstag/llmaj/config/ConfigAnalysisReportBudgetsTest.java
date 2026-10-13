package com.framstag.llmaj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every budget of the intra-module analyses is a number the reports quote, so it is part of the workspace
 * configuration rather than an implicit constant of the code. A run can then be reproduced, and changed,
 * from its {@code config.json} alone.
 */
class ConfigAnalysisReportBudgetsTest {
    @TempDir
    Path tempDir;

    private Config createConfig() throws Exception {
        Config config = new Config();
        config.setModelURL(URI.create("http://localhost:11434").toURL());
        config.setModelName("qwen2.5:7b");
        config.setProjectDirectory(Path.of("/some/project"));
        config.setAnalysisDirectory(Path.of("analysis/software-architecture"));

        return config;
    }

    @Test
    void everyBudgetIsSeededWithADefault() throws Exception {
        Config config = createConfig();

        for (String property : new String[]{
                Config.DIAGRAM_MAX_NODES_PROPERTY,
                Config.DIAGRAM_MAX_OVERVIEW_NODES_PROPERTY,
                Config.DIAGRAM_MIN_EDGE_WEIGHT_PROPERTY,
                Config.DIAGRAM_MAX_EDGES_PER_NODE_PROPERTY,
                Config.DIAGRAM_MAX_GROUP_DIAGRAMS_PROPERTY,
                Config.DIAGRAM_MIN_CUT_NODE_LIMIT_PROPERTY,
                Config.GOD_CLASS_RANKING_LIMIT_PROPERTY,
                Config.GOD_CLASS_BATCH_RANKING_LIMIT_PROPERTY,
                Config.DOMAIN_LANGUAGE_MINIMUM_CANDIDATE_TERMS_PROPERTY,
                Config.DOMAIN_LANGUAGE_TOP_TERMS_PROPERTY,
                Config.CONTEXT_MAP_LEADING_TERMS_PROPERTY,
                Config.CONTEXT_MAP_MAX_PAIRS_PROPERTY}) {
            assertTrue(config.getProperties().containsKey(property),
                    "a freshly created configuration has to state " + property);
            assertTrue(config.getBudget(property, -1) > 0,
                    property + " has to be a positive number");
        }
    }

    @Test
    void everyBudgetSurvivesAConfigurationRoundTrip() throws Exception {
        Config config = createConfig();
        config.getProperties().put(Config.DIAGRAM_MAX_NODES_PROPERTY, "7");

        ConfigStorer.save(config, tempDir);
        Config loaded = ConfigLoader.loadFromPath(tempDir.resolve("config.json"));

        assertEquals(7, loaded.getBudget(Config.DIAGRAM_MAX_NODES_PROPERTY, 40));
        assertEquals(Config.getBudget(config.getProperties(), Config.DIAGRAM_MIN_CUT_NODE_LIMIT_PROPERTY, 1),
                loaded.getBudget(Config.DIAGRAM_MIN_CUT_NODE_LIMIT_PROPERTY, 1));
        assertEquals("analysis/software-architecture/facts/domain_lexicon.csv",
                loaded.getProperties().get(Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY),
                "the seeded lexicon path is part of the workspace configuration");
        assertEquals("0.5", loaded.getProperties().get(Config.CONTEXT_MAP_UBIQUITOUS_SHARE_PROPERTY),
                "the seeded project-wide share is part of the workspace configuration");
    }

    @Test
    void anUnusableValueFallsBackToTheDefault() throws Exception {
        Config config = createConfig();

        config.getProperties().put(Config.DIAGRAM_MAX_NODES_PROPERTY, "not a number");
        assertEquals(40, config.getBudget(Config.DIAGRAM_MAX_NODES_PROPERTY, 40));

        config.getProperties().put(Config.DIAGRAM_MAX_NODES_PROPERTY, "0");
        assertEquals(40, config.getBudget(Config.DIAGRAM_MAX_NODES_PROPERTY, 40),
                "a budget of zero nodes could not draw anything");

        config.getProperties().put(Config.DIAGRAM_MAX_NODES_PROPERTY, "-5");
        assertEquals(40, config.getBudget(Config.DIAGRAM_MAX_NODES_PROPERTY, 40));

        config.getProperties().remove(Config.DIAGRAM_MAX_NODES_PROPERTY);
        assertEquals(40, config.getBudget(Config.DIAGRAM_MAX_NODES_PROPERTY, 40));
    }
}
