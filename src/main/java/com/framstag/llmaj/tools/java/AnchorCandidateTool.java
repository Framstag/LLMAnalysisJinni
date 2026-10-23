package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.tools.common.Distribution;
import com.framstag.llmaj.tools.common.DistributionEntry;
import com.framstag.llmaj.tools.sbom.AppDependency;
import com.framstag.llmaj.tools.sbom.SBOMTool;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Aggregates the anchor candidates of the project per anchor type, ranks them and bounds every list by the
 * workspace budget {@code anchor.candidatesPerType}.
 *
 * <p>The tool exists because the anchor analysis must read a payload whose size does not grow with the number
 * of modules of the project. It reads the counted evidence that the other tools already produce, ranks it per
 * anchor type and returns the strongest candidates of each type together with the total number of candidates
 * of that type, so that a reader can tell a complete list from a bounded part of a longer one.
 *
 * <p>It counts nothing of its own and it reads no prose finding of the analysis state: the numbers it returns
 * are the numbers of the analyses that counted them, and every candidate names the tool that counted its
 * evidence.
 *
 * <p>The tool covers the six anchor types that rest on tool evidence. The {@code domain} anchors rest on the
 * context map result, which the anchor analysis reads itself, so they are not part of this result.
 */
public class AnchorCandidateTool {
    private static final Logger logger = LoggerFactory.getLogger(AnchorCandidateTool.class);

    /** Number of anchor candidates per anchor type a workspace is seeded with. */
    public static final int DEFAULT_CANDIDATES_PER_TYPE = 15;

    public static final String ENTRY_POINT_ANCHOR_TYPE = "entry-point";
    public static final String SEAM_ANCHOR_TYPE = "seam";
    public static final String BUILD_UNIT_ANCHOR_TYPE = "build-unit";
    public static final String CUT_POINT_ANCHOR_TYPE = "cut-point";
    public static final String TEST_COVERAGE_ANCHOR_TYPE = "test-coverage";
    public static final String TECHNOLOGY_MARKER_ANCHOR_TYPE = "technology-marker";

    private static final String ENTRY_POINT_SOURCE = "java_get_entry_points";
    private static final String SEAM_SOURCE = "java_get_seams";
    private static final String BUILD_UNIT_SOURCE = "java_get_inter_module_dependency_report";
    private static final String CUT_POINT_SOURCE = "java_get_all_split_candidates";
    private static final String TEST_COVERAGE_SOURCE = "java_get_all_test_coverage_reports";
    private static final String SBOM_SOURCE = "sbom_get_application_dependencies";
    private static final String IMPORT_DIVERSITY_SOURCE = "java_get_all_import_diversity_reports";

    /** The suffix the import distribution appends to a prefix it counts as external to the project. */
    private static final String EXTERNAL_IMPORT_MARKER = " (external)";

    /** Number of names an evidence string lists before it states how many it left out. */
    private static final int NAMED_EVIDENCE_LIMIT = 5;

    /**
     * The entry point candidates that rest on the strongest evidence come first: the production code before the test
     * and the generated code, because a main method of a benchmark harness is not a place a modernisation can attach
     * to, then a declared main method, then the classes that carry annotations, then the classes that implement
     * interfaces. The name breaks the ties, so two calls agree.
     */
    private static final Comparator<Map<String, Object>> ENTRY_POINT_ORDER = (left, right) -> {
        int byCodeCategory = Integer.compare(codeCategoryRank(left.get("codeCategory")),
                codeCategoryRank(right.get("codeCategory")));

        if (byCodeCategory != 0) {
            return byCodeCategory;
        }

        int byMainMethod = Boolean.compare(Boolean.TRUE.equals(right.get("hasMainMethod")),
                Boolean.TRUE.equals(left.get("hasMainMethod")));

        if (byMainMethod != 0) {
            return byMainMethod;
        }

        int byAnnotationCount = Integer.compare(stringsOf(right.get("annotations")).size(),
                stringsOf(left.get("annotations")).size());

        if (byAnnotationCount != 0) {
            return byAnnotationCount;
        }

        int byInterfaceCount = Integer.compare(stringsOf(right.get("interfaces")).size(),
                stringsOf(left.get("interfaces")).size());

        if (byInterfaceCount != 0) {
            return byInterfaceCount;
        }

        int byModule = ((String) left.get("module")).compareTo((String) right.get("module"));

        return byModule != 0 ? byModule : ((String) left.get("className")).compareTo((String) right.get("className"));
    };

