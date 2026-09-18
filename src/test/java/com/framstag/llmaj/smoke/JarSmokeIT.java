package com.framstag.llmaj.smoke;

import com.framstag.llmaj.tasks.TaskDefinition;
import com.framstag.llmaj.tasks.TaskManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the packaged artefact itself. The unit tests run against {@code target/classes}
 * and the full dependency classpath, so they cannot observe that a class only resolved at
 * runtime (logback configurators, XML parser implementations) is missing from the jar.
 * <p>
 * Runs in the {@code integration-test} phase, because the artefact only exists after
 * {@code package}. Every check is offline: the runs use a workspace without configuration
 * or a workspace whose model endpoint is a closed local port, so no model is contacted.
 */
public class JarSmokeIT {

    private static final String JAR_NAME = "LLMAnalysisJinni-jar-with-dependencies.jar";
    private static final String SMOKE_DIRECTORY = "target/jar-smoke";
    private static final String LOG_LEVEL_PATTERN = "\\b(ERROR|WARN)\\b";
    private static final String LOGGER_NAME_MARKER = "AnalyseCmd";
    private static final String EXPECTED_FAILURE_MARKER = "config";
    private static final String[] FACTORY_KEY_PREFIXES = {"XMLInputFactory=", "XMLOutputFactory="};
    private static final String SBOM_PARSE_KEY = "SBOM_PARSE=";
    private static final String SBOM_PARSE_OK = "SBOM_PARSE=OK";
    private static final String SBOM_DEPENDENCIES_KEY = "SBOM_DEPENDENCIES=";
    private static final String DISPLAY_MODE_MARKER = "Display mode:";
    private static final String TASK_START_MARKER = "===> Task:";
    private static final String SERVICE_PROVIDER_MISSING_KEY = "SERVICE_PROVIDER_MISSING=";
    private static final String SERVICE_PROVIDERS_CHECKED_KEY = "SERVICE_PROVIDERS_CHECKED=";
    private static final String PROVIDER_CLASS_TO_STRIP = "com/ctc/wstx/stax/WstxInputFactory.class";
    private static final String PROVIDER_CLASS_NAME_TO_STRIP = "com.ctc.wstx.stax.WstxInputFactory";
    private static final String UNREACHABLE_MODEL_URL = "http://127.0.0.1:1";
    private static final String ANALYSIS_DIRECTORY = "analysis/software-architecture";
    private static final String NOTHING_TO_RUN_MARKER = "Nothing to run:";
    private static final String ALREADY_SUCCESSFUL_MARKER = "already successful";
    private static final String TUI_FRAME_MARKER = "Elapsed:";
    private static final String COMPLETION_SUMMARY_MARKER = "=== Analysis Complete ===";
    private static final String NATIVE_ACCESS_MANIFEST_ATTRIBUTE = "Enable-Native-Access";
    private static final String NATIVE_ACCESS_MANIFEST_VALUE = "ALL-UNNAMED";
    private static final String NATIVE_ACCESS_FLAG = "--enable-native-access=ALL-UNNAMED";
    private static final String NATIVE_ACCESS_WARNING_MARKER = "WARNING: A restricted method";
    private static final long PROCESS_TIMEOUT_MINUTES = 3;

    @Test
    void packagedArtefactReportsConfigurationFailureOnTheConsole() throws Exception {
        Path jar = packagedArtefact();
        Path missingWorkspace = Paths.get("target", "jar-smoke", "workspace-does-not-exist").toAbsolutePath();

        assertFalse(Files.exists(missingWorkspace),
                "test fixture must not exist, otherwise the run could succeed: " + missingWorkspace);

        ProcessResult result = run(javaExecutable(),
                "-jar", jar.toString(),
                "analyse", missingWorkspace.toString());

        assertNotEquals(0, result.exitCode(),
                "a run whose workspace configuration cannot be read must fail\nartefact: " + jar
                        + "\n" + result.output());

        List<String> diagnosticLines = diagnosticLines(result.output());

        assertFalse(diagnosticLines.isEmpty(),
                "no diagnostic line reached the console — all log events were discarded\nartefact: " + jar
                        + "\nexpected a line with a log level and the logger name, got:\n" + result.output());

        boolean failureIsNamed = diagnosticLines.stream()
                .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(EXPECTED_FAILURE_MARKER));

