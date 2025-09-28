package com.framstag.llmaj.tools.sbom;

import dev.langchain4j.model.output.structured.Description;

@Description("A license of a dependency in the SBOM")
public record License(
    @Description("A unique identifier for the license, a list of such identifier or even an expression for alternative licenses ")
    String description
    ) {

}
