package com.framstag.llmaj.tools.sbom;

import com.framstag.llmaj.AnalysisContext;
import de.siegmar.fastcsv.writer.CsvWriter;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.cyclonedx.model.*;
import org.cyclonedx.parsers.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SBOMTool {
    private static final Logger logger = LoggerFactory.getLogger(SBOMTool.class);

    private Bom bom;

    private final AnalysisContext context;

    private void assertSBOMLoaded() {
        if (bom == null) {
            logger.error("No SBOM loaded");

            throw new IllegalStateException("No SBOM loaded");
        }
    }

    private Metadata asserMetadata(Bom bom) {
        Metadata metadata = bom.getMetadata();

        if (metadata == null) {
            logger.error("No Metadata in SBOM");

            throw new IllegalStateException("No Metadata in SBOM");
        }

        return metadata;
    }

        private Component asserComponent(Metadata metadata) {
        Component component = metadata.getComponent();

        if (component == null) {
            logger.error("No Metadata.Component in SBOM");

            throw new IllegalStateException("No Metadata.Component in SBOM");
        }

        return component;
    }

    private String concatinateGroupIdAndName(String groupId, String name) {
        if (groupId == null || groupId.isEmpty()) {
            return name;
        }
        else {
            return groupId + ":" + name;
        }
    }

    private List<License> getLicenses() {
        List<LicenseChoice> licenseChoices = bom.getComponents()
                .stream()
                .map(Component::getLicenses)
                .toList();

        List<String> licenses = new LinkedList<>();

        for (LicenseChoice choice : licenseChoices) {
            if (choice == null) {
                continue;
            }

            if (choice.getLicenses()!= null && !choice.getLicenses().isEmpty()) {
                for (var license : choice.getLicenses()) {
                    String licenseName = license.getName() != null ? license.getName() : license.getId();
                    licenses.add(licenseName);
                }
            }
            if (choice.getExpression() != null) {
                licenses.add(choice.getExpression().getValue());
            }
        }

        return licenses
                .stream()
                .distinct()
                .sorted()
                .map(License::new)
                .collect(Collectors.toList());
    }

    private List<AppDependencyLicense> getDependencyLicenses() {
        List<AppDependencyLicense> dependencies = new LinkedList<>();

        for (Component component : bom.getComponents()) {
            LicenseChoice choice = component.getLicenses();

            StringBuilder licenseNameList = new StringBuilder();

            if (choice != null && choice.getLicenses() != null && !choice.getLicenses().isEmpty()) {

                for (var license : choice.getLicenses()) {
                    String licenseName = license.getName() != null ? license.getName() : license.getId();
                    if (licenseNameList.isEmpty()) {
                        licenseNameList.append(licenseName);
                    } else {
                        licenseNameList.append(", ").append(licenseName);
                    }
                }
            }
            if (choice != null && choice.getExpression() != null) {
                if (licenseNameList.isEmpty()) {
                    licenseNameList.append(choice.getExpression().getValue());
                } else {
                    licenseNameList.append(", ").append(choice.getExpression().getValue());
                }
            }

            dependencies.add(new AppDependencyLicense(component.getBomRef(),
                    concatinateGroupIdAndName(component.getGroup(),component.getName()),
                    component.getVersion(),
                    licenseNameList.toString()));
        }

        return dependencies.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public SBOMTool(AnalysisContext context) {
        this.context = context;
        logger.info("SBOMTool initialized.");
    }

    @Tool(name = "sbom_is_already_loaded",
            value =
                    """
                    Returns 'true' if a SBOM was already loaded. Else returns 'false'.

                    In case of errors, 'ERROR' is returned.
                    """)
    public String sbomIsAlreadyLoaded() throws IOException {
        logger.info("## SBOMIsAlreadyLoaded()");

        String result;

        if (bom != null) {
            result = "true";
        }
        else {
            result = "false";
        }

        logger.info("## SBOMIsAlreadyLoaded() => '{}'", result);

        return result;
    }

    @Tool(name="sbom_load_from_file",
            value =
            """
                Loads the SBOM file with the given filename.
                 You must call this tool, before you can access the information stored in the file.
            """)
    public String loadSBOM(@P("SBOM filename") String filename) {
        logger.info("## LoadSBOMFromFile('{}')", filename);

        if (filename == null || filename.isBlank()) {
            // A project without an SBOM reaches this call with an empty path, because that is the path the
            // location task stored. A bare error would leave the model guessing what to do next, so the
            // result names the missing input and what follows from it.
            logger.info("## LoadSBOMFromFile() => no filename was given, nothing to load");

            return "No SBOM file to load: the path of the SBOM file is empty, so no SBOM was located in"
                    + " this project. Continue without dependency data and state that no SBOM was found.";
        }

        Path absoluteFilename = context.getProjectRoot().resolve(filename);
        File file = absoluteFilename.toFile();

        if (!file.exists()) {
            logger.error("## LoadSBOMFromFile() => File '{}' does not exist", absoluteFilename);

            return "No SBOM file to load: '" + filename + "' does not exist in the project. Continue"
                    + " without dependency data and state that the file was not found.";
        }

        try {
            logger.info("Loading '{}'...", absoluteFilename);
            var parser = new JsonParser();

            bom = parser.parse(file);
            logger.info("done.");
        } catch (Exception e) {
            logger.error("=> Failed to parse SBOM file '{}'", filename, e);

            return "The SBOM file '" + filename + "' could not be parsed (" + e.getMessage()
                    + "), so no dependency data is available.";
        }

        logger.info("## => 'OK'");

        return "OK";
    }

    @Tool(name="sbom_get_application_info",
            value =
                    """
                    Return the application name as stored in the SBOM from the loaded SBOM file.
                    """)
    public String getApplicationInfo() {
        logger.info("## SBOMApplicationInfo()");

        assertSBOMLoaded();

        Metadata metadata = asserMetadata(bom);
        Component component = asserComponent(metadata);

        return component.getName()+" in version "+ component.getVersion();
    }

    @Tool(name="sbom_get_dependencies_licenses",
            value =
                    """
                    Return the various licences the application dependencies use.
                    """)
    public List<License> getApplicationDependencyLicences() {
        logger.info("## getApplicationDependencyLicences()");

        assertSBOMLoaded();

        return getLicenses();
    }

    @Tool(name="sbom_get_application_dependencies",
            value =
                    """
                    Return the dependencies of the application from the loaded SBOM file.

                    The result names the route the read took, so that a list which rests on a fallback is not read
                    as the direct dependency list of the root component, and it carries the reason when the read
                    found no dependency or had to take a fallback.
                    """)
    public AppDependencyRead getApplicationDependencies() {
        logger.info("## SBOMApplicationDependencies()");

        if (bom == null) {
            // A bare empty list reads as a property of the project, so the read says what it misses instead.
            String reason = "The SBOM is not loaded, so no dependency of the application is known.";

            logger.info("## SBOMApplicationDependencies() => {}", reason);

            return new AppDependencyRead(List.of(), AppDependencyRead.ROUTE_NONE, reason, 0, 0);
        }

        List<Component> components = bom.getComponents() == null ? List.of() : bom.getComponents();
        List<Dependency> graph = bom.getDependencies() == null ? List.of() : bom.getDependencies();

        Map<String, Component> componentMap = components.stream()
                .filter(component -> component.getBomRef() != null)
                .collect(Collectors.toMap(Component::getBomRef, Function.identity(), (first, second) -> first));

        Map<String, Dependency> dependencyMap = graph.stream()
                .filter(dependency -> dependency.getRef() != null)
                .collect(Collectors.toMap(Dependency::getRef, Function.identity(), (first, second) -> first));

        String rootReference = rootReference();

        logger.info("Root component has bom-ref '{}'", rootReference);

        return readDependencies(components, graph, componentMap, dependencyMap, rootReference);
    }

    /**
     * Resolves the root of the dependency graph through the routes a CycloneDX SBOM offers and reports the route it
     * took. A tool that writes the root entry is read through that entry; a tool that writes one entry per project
     * module of an aggregator build is read through those entries; and an SBOM whose graph resolves neither is read
     * through its components, which the result states as not being a direct dependency list.
     */
    private AppDependencyRead readDependencies(List<Component> components,
                                               List<Dependency> graph,
                                               Map<String, Component> componentMap,
                                               Map<String, Dependency> dependencyMap,
                                               String rootReference) {
        List<String> references;
        String route;
        String reason = "";

        Dependency rootEntry = rootReference.isEmpty() ? null : dependencyMap.get(rootReference);

        if (rootEntry != null) {
            logger.info("Finding direct dependencies of root component ...");

            route = AppDependencyRead.ROUTE_ROOT;
            references = referencesOf(List.of(rootEntry));
        } else {
            List<Dependency> moduleEntries = graph.stream()
                    .filter(entry -> isProjectReference(entry.getRef()))
                    .toList();

            if (!moduleEntries.isEmpty()) {
                logger.info("The graph holds no entry for '{}', reading the {} project module entries instead",
                        rootReference, moduleEntries.size());

                route = AppDependencyRead.ROUTE_PROJECT_MODULES;
                references = referencesOf(moduleEntries);
                reason = "The dependency graph of the SBOM holds no entry for the reference of the root component '"
                        + rootReference + "', so the dependencies were read from the entries of the "
                        + moduleEntries.size() + " project modules of the aggregator build instead.";
            } else {
                logger.info("The graph of the SBOM holds no usable entry for '{}', reading its components",
                        rootReference);

                route = AppDependencyRead.ROUTE_COMPONENTS;
                references = components.stream()
                        .map(Component::getBomRef)
                        .filter(reference -> reference != null)
                        .toList();
                reason = "The dependency graph of the SBOM holds no entry for the reference of the root component '"
                        + rootReference + "' and no entry of a project module, so the read rests on the components"
                        + " of the SBOM. That is not a direct dependency list: it does not tell a direct dependency"
                        + " from a transitive one.";
            }
        }

        Map<String, AppDependency> dependencies = new LinkedHashMap<>();
        int projectComponentsLeftOut = 0;

        for (String reference : references) {
            Component component = componentMap.get(reference);

            if (component == null) {
                continue;
            }

            if (isProjectReference(component.getBomRef())) {
                projectComponentsLeftOut++;
                continue;
            }

            dependencies.putIfAbsent(component.getBomRef(), new AppDependency(
                    component.getBomRef(),
                    concatinateGroupIdAndName(component.getGroup(), component.getName()),
                    component.getVersion()));
        }

        List<AppDependency> result = new ArrayList<>(dependencies.values());

        if (result.isEmpty() && reason.isEmpty()) {
            // No dependency and no fallback: the read is empty and has to say why, because the reason is what the
            // consumers of the read answer with.
            reason = "The dependency graph of the SBOM holds an entry for the reference of the root component '"
                    + rootReference + "', but that entry names no component of the SBOM, so the read found no"
                    + " dependency.";
        }

        if (result.isEmpty() && !reason.isEmpty()) {
            reason = reason + " No dependency of the application was found this way.";
        }

        if (projectComponentsLeftOut > 0) {
            logger.info("Left out {} component(s) that are modules of the project", projectComponentsLeftOut);
        }

        logger.info("## SBOMApplicationDependencies() => route '{}', {} dependency(ies)", route, result.size());

        return new AppDependencyRead(result, route, reason, components.size(), projectComponentsLeftOut);
    }

    /**
     * @return the reference of the root component as the metadata of the SBOM states it, empty when the SBOM holds
     *         no metadata, no component or no reference
     */
    private String rootReference() {
        Metadata metadata = bom.getMetadata();

        if (metadata == null || metadata.getComponent() == null || metadata.getComponent().getBomRef() == null) {
            return "";
        }

        return metadata.getComponent().getBomRef();
    }

    private static List<String> referencesOf(List<Dependency> entries) {
        List<String> references = new ArrayList<>();

        for (Dependency entry : entries) {
            if (entry.getDependencies() == null) {
                continue;
            }

            for (Dependency dependency : entry.getDependencies()) {
                if (dependency.getRef() != null) {
                    references.add(dependency.getRef());
                }
            }
        }

        return references;
    }

    /**
     * @return true when the reference belongs to a project module of an aggregator build rather than to a library
     */
    private static boolean isProjectReference(String reference) {
        return reference != null && reference.contains(AppDependencyRead.PROJECT_PATH_QUALIFIER);
    }

    @Tool(name="sbom_write_license_reports",
            value =
                    """
                    Write various reports regarding used licenses.
                    """)
    public void writeLicencesReports() throws IOException {
        logger.info("## SBOMWriteLicenseReports()");
        Path listOfLicenseCSVFile = context.getWorkingDirectory().resolve("AllLicenses/AllLicenses.csv");
        Path listOfDependenciesAndLicenseCSVFile = context.getWorkingDirectory().resolve("DependenciesAndLicenses/DependenciesAndLicenses.csv");

        List<License> licenses = getLicenses();
        CsvWriter.CsvWriterBuilder licensesWriter = CsvWriter.builder();

        logger.info("List of licenses: {}",licenses);

        try (CsvWriter csv = licensesWriter.build(listOfLicenseCSVFile)) {
            csv.writeRecord("License");
            licenses.forEach(license -> csv.writeRecord(license.description()));
        }

        List<AppDependencyLicense> dependencies  = getDependencyLicenses();
        CsvWriter.CsvWriterBuilder dependenciesWriter = CsvWriter.builder();

        logger.info("List of dependencies and their licenses: {}",dependencies);

        try (CsvWriter csv = dependenciesWriter.build(listOfDependenciesAndLicenseCSVFile)) {
            csv.writeRecord("Id", "Name", "Version", "Licenses");
            dependencies.forEach(dependency -> csv.writeRecord(dependency.id(),
                    dependency.name(),
                    dependency.version(),
                    dependency.licence()));
        }
    }
}
