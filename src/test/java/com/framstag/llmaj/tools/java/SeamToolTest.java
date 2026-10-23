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
 * The seam tool counts the interfaces of a project with the classes that implement them and the modules that
 * import them. The counts are the evidence a modernisation anchor rests on, so they have to be exact: an
 * interface nobody implements is a fact worth reporting, and a module without a report is a gap that has to be
 * named rather than passed over.
 */
class SeamToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name,
                                       List<String> imports,
                                       List<ClassReference> references,
                                       List<Clazz> classes) {
        return new BuildUnit(name, true, false, imports, references, classes);
    }

    /** The report records the interface flag, so the fixture marks the class as an interface. */
    private static Clazz interfaceClass(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setInterface(true);

        return clazz;
    }

    /**
     * An interface that declares a default method and a constant. The report drops the abstract methods of an
     * interface, so such an interface holds a method and a field of its own and is no longer read from the
     * shape of the class.
     */
    private static Clazz interfaceWithDefaultMethodAndConstant(String name) {
        Clazz clazz = interfaceClass(name);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(new Method("greet", "()V"));
        clazz.addField(new Field("DEFAULT", "java.lang.String", MethodVisibility.PUBLIC, true, true));

        return clazz;
    }

    private static Clazz classWithInterface(String name, String... interfaces) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(new Method("run", "()V"));

        for (String implemented : interfaces) {
            clazz.addInterface(implemented);
        }

        return clazz;
    }

    /**
     * The core module declares two interfaces: a port with two implementors and a consumer, and an interface
     * nobody implements.
     */
    private static Module coreModule() {
        Package pck = new Package("demo.core");

        pck.addBuildUnit(buildUnit("demo.core.Port",
                List.of(),
                List.of(),
                List.of(interfaceClass("demo.core.Port"))));

        pck.addBuildUnit(buildUnit("demo.core.Impl1",
                List.of("demo.core.Port"),
                List.of(new ClassReference("demo.core.Port", 0, 0, true)),
                List.of(classWithInterface("demo.core.Impl1", "demo.core.Port"))));

        pck.addBuildUnit(buildUnit("demo.core.Impl2",
                List.of("demo.core.Port"),
                List.of(new ClassReference("demo.core.Port", 0, 0, true)),
                List.of(classWithInterface("demo.core.Impl2", "demo.core.Port"))));

        pck.addBuildUnit(buildUnit("demo.core.Service",
                List.of("demo.core.Port"),
                List.of(new ClassReference("demo.core.Port", 1, 2, false)),
                List.of(classWithInterface("demo.core.Service"))));

        pck.addBuildUnit(buildUnit("demo.core.Unused",
                List.of(),
                List.of(),
                List.of(interfaceClass("demo.core.Unused"))));

        pck.addBuildUnit(buildUnit("demo.core.Single",
                List.of(),
                List.of(),
                List.of(interfaceClass("demo.core.Single"))));

        pck.addBuildUnit(buildUnit("demo.core.SingleImpl",
                List.of("demo.core.Single"),
                List.of(new ClassReference("demo.core.Single", 0, 0, true)),
                List.of(classWithInterface("demo.core.SingleImpl", "demo.core.Single"))));

        pck.addBuildUnit(buildUnit("demo.core.WithDefault",
                List.of(),
                List.of(),
                List.of(interfaceWithDefaultMethodAndConstant("demo.core.WithDefault"))));

        Module module = new Module("core");
        module.addPackage(pck);

        return module;
    }

    /** The web module imports the port of the core module. */
    private static Module webModule() {
        Package pck = new Package("demo.web");

        pck.addBuildUnit(buildUnit("demo.web.WebController",
                List.of("demo.core.Port"),
                List.of(new ClassReference("demo.core.Port", 1, 2, false)),
                List.of(classWithInterface("demo.web.WebController"))));

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

    private SeamTool seamTool(String... moduleNames) throws IOException {
        writeReport("core", coreModule());
        writeReport("web", webModule());

        return new SeamTool(contextOf(moduleNames));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> seamsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("seams");
    }

    private static Map<String, Object> seamOf(Map<String, Object> result, String interfaceName) {
        return seamsOf(result).stream()
                .filter(seam -> interfaceName.equals(seam.get("interfaceName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seam for " + interfaceName
                        + ", got: " + result.get("seams")));
    }

    @Test
    void anInterfaceWithImplementorsAndConsumersIsReported() throws Exception {
        Map<String, Object> result = seamTool("core", "web").getSeams();

        Map<String, Object> port = seamOf(result, "demo.core.Port");

        assertEquals("core", port.get("declaringModule"));
        assertEquals(2, port.get("implementorCount"));
        assertEquals(List.of("demo.core.Service"), port.get("consumers"));
        assertEquals(List.of("core", "web"), port.get("importingModules"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> implementors = (List<Map<String, Object>>) port.get("implementors");

        assertEquals(List.of("demo.core.Impl1", "demo.core.Impl2"),
                implementors.stream().map(implementor -> implementor.get("className")).toList());
        assertEquals(List.of("core", "core"),
                implementors.stream().map(implementor -> implementor.get("module")).toList(),
                "both implementors belong to the module that declares the interface");
    }

    @Test
    void anInterfaceWithoutAnImplementorIsReportedWithAnEmptyImplementorList() throws Exception {
        Map<String, Object> result = seamTool("core", "web").getSeams();

        Map<String, Object> unused = seamOf(result, "demo.core.Unused");

        assertEquals(List.of(), unused.get("implementors"));
        assertEquals(0, unused.get("implementorCount"));
        assertEquals(0, unused.get("consumerCount"));
        assertEquals(false, unused.get("portCandidate"));
    }

    @Test
    void anInterfaceImplementedSeveralTimesAndImportedBySeveralModulesIsAPortCandidate() throws Exception {
        Map<String, Object> result = seamTool("core", "web").getSeams();

        Map<String, Object> port = seamOf(result, "demo.core.Port");

        assertEquals(true, port.get("portCandidate"));
        assertEquals(2, port.get("implementorCount"));
        assertEquals(2, port.get("importingModuleCount"));
        assertTrue(((String) port.get("reason")).contains("implemented 2 times"), port.toString());
    }

    @Test
    void anInterfaceImplementedOnceAndUsedNowhereIsNoPortCandidate() throws Exception {
        Map<String, Object> result = seamTool("core", "web").getSeams();

        Map<String, Object> single = seamOf(result, "demo.core.Single");

        assertEquals(false, single.get("portCandidate"));
        assertEquals(1, single.get("implementorCount"));
        assertTrue(((String) single.get("reason")).contains("implemented once"), single.toString());
    }

    @Test
    void aModuleWithoutAReportIsSkippedAndNamed() throws Exception {
        Map<String, Object> result = seamTool("core", "web", "missing").getSeams();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");

        assertEquals(1, skipped.size());
        assertEquals("missing", skipped.getFirst().get("moduleName"));
        assertTrue(((String) skipped.getFirst().get("reason")).contains("No raw Java module report"),
                skipped.toString());

        assertFalse(seamsOf(result).stream()
                        .anyMatch(seam -> "missing".equals(seam.get("declaringModule"))),
                "a module without a report holds no row");
    }

    @Test
    void theInterfacesWithMoreImplementorsAndMoreConsumersComeFirst() throws Exception {
        Map<String, Object> result = seamTool("core", "web").getSeams();

        List<Map<String, Object>> seams = seamsOf(result);

        assertEquals("demo.core.Port", seams.getFirst().get("interfaceName"),
                "the port with two implementors comes before the interfaces with none");
        assertEquals("demo.core.Single", seams.get(1).get("interfaceName"),
                "the interface with one implementor comes before the one with none");
        assertEquals("demo.core.Unused", seams.get(2).get("interfaceName"));
        assertEquals("demo.core.WithDefault", seams.get(3).get("interfaceName"));
    }

    @Test
    void anInterfaceWithADefaultMethodAndAConstantIsCounted() throws Exception {
        Map<String, Object> result = seamTool("core", "web").getSeams();

        Map<String, Object> withDefault = seamOf(result, "demo.core.WithDefault");

        assertEquals("core", withDefault.get("declaringModule"));
        assertEquals(0, withDefault.get("implementorCount"));
        assertEquals(false, withDefault.get("portCandidate"));
    }

    @Test
    void twoCallsReturnTheSameOrder() throws Exception {
        SeamTool tool = seamTool("core", "web");

        assertEquals(tool.getSeams().get("seams"), tool.getSeams().get("seams"),
                "the counting needs no seed and no parameter, so two calls agree");
    }

    @Test
    void theSeamEvidenceIsWrittenAsACsvReport() throws Exception {
        seamTool("core", "web").getSeams();

        List<String> lines = Files.readAllLines(tempDir.resolve(SeamReports.REPORT_TYPE)
                .resolve("Seams.csv"));

        assertEquals("Interface,DeclaringModule,ImplementorCount,ConsumerCount,ImportingModules", lines.getFirst());
        assertEquals(4, lines.size() - 1, "one row per interface");

        assertEquals("demo.core.Port,core,2,1,core web", lines.get(1));
        assertEquals("demo.core.Single,core,1,0,core", lines.get(2));
        assertEquals("demo.core.Unused,core,0,0,", lines.get(3));
        assertEquals("demo.core.WithDefault,core,0,0,", lines.get(4));
    }

    @Test
    void theToolIsRegisteredInTheToolFactory() {
        List<Object> tools = ToolFactory.getToolInstanceList(contextOf("core"));

        assertTrue(tools.stream().anyMatch(tool -> tool instanceof SeamTool),
                "the seam tool has to be part of the tool list of the engine");
    }

    @Test
    void theToolIsNamedInTheJavaFamilyAndNamesItsParameters() throws Exception {
        java.lang.reflect.Method method = SeamTool.class.getMethod("getSeams");

        Tool tool = method.getAnnotation(Tool.class);

        assertNotNull(tool, "the seam tool has to be a tool of the engine");
        assertEquals("java_get_seams", tool.name());

        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            P description = parameter.getAnnotation(P.class);

            assertNotNull(description, "the parameter " + parameter.getName() + " has to be described");
            assertFalse(description.value().isBlank(), "the description of " + parameter.getName()
                    + " must not be empty");
        }
    }
}
