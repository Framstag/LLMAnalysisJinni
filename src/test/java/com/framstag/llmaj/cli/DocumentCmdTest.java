package com.framstag.llmaj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The document command renders the template named by {@code -t}/{@code --template}. The name is given
 * without the {@code .hbs} suffix and is looked up in the {@code documentation/} directory of the
 * analysis directory of the workspace, and it changes neither the analysis state that is rendered nor
 * the name of the output file.
 */
public class DocumentCmdTest {

    private static final String DEFAULT_TEMPLATE = "Default document of {{projectSummary.name}}\n";
    private static final String ALTERNATE_TEMPLATE = "Deutsches Dokument über {{projectSummary.name}}\n";
    private static final String ANALYSIS_STATE = """
            {
              "projectSummary": {
                "name": "Jinni"
              }
            }
            """;

    @TempDir
    Path tempDir;

    private Path workspace;

    /**
     * Writes a workspace whose analysis directory holds the default template and one alternate template.
     * The workspace configuration names the analysis directory absolutely, because the command resolves
     * the template directory from it and not from the working directory.
     */
    private Path writeWorkspace() throws Exception {
        Path analysisDirectory = tempDir.resolve("analysis");

        Files.createDirectories(analysisDirectory.resolve("documentation"));
        Files.writeString(analysisDirectory.resolve("documentation").resolve("Documentation.adoc.hbs"),
                DEFAULT_TEMPLATE);
        Files.writeString(analysisDirectory.resolve("documentation").resolve("Documentation_German.adoc.hbs"),
                ALTERNATE_TEMPLATE);

        workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        Files.writeString(workspace.resolve("config.json"), """
                {
                  "modelProvider": "ollama",
                  "modelURL": "http://localhost:11434",
                  "modelName": "test-model",
                  "projectDirectory": ".",
                  "analysisDirectory": "%s"
                }
                """.formatted(analysisDirectory.toAbsolutePath()));

        Files.writeString(workspace.resolve("analysis.json"), ANALYSIS_STATE);

        return workspace;
    }

    /**
     * Parses the arguments of the command, with the workspace as the only parameter, so that the test
     * observes the value the option carries after the command line was read.
     */
    private DocumentCmd parse(String... arguments) throws Exception {
        DocumentCmd cmd = new DocumentCmd();

        String[] argumentsWithWorkingDirectory = Arrays.copyOf(arguments, arguments.length + 1);
        argumentsWithWorkingDirectory[arguments.length] = writeWorkspace().toString();

        new CommandLine(cmd).parseArgs(argumentsWithWorkingDirectory);

        return cmd;
    }

    private String render(String... arguments) throws Exception {
        DocumentCmd cmd = parse(arguments);

        assertEquals(0, cmd.call(), "the document command must succeed");

        return Files.readString(workspace.resolve("Documentation.adoc"));
    }

    @Test
    public void theTemplateNameDefaultsToThePreviouslyHardcodedTemplate() throws Exception {
        assertEquals("Documentation.adoc", parse().templateName,
                "an invocation without the option must render the template that was hardcoded before");
    }

    @Test
    public void theShortAndTheLongOptionNameTheTemplate() throws Exception {
        assertEquals("Documentation_German.adoc", parse("-t", "Documentation_German.adoc").templateName);
        assertEquals("Documentation_German.adoc", parse("--template", "Documentation_German.adoc").templateName);
    }

    @Test
    public void theDefaultTemplateRendersWithoutTheOption() throws Exception {
        String document = render();

        assertEquals(DEFAULT_TEMPLATE.replace("{{projectSummary.name}}", "Jinni"), document,
                "the default run must render Documentation.adoc.hbs as before");
    }

    @Test
    public void theNamedTemplateRenders() throws Exception {
        String document = render("--template", "Documentation_German.adoc");

        assertEquals(ALTERNATE_TEMPLATE.replace("{{projectSummary.name}}", "Jinni"), document,
                "the named template must be the one that is rendered");
    }

    @Test
    public void theNamedTemplateKeepsTheOutputFileName() throws Exception {
        render("--template", "Documentation_German.adoc");

        assertTrue(Files.exists(workspace.resolve("Documentation.adoc")),
                "the document must be written to Documentation<postfix>");
        assertFalse(Files.exists(workspace.resolve("Documentation_German.adoc")),
                "the template name must not become the output file name");
    }

    @Test
    public void theDocumentPostfixStillAppliesToTheNamedTemplate() throws Exception {
        DocumentCmd cmd = parse("--template", "Documentation_German.adoc", "--document-postfix", ".txt");

        assertEquals(0, cmd.call());

        assertEquals(ALTERNATE_TEMPLATE.replace("{{projectSummary.name}}", "Jinni"),
                Files.readString(workspace.resolve("Documentation.txt")));
    }

    @Test
    public void anUnknownTemplateNameFailsAndWritesNoDocument() throws Exception {
        DocumentCmd cmd = parse("--template", "NoSuchTemplate.adoc");

        assertThrows(Exception.class, cmd::call,
                "a name that matches no template must fail with the template loader's error");
        assertFalse(Files.exists(workspace.resolve("Documentation.adoc")),
                "a failed render must write no document");
    }
}