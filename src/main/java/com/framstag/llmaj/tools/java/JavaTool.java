package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.AnalysisContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaTool {
    private static final Logger logger = LoggerFactory.getLogger(JavaTool.class);

    private final AnalysisContext context;

    public JavaTool(AnalysisContext context) {
        this.context = context;
        logger.info("JavaTool initialized.");

    }

}
