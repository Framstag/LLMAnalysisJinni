package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.ToolFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entry point tool counts the classes a caller can reach a module through. The counts are the evidence a
 * modernisation anchor rests on, so they have to be exact: a method named main that is not a main method has
 * to be told apart from one that is, and a module without a report is a gap that has to be named rather than
 * passed over.
 */
class EntryPointToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name, boolean production, boolean generated, List<Clazz> classes) {
        return new BuildUnit(name, production, generated, List.of(), List.of(), classes);
    }

    private static Method method(String name, String descriptor, boolean isStatic) {
        Method method = new Method(name, descriptor);
        method.setStatic(isStatic);

        return method;
    }

    private static Clazz classWithMainMethod(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(method("main", "main([Ljava/lang/String;)V", true));

        return clazz;
    }

    private static Clazz classWithInstanceMethodNamedMain(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(method("main", "main([Ljava/lang/String;)V", false));

        return clazz;
    }

    private static Clazz classWithStaticMethodNamedMainWithResult(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(method("main", "main()I", true));

        return clazz;
    }

    private static Clazz classWithAnnotationAndInterface(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addAnnotation(new Annotation("Controller", null));
        clazz.addInterface("demo.web.Handler");
        // The report of a class that is read from source and from bytecode can list the same interface
        // twice. The class implements it once, so the tool reports it once.
        clazz.addInterface("demo.web.Handler");

        return clazz;
    }

    /**
     * The app module holds a class with a main method, a class with a method named main that is not one and a
     * class with a static method named main that returns a value.
     */
    private static Module appModule() {
        Package pck = new Package("demo.app");

        pck.addBuildUnit(buildUnit("demo.app.Application", true, false,
                List.of(classWithMainMethod("demo.app.Application"))));

        pck.addBuildUnit(buildUnit("demo.app.InstanceMain", true, false,
                List.of(classWithInstanceMethodNamedMain("demo.app.InstanceMain"))));

        pck.addBuildUnit(buildUnit("demo.app.StaticMainWithResult", true, false,
                List.of(classWithStaticMethodNamedMainWithResult("demo.app.StaticMainWithResult"))));

        pck.addBuildUnit(buildUnit("demo.app.ApplicationTest", false, false,
                List.of(classWithMainMethod("demo.app.ApplicationTest"))));

        pck.addBuildUnit(buildUnit("demo.app.GeneratedLauncher", true, true,
                List.of(classWithMainMethod("demo.app.GeneratedLauncher"))));

        Module module = new Module("app");
        module.addPackage(pck);

        return module;
    }

    /** The web module holds a class that carries an annotation and implements an interface. */
    private static Module webModule() {
        Package pck = new Package("demo.web");

        pck.addBuildUnit(buildUnit("demo.web.WebController", true, false,
                List.of(classWithAnnotationAndInterface("demo.web.WebController"))));

        Module module = new Module("web");
        module.addPackage(pck);

        return module;
    }

    private void writeReport(String moduleName, Module module) throws IOException {
        Path reportDirectory = tempDir.resolve("Java");
        Files.createDirectories(reportDirectory);
        objectMapper.writeValue(reportDirectory.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private AnalysisContext contextOf(String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            languages.addObject().put("name", "Java").put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir, Map.of(), state);
    }

    private EntryPointTool entryPointTool(String... moduleNames) throws IOException {
        writeReport("app", appModule());
        writeReport("web", webModule());

        return new EntryPointTool(contextOf(moduleNames));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> candidatesOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("entryPoints");
    }

    private static Map<String, Object> candidateOf(Map<String, Object> result, String className) {
        return candidatesOf(result).stream()
                .filter(candidate -> className.equals(candidate.get("className")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no candidate for " + className
                        + ", got: " + result.get("entryPoints")));
    }

    @Test
    void aClassWithAStaticVoidMainIsReportedAsCarryingAMainMethod() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web").getEntryPoints();

        Map<String, Object> application = candidateOf(result, "demo.app.Application");

        assertEquals("app", application.get("module"));
        assertEquals("production", application.get("codeCategory"));
        assertEquals(true, application.get("hasMainMethod"));
    }

    @Test
    void anInstanceMethodNamedMainIsNoMainMethod() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web").getEntryPoints();

        Map<String, Object> instanceMain = candidateOf(result, "demo.app.InstanceMain");

        assertEquals(false, instanceMain.get("hasMainMethod"),
                "a method named main that is not static is no main method");
    }

    @Test
    void aStaticMethodNamedMainWithAResultIsNoMainMethod() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web").getEntryPoints();

        Map<String, Object> staticMain = candidateOf(result, "demo.app.StaticMainWithResult");

        assertEquals(false, staticMain.get("hasMainMethod"),
                "a static method named main that returns a value is no main method");
    }

    @Test
    void theCodeCategoryOfTheClassIsReported() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web").getEntryPoints();

        assertEquals("production", candidateOf(result, "demo.app.Application").get("codeCategory"));
        assertEquals("test", candidateOf(result, "demo.app.ApplicationTest").get("codeCategory"));
        assertEquals("generated", candidateOf(result, "demo.app.GeneratedLauncher").get("codeCategory"));
    }

    @Test
    void theAnnotationsAndTheInterfacesAreReportedWithTheClass() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web").getEntryPoints();

        Map<String, Object> controller = candidateOf(result, "demo.web.WebController");

        assertEquals(List.of("Controller"), controller.get("annotations"));
        assertEquals(List.of("demo.web.Handler"), controller.get("interfaces"),
                "an interface the report lists twice is reported once");
        assertEquals("web", controller.get("module"));
    }

    @Test
    void aModuleWithoutAReportIsSkippedAndNamed() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web", "missing").getEntryPoints();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");

        assertEquals(1, skipped.size());
        assertEquals("missing", skipped.getFirst().get("moduleName"));
        assertTrue(((String) skipped.getFirst().get("reason")).contains("No raw Java module report"),
                skipped.toString());

        assertFalse(candidatesOf(result).stream()
                        .anyMatch(candidate -> "missing".equals(candidate.get("module"))),
                "a module without a report holds no row");
    }

    @Test
    void theCandidatesAreOrderedByModuleAndByClassName() throws Exception {
        Map<String, Object> result = entryPointTool("app", "web").getEntryPoints();

        assertEquals(List.of("demo.app.Application",
                        "demo.app.ApplicationTest",
                        "demo.app.GeneratedLauncher",
                        "demo.app.InstanceMain",
                        "demo.app.StaticMainWithResult",
                        "demo.web.WebController"),
                candidatesOf(result).stream().map(candidate -> candidate.get("className")).toList());
    }

    @Test
    void twoCallsReturnTheSameOrder() throws Exception {
        EntryPointTool tool = entryPointTool("app", "web");

        assertEquals(tool.getEntryPoints().get("entryPoints"), tool.getEntryPoints().get("entryPoints"),
                "the counting needs no seed and no parameter, so two calls agree");
    }

    @Test
    void theEntryPointEvidenceIsWrittenAsACsvReport() throws Exception {
        entryPointTool("app", "web").getEntryPoints();

        List<String> lines = Files.readAllLines(tempDir.resolve(EntryPointReports.REPORT_TYPE)
                .resolve("EntryPoints.csv"));

        assertEquals("ClassName,Module,CodeCategory,Annotations,Interfaces", lines.getFirst());
        assertEquals(4, lines.size() - 1,
                "one row per class that carries a main method, an annotation or an implemented interface");

        assertEquals("demo.app.Application,app,production,,", lines.get(1));
        assertEquals("demo.app.ApplicationTest,app,test,,", lines.get(2));
        assertEquals("demo.app.GeneratedLauncher,app,generated,,", lines.get(3));
        assertEquals("demo.web.WebController,web,production,Controller,demo.web.Handler", lines.get(4));
    }

    @Test
    void theToolIsRegisteredInTheToolFactory() {
        List<Object> tools = ToolFactory.getToolInstanceList(contextOf("app"));

        assertTrue(tools.stream().anyMatch(tool -> tool instanceof EntryPointTool),
                "the entry point tool has to be part of the tool list of the engine");
    }

    @Test
    void theToolIsNamedInTheJavaFamilyAndNamesItsParameters() throws Exception {
        java.lang.reflect.Method method = EntryPointTool.class.getMethod("getEntryPoints");

        Tool tool = method.getAnnotation(Tool.class);

        assertNotNull(tool, "the entry point tool has to be a tool of the engine");
        assertEquals("java_get_entry_points", tool.name());

        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            P description = parameter.getAnnotation(P.class);

            assertNotNull(description, "the parameter " + parameter.getName() + " has to be described");
            assertFalse(description.value().isBlank(), "the description of " + parameter.getName()
                    + " must not be empty");
        }
    }
}
