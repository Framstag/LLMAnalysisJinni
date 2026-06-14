package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaToolTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    @SuppressWarnings("unchecked")
    @Test
    void getAllCyclomaticComplexityReportsFiltersNonJavaModules() throws Exception {
        writeRawReport("core");
        AnalysisContext context = contextWithModules("core", "docs");
        JavaTool javaTool = new JavaTool(context);

        Map<String, Object> result = javaTool.getAllCyclomaticComplexityReports();
        List<?> reports = (List<?>) result.get("reports");
        List<?> skipped = (List<?>) result.get("skipped");

        assertEquals(1, reports.size());
        assertTrue(skipped.isEmpty());

        Map<?, ?> coreReport = (Map<?, ?>) reports.getFirst();
        assertEquals("core", coreReport.get("moduleName"));
        assertEquals("Cyclomatic complexity", coreReport.get("metric"));
        assertTrue(coreReport.get("report") instanceof List<?>);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAllMetricReportToolsReturnSkippedErrorsForMissingRawReports() throws Exception {
        Map<String, String> perModuleMethods = Map.ofEntries(
                Map.entry("getAllCyclomaticComplexityReports", "getCyclomaticComplexityModuleReport"),
                Map.entry("getAllVisibilityDistributionReports", "getVisibilityDistributionReport"),
                Map.entry("getAllInheritanceReports", "getInheritanceReport"),
                Map.entry("getAllMethodComplexityReports", "getMethodComplexityReport"),
                Map.entry("getAllMethodNestingDepthReports", "getMethodNestingDepthReport"),
                Map.entry("getAllFieldVisibilityReports", "getFieldVisibilityReport"),
                Map.entry("getAllClassCohesionReports", "getClassCohesionReport"),
                Map.entry("getAllCouplingReports", "getCouplingReport"),
                Map.entry("getAllTestCoverageReports", "getTestCoverageReport"),
                Map.entry("getAllCircularDependencyReports", "getCircularDependencyReport"),
                Map.entry("getAllMethodCountReports", "getMethodCountReport"),
                Map.entry("getAllDocumentationRatioReports", "getDocumentationRatioReport"),
                Map.entry("getAllDataClassReports", "getDataClassReport"),
                Map.entry("getAllBooleanParameterReports", "getBooleanParameterReport"),
                Map.entry("getAllAnnotationReports", "getAnnotationReport"),
                Map.entry("getAllPackageTangleReports", "getPackageTangleReport"),
                Map.entry("getAllImportDiversityReports", "getImportDiversityReport"));

        JavaTool javaTool = new JavaTool(contextWithModules("core", "api"));
        for (String methodName : perModuleMethods.keySet()) {
            Map<String, Object> result = (Map<String, Object>) javaTool.getClass()
                    .getMethod(methodName)
                    .invoke(javaTool);
            List<?> reports = (List<?>) result.get("reports");
            List<?> skipped = (List<?>) result.get("skipped");

            assertTrue(reports.isEmpty(), methodName);
            assertEquals(2, skipped.size(), methodName);
            assertSkippedError((Map<?, ?>) skipped.get(0), "core");
            assertSkippedError((Map<?, ?>) skipped.get(1), "api");
        }
    }

    @Test
    void getAllCyclomaticComplexityReportsUsesRawReportsAndReturnsCompactDistributions() throws Exception {
        writeRawReport("core");
        AnalysisContext context = contextWithModules("core", "docs");
        JavaTool javaTool = new JavaTool(context);

        Map<String, Object> result = javaTool.getAllCyclomaticComplexityReports();

        List<?> reports = (List<?>) result.get("reports");
        List<?> skipped = (List<?>) result.get("skipped");
        assertEquals(1, reports.size());
        assertTrue(skipped.isEmpty());

        Map<?, ?> coreReport = (Map<?, ?>) reports.getFirst();
        assertEquals("core", coreReport.get("moduleName"));
        assertEquals("Cyclomatic complexity", coreReport.get("metric"));
        assertTrue(coreReport.get("report") instanceof List<?>);

        assertTrue(((List<?>) javaTool.getCyclomaticComplexityModuleReport("core")).size() > 0);
    }

    @Test
    void getAllCyclomaticComplexityReportsMatchesPerModuleReportsForAllJavaModules() throws Exception {
        writeRawReport("core");
        writeRawReport("api");
        JavaTool javaTool = new JavaTool(contextWithModules("core", "api"));

        Map<String, Object> batchResult = javaTool.getAllCyclomaticComplexityReports();
        List<?> batchReports = (List<?>) batchResult.get("reports");
        List<?> skipped = (List<?>) batchResult.get("skipped");

        assertEquals(2, batchReports.size());
        assertTrue(skipped.isEmpty());

        assertEquals(metricReportJson(javaTool.getCyclomaticComplexityModuleReport("core")),
                metricReportJson(((Map<?, ?>) batchReports.get(0)).get("report")));
        assertEquals(metricReportJson(javaTool.getCyclomaticComplexityModuleReport("api")),
                metricReportJson(((Map<?, ?>) batchReports.get(1)).get("report")));
    }

    @Test
    void generateAllModuleAnalysisReportsContinuesAfterModuleError() throws Exception {
        Files.writeString(tempDir.resolve("Java_core.json"), "{}");
        Map<String, String> modulePaths = new LinkedHashMap<>();
        modulePaths.put("core", ".");
        modulePaths.put("api", "missing-api");
        AnalysisContext context = contextWithModulePaths(modulePaths);
        JavaTool javaTool = new JavaTool(context);

        Map<String, Object> result = javaTool.generateAllModuleAnalysisReports();

        List<?> reports = (List<?>) result.get("reports");
        List<?> skipped = (List<?>) result.get("skipped");
        assertEquals(2, reports.size());
        assertTrue(skipped.isEmpty());

        assertReportDescriptor((Map<?, ?>) reports.get(0), "core", "REUSED", "Java", "Java_core");
        assertReportDescriptor((Map<?, ?>) reports.get(1), "api", "ERROR", "Java", "Java_api");
    }

    @Test
    void generateAllModuleAnalysisReportsReusesExistingReportsAndSkipsNonJavaModules() throws Exception {
        Files.writeString(tempDir.resolve("Java_core.json"), "{}");
        AnalysisContext context = contextWithModules("core", "docs");
        JavaTool javaTool = new JavaTool(context);

        Map<String, Object> result = javaTool.generateAllModuleAnalysisReports();

        List<?> reports = (List<?>) result.get("reports");
        List<?> skipped = (List<?>) result.get("skipped");
        assertEquals(1, reports.size());
        assertEquals(1, skipped.size());

        Map<?, ?> coreReport = (Map<?, ?>) reports.getFirst();
        assertReportDescriptor(coreReport, "core", "REUSED", "Java", "Java_core");

        Map<?, ?> docsSkipped = (Map<?, ?>) skipped.getFirst();
        assertEquals("docs", docsSkipped.get("moduleName"));
        assertEquals("SKIPPED", docsSkipped.get("status"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAllMetricReportToolsReturnCompactReportsForAllModules() throws Exception {
        writeRawReport("core");
        writeRawReport("api");
        JavaTool javaTool = new JavaTool(contextWithModules("core", "api"));

        Map<String, String> perModuleMethods = Map.ofEntries(
                Map.entry("getAllCyclomaticComplexityReports", "getCyclomaticComplexityModuleReport"),
                Map.entry("getAllVisibilityDistributionReports", "getVisibilityDistributionReport"),
                Map.entry("getAllInheritanceReports", "getInheritanceReport"),
                Map.entry("getAllMethodComplexityReports", "getMethodComplexityReport"),
                Map.entry("getAllMethodNestingDepthReports", "getMethodNestingDepthReport"),
                Map.entry("getAllFieldVisibilityReports", "getFieldVisibilityReport"),
                Map.entry("getAllClassCohesionReports", "getClassCohesionReport"),
                Map.entry("getAllCouplingReports", "getCouplingReport"),
                Map.entry("getAllTestCoverageReports", "getTestCoverageReport"),
                Map.entry("getAllCircularDependencyReports", "getCircularDependencyReport"),
                Map.entry("getAllMethodCountReports", "getMethodCountReport"),
                Map.entry("getAllDocumentationRatioReports", "getDocumentationRatioReport"),
                Map.entry("getAllDataClassReports", "getDataClassReport"),
                Map.entry("getAllBooleanParameterReports", "getBooleanParameterReport"),
                Map.entry("getAllAnnotationReports", "getAnnotationReport"),
                Map.entry("getAllPackageTangleReports", "getPackageTangleReport"),
                Map.entry("getAllImportDiversityReports", "getImportDiversityReport"));

        for (String methodName : perModuleMethods.keySet()) {
            java.lang.reflect.Method batchMethod = javaTool.getClass().getMethod(methodName);
            java.lang.reflect.Method moduleMethod = javaTool.getClass().getMethod(perModuleMethods.get(methodName), String.class);
            Map<String, Object> result = (Map<String, Object>) batchMethod.invoke(javaTool);
            List<?> reports = (List<?>) result.get("reports");
            List<?> skipped = (List<?>) result.get("skipped");

            assertEquals(2, reports.size(), methodName);
            assertTrue(skipped.isEmpty(), methodName);

            Map<?, ?> coreBatchReport = (Map<?, ?>) reports.get(0);
            Map<?, ?> apiBatchReport = (Map<?, ?>) reports.get(1);
            String metric = (String) result.get("metric");
            assertMetricReport(coreBatchReport, "core", metric);
            assertMetricReport(apiBatchReport, "api", metric);

            assertEquals(metricReportJson(moduleMethod.invoke(javaTool, "core")),
                    metricReportJson(coreBatchReport.get("report")), methodName);
            assertEquals(metricReportJson(moduleMethod.invoke(javaTool, "api")),
                    metricReportJson(apiBatchReport.get("report")), methodName);
        }
    }

    @Test
    void getAllCyclomaticComplexityReportsReturnsSkippedErrorForMissingRawReport() throws Exception {
        writeRawReport("core");
        JavaTool javaTool = new JavaTool(contextWithModules("core", "api"));

        Map<String, Object> result = javaTool.getAllCyclomaticComplexityReports();

        List<?> skipped = (List<?>) result.get("skipped");
        assertEquals(1, skipped.size());
        Map<?, ?> apiError = (Map<?, ?>) skipped.get(0);
        assertEquals("api", apiError.get("moduleName"));
        assertEquals("ERROR", apiError.get("status"));
        assertEquals("Java_api", apiError.get("reportName"));
        assertTrue(((String) apiError.get("reasoning")).contains("Could not load or compute Java metric report"));
    }

    @Test
    void generateAllModuleAnalysisReportsGeneratesMissingRawReport() throws Exception {
        Path sourceDir = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("App.java"), """
                package demo;
                public class App {
                    public int add(int a, int b) {
                        if (a > 0) {
                            return a + b;
                        }
                        return b;
                    }
                }
                """);

        AnalysisContext context = contextWithModules("core", "docs");
        JavaTool javaTool = new JavaTool(context);

        Map<String, Object> result = javaTool.generateAllModuleAnalysisReports();

        List<?> reports = (List<?>) result.get("reports");
        assertEquals(1, reports.size());
        assertTrue(Files.exists(tempDir.resolve("Java_core.json")));

        Map<?, ?> coreReport = (Map<?, ?>) reports.getFirst();
        assertReportDescriptor(coreReport, "core", "GENERATED", "Java", "Java_core");
    }


    private void assertSkippedError(Map<?, ?> report, String moduleName) {
        assertEquals(moduleName, report.get("moduleName"));
        assertEquals("ERROR", report.get("status"));
        assertTrue(((String) report.get("reasoning")).contains("Could not load or compute Java metric report"));
    }

    private void assertMetricReport(Map<?, ?> report, String moduleName) {
        assertMetricReport(report, moduleName, null);
    }

    private void assertMetricReport(Map<?, ?> report, String moduleName, String metricName) {
        assertEquals(moduleName, report.get("moduleName"));
        if (metricName != null) {
            assertEquals(metricName, report.get("metric"));
        } else {
            assertTrue(report.containsKey("metric"));
        }
        assertTrue(report.containsKey("report"));
    }

    private String metricReportJson(Object metricReport) throws Exception {
        return objectMapper.writeValueAsString(metricReport);
    }

    private void assertReportDescriptor(Map<?, ?> descriptor, String moduleName, String status,
                                        String programmingLanguage, String reportName) {
        assertEquals(moduleName, descriptor.get("moduleName"));
        assertEquals(status, descriptor.get("status"));
        assertEquals(programmingLanguage, descriptor.get("programmingLanguage"));
        assertEquals(reportName, descriptor.get("reportName"));
    }
    private void writeRawReport(String moduleName) throws Exception {
        Method method = new Method("add", "(int,int)int");
        method.setCyclomaticComplexity(3);
        method.setVisibility(MethodVisibility.PUBLIC);
        method.setParameterCount(2);
        method.setLinesOfCode(10);
        method.setNestingDepth(2);

        Clazz clazz = new Clazz("demo.App", "App class.");
        clazz.addMethod(method);

        BuildUnit buildUnit = new BuildUnit("main", true, false, List.of(), List.of(clazz));
        Package pkg = new Package("demo");
        pkg.addBuildUnit(buildUnit);

        Module module = new Module(moduleName);
        module.addPackage(pkg);

        objectMapper.writeValue(tempDir.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private AnalysisContext contextWithModules(String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            ObjectNode language = languages.addObject();
            language.put("name", moduleName.equals("docs") ? "Python" : "Java");
            language.put("version", "unknown");

            ArrayNode subdirectories = module.putObject("subdirectories").putArray("directories");
            ObjectNode directory = subdirectories.addObject();
            directory.put("path", "src/main/java");
            directory.put("categoryId", "Src");
            directory.put("desc", "Java source directory");
        }

        return new AnalysisContext(tempDir, tempDir, Map.of(), state);
    }

    private AnalysisContext contextWithModulePaths(Map<String, String> modulePaths) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (Map.Entry<String, String> entry : modulePaths.entrySet()) {
            ObjectNode module = modules.addObject();
            module.put("name", entry.getKey());
            module.put("path", entry.getValue());

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            ObjectNode language = languages.addObject();
            language.put("name", "Java");
            language.put("version", "unknown");

            ArrayNode subdirectories = module.putObject("subdirectories").putArray("directories");
            ObjectNode directory = subdirectories.addObject();
            directory.put("path", "src/main/java");
            directory.put("categoryId", "Src");
            directory.put("desc", "Java source directory");
        }

        return new AnalysisContext(tempDir, tempDir, Map.of(), state);
    }
}
