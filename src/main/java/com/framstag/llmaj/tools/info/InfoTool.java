package com.framstag.llmaj.tools.info;

import com.framstag.llmaj.AnalysisContext;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfoTool {
    private static final Logger logger = LoggerFactory.getLogger(InfoTool.class);

    private final AnalysisContext context;

    public InfoTool(AnalysisContext context) {
        this.context = context;
        logger.info("InfoTool initialized.");
    }

    @Tool(name = "ToolsVersion", value = "Returns the version of the tools")
    public String getVersion() {
        logger.info("## ToolsVersion()");

        logger.info("## ToolsVersion() => '{}'", context.getVersion());
        return context.getVersion();
    }
}