        assertTrue(failureIsNamed,
                "a console diagnostic line must name the failure, got:\n" + result.output());
    }

    @Test
    void packagedArtefactShowsTaskTraceAndProgress() throws Exception {
        Path jar = packagedArtefact();
        Path workspace = writeTraceWorkspace();

        ProcessResult result = run(javaExecutable(),
                "-jar", jar.toString(),
                "analyse", "--execution-trace=true", "--single-step=true", workspace.toString());

        assertTrue(result.output().contains(DISPLAY_MODE_MARKER),
                "the run must report the display mode it uses:\nartefact: " + jar
                        + "\n" + result.output());

        assertTrue(result.output().contains(TASK_START_MARKER),
                "the execution trace must contain a task start line:\nartefact: " + jar
                        + "\n" + result.output());

        assertFalse(result.output().contains(NOTHING_TO_RUN_MARKER),
                "a run with a pending task must not claim that nothing is runnable:\nartefact: " + jar
                        + "\n" + result.output());
    }

    @Test
    void packagedArtefactReportsARunWithoutRunnableTasks() throws Exception {
        Path jar = packagedArtefact();
        Path workspace = writeIdleWorkspace();

        ProcessResult result = run(javaExecutable(),
                "-jar", jar.toString(),
                "analyse", workspace.toString());

        assertEquals(0, result.exitCode(), "a run without runnable tasks must succeed:\nartefact: " + jar
                + "\n" + result.output());
        assertTrue(result.output().contains(NOTHING_TO_RUN_MARKER),
                "a run without runnable tasks must state that nothing is runnable:\nartefact: " + jar
                        + "\n" + result.output());
        assertTrue(result.output().contains(ALREADY_SUCCESSFUL_MARKER),
                "the statement must make clear that the tasks are already successful:\nartefact: " + jar
                        + "\n" + result.output());
        assertFalse(result.output().contains(TUI_FRAME_MARKER),
                "a run without runnable tasks must not paint a TUI frame:\nartefact: " + jar
                        + "\n" + result.output());
        assertFalse(result.output().contains(COMPLETION_SUMMARY_MARKER),
                "a run without runnable tasks must not report task outcomes as if work had been done:\n"
                        + "artefact: " + jar + "\n" + result.output());
    }

    @Test
    void packagedArtefactDeclaresNativeAccessForItsTerminal() throws Exception {
        Path jar = packagedArtefact();

        assertEquals(NATIVE_ACCESS_MANIFEST_VALUE, manifestAttribute(jar, NATIVE_ACCESS_MANIFEST_ATTRIBUTE),
                "the artefact must declare the native access its terminal implementation needs:\nartefact: " + jar);

        // The trace workspace makes the run probe a terminal, which is what loads the native
        // terminal implementation; the idle workspace would return before that.
        Path workspace = writeTraceWorkspace();

        ProcessResult result = run(javaExecutable(),
                "-jar", jar.toString(),
                "analyse", "--single-step=true", workspace.toString());

        assertFalse(result.output().contains(NATIVE_ACCESS_WARNING_MARKER),
                "starting the artefact must not emit a restricted native access warning:\nartefact: " + jar
                        + "\n" + result.output());
    }

    @Test
    void packagedArtefactSupportsXmlFactoriesServiceProvidersAndSbomParsing() throws Exception {
        Path jar = packagedArtefact();
        String classpath = jar + File.pathSeparator
                + Paths.get("target", "test-classes").toAbsolutePath();

        ProcessResult result = run(javaExecutable(),
                NATIVE_ACCESS_FLAG,
                "-cp", classpath,
                JarSmokeProbe.class.getName(), jar.toString());

        assertEquals(0, result.exitCode(), "artefact probe reported failures:\nartefact: " + jar
                + "\n" + result.output());
        assertTrue(reportsFactory(result.output(), FACTORY_KEY_PREFIXES[0]),
                "no XML input factory could be created from the packaged classpath:\nartefact: " + jar
                        + "\n" + result.output());
        assertTrue(reportsFactory(result.output(), FACTORY_KEY_PREFIXES[1]),
                "no XML output factory could be created from the packaged classpath:\nartefact: " + jar
                        + "\n" + result.output());
        assertTrue(result.output().contains(SBOM_PARSE_OK),
                "the packaged artefact could not parse the SBOM document:\nartefact: " + jar
                        + "\n" + result.output());
        assertTrue(readReportedCount(result.output(), SBOM_DEPENDENCIES_KEY) > 0,
                "the parsed SBOM document had no dependencies:\nartefact: " + jar
                        + "\n" + result.output());
        assertFalse(result.output().contains(SERVICE_PROVIDER_MISSING_KEY),
                "the artefact declares service providers that it does not contain:\nartefact: " + jar
                        + "\n" + result.output());
        assertTrue(readReportedCount(result.output(), SERVICE_PROVIDERS_CHECKED_KEY) > 0,
                "no declared service provider was checked, the guard is not effective:\nartefact: " + jar
                        + "\n" + result.output());
    }

    @Test
    void artefactVerificationDetectsAClassMissingFromTheArtefact() throws Exception {
        Path jar = packagedArtefact();
        Path artefactWithoutClass = copyArtefactWithoutEntry(jar, PROVIDER_CLASS_TO_STRIP);
        String classpath = artefactWithoutClass + File.pathSeparator
                + Paths.get("target", "test-classes").toAbsolutePath();

        ProcessResult result = run(javaExecutable(),
                NATIVE_ACCESS_FLAG,
                "-cp", classpath,
                JarSmokeProbe.class.getName(), artefactWithoutClass.toString());

        assertNotEquals(0, result.exitCode(),
                "the verification must fail for an artefact that lost a runtime-loaded class:\n"
                        + "artefact: " + artefactWithoutClass + "\n" + result.output());

        String missingClass = PROVIDER_CLASS_NAME_TO_STRIP;

        assertTrue(result.output().contains(SERVICE_PROVIDER_MISSING_KEY + missingClass),
                "the failure must name the missing capability '" + missingClass + "':\n"
                        + "artefact: " + artefactWithoutClass + "\n" + result.output());
    }

    /**
     * Writes a workspace whose model endpoint is a closed local port, so the run stays offline, and
     * which has no task state yet: every task is pending and the first task is the one that starts.
     */
    private static Path writeTraceWorkspace() throws IOException {
        Path workspace = Paths.get(SMOKE_DIRECTORY, "trace-workspace").toAbsolutePath();

        writeWorkspaceConfiguration(workspace);

        return workspace;
    }

    /**
     * Writes a workspace in which every task of the analysed task list is already successful, so the
     * run has nothing to execute. The task ids are read from the task list itself, so the fixture
     * does not rot when the analysis gains or loses a task.
     */
    private static Path writeIdleWorkspace() throws IOException {
        Path workspace = Paths.get(SMOKE_DIRECTORY, "idle-workspace").toAbsolutePath();

        writeWorkspaceConfiguration(workspace);

        TaskManager taskManager = TaskManager.initializeTasks(Path.of(ANALYSIS_DIRECTORY), workspace, Set.of());

        assertNotNull(taskManager, "could not load the task list from " + ANALYSIS_DIRECTORY);

        for (TaskDefinition task : taskManager.getAllTasks()) {
            if (task.isActive()) {
                taskManager.markTaskAsSuccessful(task);
            }
        }

        return workspace;
    }

    private static void writeWorkspaceConfiguration(Path workspace) throws IOException {
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("config.json"), """
                {
                  "modelProvider": "ollama",
                  "modelURL": "%s",
                  "modelName": "unreachable-for-smoke-test",
                  "nativeJSON": true,
                  "requestTimeout": 1,
                  "projectDirectory": "%s",
                  "analysisDirectory": "%s"
                }
                """.formatted(UNREACHABLE_MODEL_URL, Paths.get("").toAbsolutePath(), ANALYSIS_DIRECTORY));

        Files.deleteIfExists(workspace.resolve("state.json"));
    }

    private static String manifestAttribute(Path jar, String attributeName) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var manifest = jarFile.getManifest();

            return manifest == null ? null : manifest.getMainAttributes().getValue(attributeName);
        }
    }

    private static Path copyArtefactWithoutEntry(Path jar, String entryName) throws IOException {
        Path damagedJar = Paths.get(SMOKE_DIRECTORY, "artefact-without-provider-class.jar").toAbsolutePath();

        Files.createDirectories(damagedJar.getParent());

        try (JarFile jarFile = new JarFile(jar.toFile());
             JarOutputStream target = new JarOutputStream(Files.newOutputStream(damagedJar))) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().equals(entryName)) {
                    continue;
                }

                target.putNextEntry(new JarEntry(entry.getName()));

                if (!entry.isDirectory()) {
                    try (var entryStream = jarFile.getInputStream(entry)) {
                        entryStream.transferTo(target);
                    }
                }

                target.closeEntry();
            }
        }

        return damagedJar;
    }

    private static Path packagedArtefact() {
        Path jar = Paths.get("target", JAR_NAME).toAbsolutePath();

        assertTrue(Files.isRegularFile(jar),
                "packaged artefact is missing, run 'mvn package' before this test: " + jar);

        return jar;
    }

    private static String javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static List<String> diagnosticLines(String output) {
        Pattern logLine = Pattern.compile(".*" + LOG_LEVEL_PATTERN + ".*\\b" + LOGGER_NAME_MARKER + "\\b.*- .*");

        List<String> lines = new ArrayList<>();

        for (String line : output.split("\\R")) {
            if (logLine.matcher(line).matches()) {
                lines.add(line);
            }
        }

        return lines;
    }

    private static boolean reportsFactory(String output, String key) {
        Pattern factoryLine = Pattern.compile(".*" + Pattern.quote(key) + "\\S+.*", Pattern.DOTALL);

        return factoryLine.matcher(output).matches();
    }

    private static int readReportedCount(String output, String key) {
        for (String line : output.split("\\R")) {
            if (line.startsWith(key)) {
                return Integer.parseInt(line.substring(key.length()).trim());
            }
        }

        return 0;
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private static ProcessResult run(String... command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> collectOutput(process, output), "jar-smoke-output");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            reader.join(TimeUnit.SECONDS.toMillis(10));
            throw new AssertionError("process did not finish within " + PROCESS_TIMEOUT_MINUTES
                    + " minutes: " + String.join(" ", command));
        }

        reader.join(TimeUnit.SECONDS.toMillis(10));

        return new ProcessResult(process.exitValue(), output.toString());
    }

    private static void collectOutput(Process process, StringBuilder output) {
        try (var inputStream = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = inputStream.read(buffer)) >= 0) {
                output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            output.append("\n<output could not be read: ").append(e).append(">\n");
        }
    }
}