    private static int codeCategoryRank(Object codeCategory) {
        String category = codeCategory == null ? "" : codeCategory.toString();

        return switch (category) {
            case "production" -> 0;
            case "test" -> 1;
            default -> 2;
        };
    }

    private final AnalysisContext context;
    private final JavaTool javaTool;
    private final SBOMTool sbomTool;

    public AnchorCandidateTool(AnalysisContext context, JavaTool javaTool, SBOMTool sbomTool) {
        this.context = context;
        this.javaTool = javaTool;
        this.sbomTool = sbomTool;
        logger.info("AnchorCandidateTool initialized.");
    }

    /** The candidates of one anchor type: how many the project holds and their rank, before the bound. */
    private record CandidateSet(int totalCount, List<AnchorCandidate> ranked) {
    }

    /** A cut point candidate with the rank keys of the ladder step it rests on. */
    private record CutPointCandidate(AnchorCandidate candidate, int cost, int step) {
    }

    /** A test coverage candidate with the rank keys of the coverage report it rests on. */
    private record CoverageCandidate(AnchorCandidate candidate, int untestedCount, int coveragePercent) {
    }

    /** A technology marker candidate with the number of imports the project spends on its namespace. */
    private record UsageCandidate(AnchorCandidate candidate, int importCount, boolean dependency) {
    }

