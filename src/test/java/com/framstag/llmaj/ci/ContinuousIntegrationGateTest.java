package com.framstag.llmaj.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The verification run validates the specifications of the project with a version of the validation tool
 * that is pinned in the repository. The version is written twice, once for the developer and once for
 * the runner, and these tests keep the two pins equal.
 */
class ContinuousIntegrationGateTest {

    private static final Path MISE_TOML = Path.of("mise.toml");
    private static final Path WORKFLOW = Path.of(".github", "workflows", "ci.yml");
    private static final String TOOL = "@fission-ai/openspec";
    private static final Pattern MISE_PIN =
            Pattern.compile("\"npm:" + Pattern.quote(TOOL) + "\"\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern WORKFLOW_PIN =
            Pattern.compile(Pattern.quote(TOOL) + "@([^\\s\"']+)");

    /**
     * The version of the validation tool, as mise.toml pins it.
     */
    private static String pinnedInMise() throws IOException {
        Matcher matcher = MISE_PIN.matcher(Files.readString(MISE_TOML));
        assertTrue(matcher.find(), "mise.toml does not pin the version of " + TOOL);
        return matcher.group(1);
    }

    /**
     * The version of the validation tool, as the workflow pins it.
     */
    private static String pinnedInWorkflow() throws IOException {
        Matcher matcher = WORKFLOW_PIN.matcher(Files.readString(WORKFLOW));
        assertTrue(matcher.find(), "the workflow does not pin the version of " + TOOL);
        return matcher.group(1);
    }

    @Test
    void theWorkflowAndTheMiseFilePinTheSameVersion() throws IOException {
        assertEquals(pinnedInMise(), pinnedInWorkflow(),
                "the workflow and mise.toml pin different versions of " + TOOL);
    }

    @Test
    void thePinnedVersionIsAVersionAndNotTheNewestOne() throws IOException {
        String version = pinnedInMise();

        assertNotEquals("latest", version, "the version of " + TOOL + " is not pinned");
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"),
                "the pinned version of " + TOOL + " is not a version: " + version);
    }

    @Test
    void theWorkflowValidatesTheSpecifications() throws IOException {
        String workflow = Files.readString(WORKFLOW);

        assertTrue(workflow.contains("validate --all --strict"),
                "the workflow does not run the strict validation of the specifications");
    }
}
