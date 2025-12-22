package com.framstag.llmaj.tools;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.tools.file.FileTool;
import com.framstag.llmaj.tools.filestatistics.FileStatisticsTool;
import com.framstag.llmaj.tools.info.InfoTool;
import com.framstag.llmaj.tools.java.JavaTool;
import com.framstag.llmaj.tools.sbom.SBOMTool;

import java.util.List;

public class ToolFactory {
    public static List<Object> getToolInstanceList(AnalysisContext context) {
        InfoTool infoTool = new InfoTool(context);
        FileTool fileTool = new FileTool(context);
        SBOMTool sbomTool = new SBOMTool(context);
        FileStatisticsTool fileStatisticsTool = new FileStatisticsTool(context);
        JavaTool javaTool = new JavaTool(context);

        return  List.of(infoTool, fileTool, sbomTool,fileStatisticsTool, javaTool);
    }
}
