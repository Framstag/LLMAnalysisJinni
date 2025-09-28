package com.framstag.llmaj.tools.sbom;

import dev.langchain4j.model.output.structured.Description;

@Description("A dependency of an app")
public record AppDependency(
    @Description("The unique identifier of the dependency in the SBOM")
    String id,
    @Description("Name of the dependency")
    String name,
    @Description("Version of the dependency")
    String version) {

}
