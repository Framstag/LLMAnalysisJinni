package com.framstag.llmaj.smoke;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.tools.sbom.SBOMTool;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Runs inside the classpath of the packaged artefact and reports capabilities that the
 * unit tests cannot observe, because they run on the full dependency classpath:
 * <ul>
 *     <li>XML parsers obtained through the platform factory mechanism</li>
 *     <li>every {@code META-INF/services} provider class declared in the artefact</li>
 *     <li>parsing an SBOM document through the SBOM tool</li>
 * </ul>
 * Both are resolved at runtime, so a packaging step that removes unreferenced classes
 * breaks them without breaking compilation. Results are written to standard output in
 * {@code KEY=value} form; the process exit code is non-zero when a check fails.
 * <p>
 * Started by {@link JarSmokeIT} as a separate process with the packaged artefact and the
 * test classes on the classpath.
 */
public final class JarSmokeProbe {

    private static final String XML_INPUT_FACTORY_KEY = "XMLInputFactory=";
    private static final String XML_OUTPUT_FACTORY_KEY = "XMLOutputFactory=";
    private static final String CHECKED_KEY = "SERVICE_PROVIDERS_CHECKED=";
    private static final String UNRESOLVED_KEY = "SERVICE_PROVIDER_UNRESOLVED=";
    private static final String MISSING_KEY = "SERVICE_PROVIDER_MISSING=";
    private static final String SBOM_KEY = "SBOM_PARSE=";
    private static final String SBOM_DEPENDENCIES_KEY = "SBOM_DEPENDENCIES=";
    private static final String FAILURE_KEY = "FAILURE: ";

    private static final String DEFAULT_SBOM_FILE = "target/bom.json";
    private static final String SBOM_OK = "OK";

    private static final String SERVICE_PREFIX = "META-INF/services/";

    private JarSmokeProbe() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.out.println(FAILURE_KEY + "usage: JarSmokeProbe <packaged-jar> [sbom-file]");
            System.exit(1);
        }

        Path jarPath = Path.of(args[0]);
        String sbomFile = args.length == 2 ? args[1] : DEFAULT_SBOM_FILE;
        List<String> failures = new ArrayList<>();

        reportXmlFactory(XML_INPUT_FACTORY_KEY, XMLInputFactory::newInstance, failures);
        reportXmlFactory(XML_OUTPUT_FACTORY_KEY, XMLOutputFactory::newInstance, failures);
        reportSbom(sbomFile, failures);

        List<String> providerClasses = declaredServiceProviders(jarPath);
        List<String> missingProviders = new ArrayList<>();
        List<String> unresolvedProviders = new ArrayList<>();

        for (String providerClass : providerClasses) {
            if (!isDeclaredInClasspath(providerClass)) {
                // The artefact declares a provider but does not contain it. Compilation cannot
                // notice this, so it is exactly the defect this probe exists for.
                missingProviders.add(providerClass);
            } else if (!isLoadable(providerClass)) {
                // Present, but one of its dependencies (an optional API, for example the
                // servlet API) is not on the classpath. This behaves the same way when the
                // program runs from the full classpath, so it is reported, not failed.
                unresolvedProviders.add(providerClass);
            }
        }

        System.out.println(CHECKED_KEY + providerClasses.size());

        for (String unresolvedProvider : unresolvedProviders) {
            System.out.println(UNRESOLVED_KEY + unresolvedProvider);
        }

        for (String missingProvider : missingProviders) {
            System.out.println(MISSING_KEY + missingProvider);
            failures.add("declared service provider is not in the artefact: " + missingProvider);
        }

        for (String failure : failures) {
            System.out.println(FAILURE_KEY + failure);
        }

        System.out.flush();

        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    private interface FactorySupplier {
        Object get();
    }

    private static void reportXmlFactory(String key, FactorySupplier supplier, List<String> failures) {
        try {
            Object factory = supplier.get();
            System.out.println(key + factory.getClass().getName());
        } catch (Throwable t) {
            System.out.println(key);
            failures.add("cannot create " + key.replace("=", "") + ": " + t);
        }
    }

    private static void reportSbom(String sbomFile, List<String> failures) {
        try {
            AnalysisContext context = new AnalysisContext(Path.of("").toAbsolutePath(),
                    Path.of("").toAbsolutePath(), Collections.emptyMap(), null);
            SBOMTool sbomTool = new SBOMTool(context);

            String status = sbomTool.loadSBOM(sbomFile);

            System.out.println(SBOM_KEY + status);

            if (!SBOM_OK.equals(status)) {
                failures.add("SBOM document '" + sbomFile + "' could not be parsed: " + status);
                return;
            }

            int dependencyCount = sbomTool.getApplicationDependencies().size();

            System.out.println(SBOM_DEPENDENCIES_KEY + dependencyCount);

            if (dependencyCount <= 0) {
                failures.add("parsed SBOM document '" + sbomFile + "' contains no dependencies");
            }
        } catch (Throwable t) {
            System.out.println(SBOM_KEY + "ERROR");
            failures.add("SBOM parsing failed: " + t);
        }
    }

    private static List<String> declaredServiceProviders(Path jarPath) throws IOException {
        // A provider class can be declared by more than one service file, so it is reported once.
        List<String> providers = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory() || !entry.getName().startsWith(SERVICE_PREFIX)) {
                    continue;
                }

                String providerClass = entry.getName();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        String providerClassName = line.trim();

                        if (providerClassName.isEmpty() || providerClassName.startsWith("#")) {
                            continue;
                        }

                        providers.add(providerClassName);
                    }
                }
            }
        }

        return providers.stream().distinct().toList();
    }

    private static boolean isDeclaredInClasspath(String className) {
        String resourceName = className.replace('.', '/') + ".class";

        return JarSmokeProbe.class.getClassLoader().getResource(resourceName) != null;
    }

    private static boolean isLoadable(String className) {
        try {
            Class.forName(className, false, JarSmokeProbe.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
