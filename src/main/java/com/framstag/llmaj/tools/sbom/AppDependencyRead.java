package com.framstag.llmaj.tools.sbom;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * The result of a read of the dependencies of the application from the loaded SBOM.
 *
 * <p>The read can resolve the root of the dependency graph through more than one route, because the tools that
 * write a CycloneDX SBOM do not agree on the entry that describes the root component. The route therefore travels
 * with the dependencies, so that a reader can tell a direct dependency list from a list that rests on a fallback.
 *
 * <p>A list alone could not carry the reason a read found nothing, and the reason is what the consumers of the
 * read need in order to answer without inventing one.
 *
 * @param dependencies the dependencies the read found
 * @param route the route the read took to resolve the root of the dependency graph
 * @param reason why the result is not the direct dependency list of the root component, empty when it is
 * @param componentsInSbom the number of components the SBOM holds
 * @param projectComponentsLeftOut the number of components that are modules of the project and are no dependency
 */
@Description("The result of a read of the application dependencies from the loaded SBOM")
public record AppDependencyRead(
        @Description("The dependencies the read found")
        List<AppDependency> dependencies,
        @Description("The route the read took to resolve the root of the dependency graph: 'root', "
                + "'project-modules', 'components' or 'none'")
        String route,
        @Description("Why the result is not the direct dependency list of the root component, empty when it is")
        String reason,
        @Description("The number of components the SBOM holds")
        int componentsInSbom,
        @Description("The number of components that are modules of the project and are therefore no dependency")
        int projectComponentsLeftOut) {

    /** The direct route: the graph holds an entry for the reference of the root component. */
    public static final String ROUTE_ROOT = "root";

    /** The fallback route: the graph holds one entry per project module of an aggregator build. */
    public static final String ROUTE_PROJECT_MODULES = "project-modules";

    /** The fallback route for an SBOM whose graph cannot be resolved: its components are reported. */
    public static final String ROUTE_COMPONENTS = "components";

    /** No SBOM was loaded, so no route was taken. */
    public static final String ROUTE_NONE = "none";

    /**
     * The qualifier a reference of an aggregator build carries for the project module it belongs to. The plugin
     * that writes such a build puts it into the reference of every module component, which is what the read keys
     * on, and which is why a module component can be told apart from a library without a further rule.
     */
    static final String PROJECT_PATH_QUALIFIER = "project_path=";

    /**
     * @return true when the dependencies are a direct dependency list, which the routes of the root component and
     *         of the project modules are, and which the component route is not
     */
    public boolean isDirectDependencyList() {
        return ROUTE_ROOT.equals(route) || ROUTE_PROJECT_MODULES.equals(route);
    }

    /**
     * @return true when the read resolved the graph through a route other than the one of the root component, or
     *         when it found no dependency at all
     */
    public boolean statesReason() {
        return reason != null && !reason.isBlank();
    }
}