    @Tool(name = "java_get_anchor_candidates",
            value = """
                    Returns the anchor candidates of the project per anchor type as a bounded, ranked list:
                    the entry points, the seams, the build units, the cut points, the test coverage and the
                    technology markers.

                    Every list holds the strongest candidates of its type up to the budget of the workspace
                    property "anchor.candidatesPerType", and beside it the total number of candidates that
                    type holds, so that a bounded list can be told apart from a complete one.

                    Every candidate names the element it points at, the module it belongs to, the counted
                    evidence it rests on with the numbers, and the tool that counted that evidence.

                    The tool counts nothing of its own: it aggregates the evidence of the seam, entry point,
                    inter module dependency, split candidate, test coverage, import diversity and dependency
                    analyses, and it ranks that evidence per type.

                    A module whose raw Java report is missing is named as skipped with the reason, and an
                    analysis whose input is absent is named as an absent source with the reason, so that no
                    count is invented for it.

                    The domain anchors are not part of this result: they rest on the context map result, which
                    the analysis reads itself.
                    """)
    public Map<String, Object> getAnchorCandidates() throws IOException {
        int budget = Config.getBudget(context.getProperties(), Config.ANCHOR_CANDIDATES_PER_TYPE_PROPERTY,
                DEFAULT_CANDIDATES_PER_TYPE);

        logger.info("## GetAnchorCandidates({} candidate(s) per anchor type)", budget);

        Map<String, String> skipped = new LinkedHashMap<>();
        List<Map<String, Object>> absentSources = new ArrayList<>();

        List<Map<String, Object>> anchorTypes = new ArrayList<>();
        anchorTypes.add(anchorTypeResult(ENTRY_POINT_ANCHOR_TYPE, budget, entryPointCandidates(skipped)));
        anchorTypes.add(anchorTypeResult(SEAM_ANCHOR_TYPE, budget, seamCandidates(skipped)));
        anchorTypes.add(anchorTypeResult(BUILD_UNIT_ANCHOR_TYPE, budget, buildUnitCandidates(skipped)));
        anchorTypes.add(anchorTypeResult(CUT_POINT_ANCHOR_TYPE, budget, cutPointCandidates(skipped)));
        anchorTypes.add(anchorTypeResult(TEST_COVERAGE_ANCHOR_TYPE, budget, testCoverageCandidates(skipped)));
        anchorTypes.add(anchorTypeResult(TECHNOLOGY_MARKER_ANCHOR_TYPE, budget,
                technologyMarkerCandidates(skipped, absentSources)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidatesPerType", budget);
        result.put("anchorTypes", anchorTypes);
        result.put("skipped", skippedModules(skipped));
        result.put("absentSources", absentSources);

        return result;
    }

    /**
     * Bounds the ranked list of one anchor type and states beside it how many candidates the type holds, so
     * that a reader can see that the list is a part of the whole.
     */
    private static Map<String, Object> anchorTypeResult(String anchorType, int budget, CandidateSet candidates) {
        List<AnchorCandidate> bounded = candidates.ranked().size() <= budget
                ? candidates.ranked()
                : candidates.ranked().subList(0, budget);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("anchorType", anchorType);
        result.put("budget", budget);
        result.put("totalCount", candidates.totalCount());
        result.put("returnedCount", bounded.size());
        result.put("candidates", bounded.stream().map(AnchorCandidateTool::descriptor).toList());

        return result;
    }

    private static Map<String, Object> descriptor(AnchorCandidate candidate) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("element", candidate.element());
        descriptor.put("module", candidate.module());
        descriptor.put("alsoSeenIn", candidate.alsoSeenIn());
        descriptor.put("evidence", candidate.evidence());
        descriptor.put("source", candidate.source());

        return descriptor;
    }

    /**
     * Folds the appearances of one element that several module reports hold into one candidate per element. The
     * strongest appearance, that is the first in the ranked order, becomes the candidate and names the module it is
     * read in; the modules of the other appearances are carried beside it. Without the fold the budget of a list is
     * spent on the same element several times, and the ranks below it are pushed out of the list.
     *
     * @param ranked the candidates in their ranked order, strongest first
     * @return one candidate per element, in the order of the strongest appearance
     */
    private static List<AnchorCandidate> foldByElement(List<AnchorCandidate> ranked) {
        Map<String, AnchorCandidate> byElement = new LinkedHashMap<>();
        Map<String, List<String>> modulesByElement = new LinkedHashMap<>();

        for (AnchorCandidate candidate : ranked) {
            String element = candidate.element() == null ? "" : candidate.element();

            if (!byElement.containsKey(element)) {
                byElement.put(element, candidate);
                modulesByElement.put(element, new ArrayList<>(candidate.alsoSeenIn()));
                continue;
            }

            List<String> modules = modulesByElement.get(element);

            if (candidate.module() != null && !candidate.module().equals(byElement.get(element).module())
                    && !modules.contains(candidate.module())) {
                modules.add(candidate.module());
            }
        }

        List<AnchorCandidate> folded = new ArrayList<>(byElement.size());

        for (Map.Entry<String, AnchorCandidate> entry : byElement.entrySet()) {
            AnchorCandidate strongest = entry.getValue();
            List<String> alsoSeenIn = modulesByElement.get(entry.getKey());
            List<String> evidence = new ArrayList<>(strongest.evidence());

            if (!alsoSeenIn.isEmpty()) {
                evidence.add("the reports of the module(s) " + String.join(", ", alsoSeenIn)
                        + " hold the same element, so it is counted once here");
            }

            folded.add(new AnchorCandidate(strongest.element(), strongest.module(), List.copyOf(alsoSeenIn),
                    List.copyOf(evidence), strongest.source()));
        }

        return folded;
    }

    /**
     * The classes the entry point analysis counts as evidence of an entry point: a class that declares a main
     * method, that carries an annotation or that implements an interface.
     */
    private CandidateSet entryPointCandidates(Map<String, String> skipped) throws IOException {
        Map<String, Object> report = new EntryPointTool(context).getEntryPoints();
        collectSkipped(skipped, descriptorsOf(report, "skipped"));

        List<Map<String, Object>> candidates = descriptorsOf(report, "entryPoints").stream()
                .filter(AnchorCandidateTool::carriesEntryPointEvidence)
                .sorted(ENTRY_POINT_ORDER)
                .toList();

        List<AnchorCandidate> ranked = new ArrayList<>(candidates.size());

        for (Map<String, Object> candidate : candidates) {
            ranked.add(new AnchorCandidate((String) candidate.get("className"),
                    (String) candidate.get("module"),
                    entryPointEvidenceOf(candidate),
                    ENTRY_POINT_SOURCE));
        }

        List<AnchorCandidate> folded = foldByElement(ranked);

        return new CandidateSet(folded.size(), folded);
    }

    private static boolean carriesEntryPointEvidence(Map<String, Object> candidate) {
        return Boolean.TRUE.equals(candidate.get("hasMainMethod"))
                || !stringsOf(candidate.get("annotations")).isEmpty()
                || !stringsOf(candidate.get("interfaces")).isEmpty();
    }

    private static List<String> entryPointEvidenceOf(Map<String, Object> candidate) {
        List<String> evidence = new ArrayList<>();

        if (Boolean.TRUE.equals(candidate.get("hasMainMethod"))) {
            evidence.add("declares a main method");
        }

        List<String> annotations = stringsOf(candidate.get("annotations"));

        if (!annotations.isEmpty()) {
            evidence.add("carries " + annotations.size() + " annotation(s): " + boundedJoin(annotations));
        }

        List<String> interfaces = stringsOf(candidate.get("interfaces"));

        if (!interfaces.isEmpty()) {
            evidence.add("implements " + interfaces.size() + " interface(s): " + boundedJoin(interfaces));
        }

        evidence.add("the class is " + candidate.get("codeCategory") + " code");

        return evidence;
    }

    /**
     * The interfaces of the project, in the rank the seam analysis gives them: the interfaces with more
     * implementors and more importing modules first.
     */
    private CandidateSet seamCandidates(Map<String, String> skipped) throws IOException {
        Map<String, Object> report = new SeamTool(context).getSeams();
        collectSkipped(skipped, descriptorsOf(report, "skipped"));

        List<Map<String, Object>> seams = descriptorsOf(report, "seams");
        List<AnchorCandidate> ranked = new ArrayList<>(seams.size());

        for (Map<String, Object> seam : seams) {
            ranked.add(new AnchorCandidate((String) seam.get("interfaceName"),
                    (String) seam.get("declaringModule"),
                    seamEvidenceOf(seam),
                    SEAM_SOURCE));
        }

        List<AnchorCandidate> folded = foldByElement(ranked);

        return new CandidateSet(folded.size(), folded);
    }

    private static List<String> seamEvidenceOf(Map<String, Object> seam) {
        List<String> implementors = new ArrayList<>();

        for (Map<String, Object> implementor : descriptorsOf(seam, "implementors")) {
            implementors.add(implementor.get("className") + " (" + implementor.get("module") + ")");
        }

        List<String> evidence = new ArrayList<>();
        evidence.add("implemented " + intOf(seam, "implementorCount") + " time(s)"
                + (implementors.isEmpty() ? "" : ": " + boundedJoin(implementors)));
        evidence.add("used by " + intOf(seam, "consumerCount")
                + " class(es) of the declaring module that do not implement it");
        evidence.add("imported by " + intOf(seam, "importingModuleCount") + " module(s): "
                + boundedJoin(stringsOf(seam.get("importingModules"))));
        evidence.add("the seam analysis reads it as "
                + (Boolean.TRUE.equals(seam.get("portCandidate")) ? "a port candidate" : "no port candidate")
                + " (" + seam.get("reason") + ")");

        return evidence;
    }

    /**
     * The modules of the inter module dependency matrix: the hubs that other modules depend on and the
     * orphans that take part in no dependency of the project at all.
     */
    private CandidateSet buildUnitCandidates(Map<String, String> skipped) throws IOException {
        List<Distribution> report = javaTool.getInterModuleDependencyReport();
        Map<String, Integer> afferentCoupling = entriesNamed(report, "Inter-module Ca");
        Map<String, Integer> efferentCoupling = entriesNamed(report, "Inter-module Ce");

        List<String> moduleNames = new ArrayList<>(afferentCoupling.keySet());
        moduleNames.sort(Comparator
                .comparingInt((String module) -> afferentCoupling.getOrDefault(module, 0)).reversed()
                .thenComparing(Comparator
                        .comparingInt((String module) -> efferentCoupling.getOrDefault(module, 0)).reversed())
                .thenComparing(Comparator.<String>naturalOrder()));

        List<AnchorCandidate> ranked = new ArrayList<>();

        for (String moduleName : moduleNames) {
            int afferent = afferentCoupling.getOrDefault(moduleName, 0);
            int efferent = efferentCoupling.getOrDefault(moduleName, 0);

            if (skipped.containsKey(moduleName)) {
                // The module report could not be read, so its two zeroes are a gap and not a count. The
                // skipped list names it already.
                continue;
            }

            if (afferent == 0 && efferent != 0) {
                // A module that only uses other modules is no hub and no orphan: nothing attaches to it.
                continue;
            }

            ranked.add(new AnchorCandidate(moduleName,
                    moduleName,
                    buildUnitEvidenceOf(afferent, efferent),
                    BUILD_UNIT_SOURCE));
        }

        return new CandidateSet(ranked.size(), ranked);
    }

    private static List<String> buildUnitEvidenceOf(int afferentCoupling, int efferentCoupling) {
        int coupling = afferentCoupling + efferentCoupling;

        List<String> evidence = new ArrayList<>();
        evidence.add("afferent coupling " + afferentCoupling + ": " + afferentCoupling
                + " module(s) of the project depend on it");
        evidence.add("efferent coupling " + efferentCoupling + ": it depends on " + efferentCoupling
                + " module(s) of the project");
        evidence.add("instability " + (coupling == 0 ? 0 : (efferentCoupling * 100) / coupling)
                + " % (Ce/(Ce+Ca)*100)");

        if (afferentCoupling == 0) {
            evidence.add("the module takes part in no inter module dependency of the project");
        }

        return evidence;
    }

    /**
     * The steps of the separation ladders of the project, the cheapest step of every module first.
     */
    private CandidateSet cutPointCandidates(Map<String, String> skipped) throws IOException {
        Map<String, Object> report = javaTool.getAllSplitCandidates();
        collectSkipped(skipped, descriptorsOf(report, "skipped"));

        List<CutPointCandidate> candidates = new ArrayList<>();
        int totalCount = 0;

        for (Map<String, Object> moduleReport : descriptorsOf(report, "reports")) {
            String moduleName = (String) moduleReport.get("moduleName");

            if (!"OK".equals(moduleReport.get("status"))) {
                skipped.putIfAbsent(moduleName, String.valueOf(moduleReport.get("reasoning")));
                continue;
            }

            // The ladder of a module is bounded by the split candidate analysis itself, so the count of the
            // whole is the number of steps the ladder holds, not the number of steps this tool can rank.
            totalCount += intOf(moduleReport, "separationCount");

            int step = 0;

            for (Map<String, Object> separation : descriptorsOf(moduleReport, "separations")) {
                step++;

                candidates.add(new CutPointCandidate(new AnchorCandidate(
                        moduleName + " step " + step,
                        moduleName,
                        cutPointEvidenceOf(separation),
                        CUT_POINT_SOURCE), intOf(separation, "cost"), step));
            }
        }

        candidates.sort(Comparator.comparingInt(CutPointCandidate::cost)
                .thenComparing(candidate -> candidate.candidate().module())
                .thenComparingInt(CutPointCandidate::step));

        return new CandidateSet(totalCount, candidates.stream().map(CutPointCandidate::candidate).toList());
    }

    private static List<String> cutPointEvidenceOf(Map<String, Object> separation) {
        List<String> evidence = new ArrayList<>();
        evidence.add("the ladder prices the step at " + intOf(separation, "cost")
                + " (sum of the reference weight it severs)");
        evidence.add("it severs " + intOf(separation, "severedEdgeCount")
                + " reference edge(s): the strongest weighs " + intOf(separation, "strongestSeveredWeight")
                + ", " + intOf(separation, "totalSeveredWeight") + " in total");
        evidence.add("it separates the module into " + intOf(separation, "groupCount") + " group(s)");

        List<Map<String, Object>> groups = descriptorsOf(separation, "groups");

        if (!groups.isEmpty()) {
            Map<String, Object> largest = groups.getFirst();

            evidence.add("the largest group holds " + intOf(largest, "size") + " class(es), "
                    + intOf(largest, "dominantPackageMemberCount") + " of them in the package "
                    + largest.get("dominantPackage"));
        }

        return evidence;
    }

    /**
     * The modules the test coverage analysis counts, the module with the most untested production classes
     * first.
     */
    private CandidateSet testCoverageCandidates(Map<String, String> skipped) throws IOException {
        Map<String, Object> report = javaTool.getAllTestCoverageReports();
        collectSkipped(skipped, descriptorsOf(report, "skipped"));

        List<CoverageCandidate> candidates = new ArrayList<>();

        for (Map<String, Object> moduleReport : descriptorsOf(report, "reports")) {
            String moduleName = (String) moduleReport.get("moduleName");
            Map<String, Integer> summary = new TreeMap<>();

            for (Map<String, Object> distribution : descriptorsOf(moduleReport, "report")) {
                if ("Test coverage summary".equals(distribution.get("name"))) {
                    summary.putAll(entriesOf(distribution));
                }
            }

            if (summary.isEmpty()) {
                skipped.putIfAbsent(moduleName, "The test coverage report of the module holds no summary.");
                continue;
            }

            int productionClassCount = summary.getOrDefault("TOTAL_PRODUCTION_CLASSES", 0);
            int testedClassCount = summary.getOrDefault("TESTED_CLASSES", 0);
            int untestedClassCount = summary.getOrDefault("UNTESTED_CLASSES", 0);
            int coveragePercent = summary.getOrDefault("COVERAGE_RATIO_PERCENT",
                    productionClassCount == 0 ? 100 : 0);

            List<String> evidence = List.of(
                    productionClassCount + " production class(es) of the module are counted",
                    testedClassCount + " of them have a test class, " + untestedClassCount + " have none",
                    "the test coverage of the module is " + coveragePercent + " %");

            candidates.add(new CoverageCandidate(
                    new AnchorCandidate(moduleName, moduleName, evidence, TEST_COVERAGE_SOURCE),
                    untestedClassCount, coveragePercent));
        }

        candidates.sort(Comparator.comparingInt(CoverageCandidate::untestedCount).reversed()
                .thenComparingInt(CoverageCandidate::coveragePercent)
                .thenComparing(candidate -> candidate.candidate().module()));

        return new CandidateSet(candidates.size(), candidates.stream().map(CoverageCandidate::candidate).toList());
    }

    /**
     * The direct dependencies of the application and the external import prefixes of the project, the ones the
     * project spends the most imports on first. A dependency is ranked by the imports of its namespace, so a
     * dependency the project leans on stands before one it barely mentions.
     */
    private CandidateSet technologyMarkerCandidates(Map<String, String> skipped,
                                                    List<Map<String, Object>> absentSources) throws IOException {
        Map<String, Object> diversityReport = javaTool.getAllImportDiversityReports();
        collectSkipped(skipped, descriptorsOf(diversityReport, "skipped"));

        Map<String, Integer> importCounts = new TreeMap<>();
        Map<String, Set<String>> importingModules = new TreeMap<>();

        for (Map<String, Object> moduleReport : descriptorsOf(diversityReport, "reports")) {
            String moduleName = (String) moduleReport.get("moduleName");

            for (Map<String, Object> distribution : descriptorsOf(moduleReport, "report")) {
                for (Map<String, Object> entry : descriptorsOf(distribution, "entries")) {
                    String label = String.valueOf(entry.get("value"));

                    if (!label.endsWith(EXTERNAL_IMPORT_MARKER)) {
                        continue;
                    }

                    String prefix = label.substring(0, label.length() - EXTERNAL_IMPORT_MARKER.length());

                    importCounts.merge(prefix, intOf(entry, "count"), Integer::sum);
                    importingModules.computeIfAbsent(prefix, key -> new TreeSet<>()).add(moduleName);
                }
            }
        }

        List<UsageCandidate> candidates = new ArrayList<>();

        for (AppDependency dependency : dependencies(absentSources)) {
            String namespace = namespaceOf(dependency.name());
            int importCount = importCounts.getOrDefault(namespace, 0);

            List<String> evidence = new ArrayList<>();
            evidence.add("version " + (dependency.version() == null ? "unknown" : dependency.version()));
            evidence.add("the SBOM lists it as a direct dependency of the application");
            evidence.add(importCount == 0
                    ? "no import of the project uses the namespace " + namespace
                    : "the import distribution counts " + importCount + " import(s) of the namespace "
                            + namespace);

            candidates.add(new UsageCandidate(
                    new AnchorCandidate(dependency.name(), "", evidence, SBOM_SOURCE), importCount, true));
        }

        for (Map.Entry<String, Integer> entry : importCounts.entrySet()) {
            Set<String> modules = importingModules.getOrDefault(entry.getKey(), Set.of());

            List<String> evidence = List.of(
                    "the import distribution counts " + entry.getValue() + " import(s) of the prefix "
                            + entry.getKey(),
                    "the prefix is imported by " + modules.size() + " module(s): "
                            + boundedJoin(new ArrayList<>(modules)));

            candidates.add(new UsageCandidate(
                    new AnchorCandidate(entry.getKey(), "", evidence, IMPORT_DIVERSITY_SOURCE),
                    entry.getValue(), false));
        }

        candidates.sort(Comparator.comparingInt(UsageCandidate::importCount).reversed()
                .thenComparing(candidate -> !candidate.dependency())
                .thenComparing(candidate -> candidate.candidate().element()));

        return new CandidateSet(candidates.size(), candidates.stream().map(UsageCandidate::candidate).toList());
    }

    /**
     * The direct dependencies of the application. A workspace without a loaded SBOM holds none, and that is
     * stated as an absent source rather than as an empty list without a reason.
     */
    private List<AppDependency> dependencies(List<Map<String, Object>> absentSources) {
        try {
            return sbomTool.getApplicationDependencies();
        } catch (IllegalStateException e) {
            logger.warn("The SBOM is not loaded, so the technology-marker candidates rest on the import"
                    + " distribution alone");

            Map<String, Object> absentSource = new LinkedHashMap<>();
            absentSource.put("source", SBOM_SOURCE);
            absentSource.put("reason", "The SBOM is not loaded, so no dependency of the application is counted"
                    + " and the technology-marker candidates rest on the import distribution alone.");
            absentSources.add(absentSource);

            return List.of();
        }
    }

    /**
     * The namespace of a dependency, read from the group id of its name: {@code org.slf4j:slf4j-api} spends its
     * imports under {@code org.slf4j}.
     */
    private static String namespaceOf(String dependencyName) {
        if (dependencyName == null || dependencyName.isBlank()) {
            return "";
        }

        int groupSeparator = dependencyName.indexOf(':');
        String groupId = groupSeparator < 0 ? dependencyName : dependencyName.substring(0, groupSeparator);
        String[] segments = groupId.split("\\.");

        return segments.length >= 2 ? segments[0] + "." + segments[1] : groupId;
    }

    private static Map<String, Integer> entriesNamed(List<Distribution> report, String namePrefix) {
        Map<String, Integer> entries = new TreeMap<>();

        for (Distribution distribution : report) {
            if (!distribution.getName().startsWith(namePrefix)) {
                continue;
            }

            for (DistributionEntry entry : distribution.getEntries()) {
                entries.put(entry.getValue(), entry.getCount());
            }
        }

        return entries;
    }

    private static Map<String, Integer> entriesOf(Map<String, Object> compactDistribution) {
        Map<String, Integer> entries = new LinkedHashMap<>();

        for (Map<String, Object> entry : descriptorsOf(compactDistribution, "entries")) {
            entries.put(String.valueOf(entry.get("value")), intOf(entry, "count"));
        }

        return entries;
    }

    private static List<Map<String, Object>> descriptorsOf(Map<String, Object> parent, String key) {
        Object value = parent.get(key);

        if (!(value instanceof List<?> entries)) {
            return List.of();
        }

        List<Map<String, Object>> descriptors = new ArrayList<>(entries.size());

        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> descriptor) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedDescriptor = (Map<String, Object>) descriptor;
                descriptors.add(typedDescriptor);
            }
        }

        return descriptors;
    }

    private static List<String> stringsOf(Object value) {
        if (!(value instanceof List<?> entries)) {
            return List.of();
        }

        List<String> strings = new ArrayList<>(entries.size());

        for (Object entry : entries) {
            strings.add(String.valueOf(entry));
        }

        return strings;
    }

    private static int intOf(Map<String, Object> descriptor, String key) {
        Object value = descriptor.get(key);

        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Joins the values and states how many were left out, so that one evidence string cannot grow with the
     * size of the project.
     */
    private static String boundedJoin(List<String> values) {
        if (values.size() <= NAMED_EVIDENCE_LIMIT) {
            return String.join(", ", values);
        }

        return String.join(", ", values.subList(0, NAMED_EVIDENCE_LIMIT))
                + " and " + (values.size() - NAMED_EVIDENCE_LIMIT) + " more";
    }

    /**
     * Collects the modules a source analysis names as skipped. The Java tool states the reason as
     * {@code reasoning}, the seam and entry point tools state it as {@code reason}; both are reported the same
     * way here, so that a reader of the result sees one list.
     */
    private static void collectSkipped(Map<String, String> skipped, List<Map<String, Object>> entries) {
        for (Map<String, Object> entry : entries) {
            String moduleName = String.valueOf(entry.get("moduleName"));
            Object reason = entry.get("reason") != null ? entry.get("reason") : entry.get("reasoning");

            if (reason != null) {
                skipped.putIfAbsent(moduleName, String.valueOf(reason));
            }
        }
    }

    private static List<Map<String, Object>> skippedModules(Map<String, String> skipped) {
        List<Map<String, Object>> descriptors = new ArrayList<>(skipped.size());

        for (Map.Entry<String, String> entry : skipped.entrySet()) {
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("moduleName", entry.getKey());
            descriptor.put("reason", entry.getValue());
            descriptors.add(descriptor);
        }

        return descriptors;
    }
}
