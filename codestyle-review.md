少爷，先读规矩。

<｜DSML｜tool_calls>
<｜DSML｜invoke name="read">
<｜DSML｜parameter name="filePath" string="true">/home/tim/projects/LLMAnalysisJinni/guidelines/CodeStyles.md</｜DSML｜parameter>
</｜DSML｜invoke>
<｜DSML｜invoke name="execute">
<｜DSML｜parameter name="command" string="true">git diff --cached --diff-filter=M -- src/main/java/com/framstag/llmaj/tools/java/JavaTool.java src/test/java/com/framstag/llmaj/tools/java/JavaToolTest.java 2>/dev/null || git diff HEAD -- src/main/java/com/framstag/llmaj/tools/java/JavaTool.java src/test/java/com/framstag/llmaj/tools/java/JavaToolTest.java</｜DSML｜parameter>
<｜DSML｜parameter name="timeout" string="false">10000</｜DSML｜parameter>
</｜DSML｜invoke>
</｜DSML｜tool_calls>