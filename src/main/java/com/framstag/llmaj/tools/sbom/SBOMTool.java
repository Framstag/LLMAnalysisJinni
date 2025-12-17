package com.framstag.llmaj.tools.sbom;

import com.framstag.llmaj.AnalysisContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.parsers.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

    public SBOMTool(AnalysisContext context) {
        this.context = context;
    }

    @Tool(name = "SBOMIsAlreadyLoaded",
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

    @Tool(name="LoadSBOMFromFile",
            value =
            """
                Loads the SBOM file with the given filename.
                 You must call this tool, before you can access the information stored in the file.
            """)
    public String loadSBOM(@P("SBOM filename") String filename) {
        logger.info("## LoadSBOMFromFile('{}')", filename);

        File file = Path.of(context.getProjectRoot()).resolve(filename).toFile();

        if (!file.exists()) {
            logger.error("## LoadSBOMFromFile() => File '{}' does not exist", filename);

            return "Error";
        }

        var parser = new JsonParser();

        try {
            logger.info("## LoadSBOMFromFile() loading '{}'...", filename);
            bom = parser.parse(file);
        } catch (ParseException e) {
            logger.error("## LoadSBOMFromFile() => Failed to parse SBOM file '{}'", filename, e);

            return "Error";
        }

        logger.info("## LoadSBOMFromFile() => 'OK'");

        return "OK";
    }

    @Tool(name="SBOMApplicationInfo",
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

    @Tool(name="SBOMApplicationDependencyLicences",
            value =
                    """
                    Return the various licences the application dependencies use.
                    """)
    public List<License> getApplicationDependencyLicences() {
        logger.info("## getApplicationDependencyLicences()");

        assertSBOMLoaded();

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

    @Tool(name="SBOMApplicationDependencies",
            value =
                    """
                    Return all direct dependencies of the application from the loaded SBOM file.
                    """)
    public List<AppDependency> getApplicationDependencies() {
        logger.info("## SBOMApplicationDependencies()");

        assertSBOMLoaded();

        String id = bom.getMetadata().getComponent().getBomRef();

        logger.info("Root component has bom-ref '{}'", id);


        Map<String,Dependency> dependencyMap = bom.getDependencies()
                .stream()
                .collect(Collectors.toMap(Dependency::getRef, Function.identity()));

        Map<String,Component> componentMap = bom.getComponents()
                .stream()
                .collect(Collectors.toMap(Component::getBomRef, Function.identity()));

        logger.info("Finding dependency entry for root component ...");

        Dependency rootDependency = dependencyMap.get(id);

        if (rootDependency == null) {
            logger.error("No root dependency found in SBOM for id '{}'", id);
            return List.of();
        }

        logger.info("Finding direct dependencies of root component ...");

        return  rootDependency.getDependencies()
                .stream()
                .map(dependency -> componentMap.get(dependency.getRef()))
                .map(component -> new AppDependency(
                        component.getBomRef(),
                        concatinateGroupIdAndName(component.getGroup(),component.getName()),
                        component.getVersion()))
                .collect(Collectors.toList());
    }
}
