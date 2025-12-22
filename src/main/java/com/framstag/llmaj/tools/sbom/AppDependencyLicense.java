package com.framstag.llmaj.tools.sbom;

import dev.langchain4j.model.output.structured.Description;

@Description("A dependency of an app and its license")
public record AppDependencyLicense (
    @Description("The unique identifier of the dependency in the SBOM")
    String id,
    @Description("Name of the dependency")
    String name,
    @Description("Version of the dependency")
    String version,
    @Description("Licence of the dependency")
    String licence) implements Comparable<AppDependencyLicense> {

    @Override
    public int compareTo(AppDependencyLicense o) {
        return id.compareTo(o.id);
    }
}
