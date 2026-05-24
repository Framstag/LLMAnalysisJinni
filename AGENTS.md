# AGENTS.md — AI Agent Guide for LLMAnalysisJinni

## What this project is

A **scriptable LLM analysis engine** that performs code architecture analysis through structured, iterative LLM calls instead of monolithic prompts. The engine chains tasks defined in YAML, each with:

- A prompt (Handlebars template, enriched with dynamic context)
- A JSON Schema response format (enforces structured output)
- A dependency graph (tasks wait on tags from other tasks)
- Optional loop support (iterate over analysis substructures e.g., modules)
- Tool whitelist/blacklist (which MCP tools the LLM can call)

## Quickstart for agents

### Build

```bash
mvn verify -DskipTests   # build without tests (generates SBOM under target/)
mvn verify                # build with tests
```

### Run

```bash
# Initialize workspace
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- workspace init \
  --modelProvider=OLLAMA --modelUrl http://localhost:11434 \
  --model "qwen2.5:7b" -j=true -- <project-dir> analysis/software-architecture <workspace-dir>

# Run analysis
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- analyse <workspace-dir>

# Generate docs
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- document <workspace-dir>
```

## Source tree map

```
src/main/java/com/framstag/llmaj/
  Main.java                          # Entry point (picocli)
  AnalysisContext.java               # Mutable analysis state (JSON object node)
  cli/                               # CLI commands
  config/                            # Config loading/storing
  file/                              # File helpers
  handlebars/                        # Handlebars template factory
  json/                              # JSON helpers, ObjectMapper factory
  lc4j/                              # LangChain4j integration
    ChatExecutor.java                # Core: sends prompt to LLM, validates JSON response
    ChatModelFactory.java            # Creates LangChain4j chat models
    ToolFilter.java                  # Filters available MCP tools per task
  state/                             # State persistence (state.json)
  tasks/
    TaskDefinition.java              # YAML -> task POJO
    TaskManager.java                 # DAG scheduler, validation, state I/O
    TaskState.java / TaskStatus.java # Execution state tracking
  tools/
    filesystem/                      # file listing, glob matching, counting
    fileio/                          # read file contents
    filestatistics/                  # file count/type statistics
    info/                            # system info (version)
    java/                            # JavaParser-based analysis (classes, methods, cyclomatic complexity)
    sbom/                            # CycloneDX SBOM parsing
    ToolFactory.java                 # Tool registration
```

```
analysis/software-architecture/
  tasks.yaml              # The main pipeline: task definitions with dependencies
  prompts/                # Handlebars prompt templates (systemprompt.md + per-task)
  results/                # JSON Schema files for LLM response validation
  macros/                 # Shared Handlebars partials (e.g., list_of_modules)
  facts/                  # Static knowledge (e.g., build system wildcards)
  documentation/          # Documentation generation template (Documentation.md.hbs)
```

## Key conventions

### 1. Tasks are YAML, not code

New analysis capabilities require writing:

1. A prompt template in `analysis/<domain>/prompts/`
2. A JSON Schema in `analysis/<domain>/results/`
3. A YAML block in `analysis/<domain>/tasks.yaml`

No Java code needed for basic tasks. Only new MCP tools require Java.

### 2. Task YAML anatomy

```yaml
---
id: MyTaskId
name: Human-readable name
systemPrompt: prompts/systemprompt.md   # shared system prompt
prompt: prompts/my_task.md              # task-specific prompt
responseFormat: results/MySchema.json   # JSON Schema
responseProperty: myProperty            # where to store result in analysis state
active: true
dependsOn:
  - some_tag                            # tags from other tasks
tags:
  - my_tag                              # tags this task publishes
loopOn: /modules/modules                # optional: iterate over JSONPath
toolWhitelist:
  - filesystem_get_all_files_in_dir     # tools LLM may use
```

### 3. State machine

The `TaskManager` tracks tasks via `state.json` in the workspace:

- `PENDING` → `PROCESSING` (while LLM runs) → `SUCCESSFUL` / `FAILED`
- Failed tasks can be retried; `state.json` stores the execution history
- Dependency resolution = tag matching: all `dependsOn` tags must be published by successful tasks before a task runs

### 4. Prompt template model

Prompts use Handlebars (`{{...}}`) and have access to:

- `context` — the full `AnalysisContext` (project root, working dir, properties)
- `analysis` — accumulated JSON analysis state (`analysis.json`)
- `currentLoopElement` — current item when looping
- Includes: `{{> macros/list_of_modules}}` etc.

### 5. LLM response enforcement

All LLM responses must be valid JSON matching the task's schema. The `ChatExecutor` validates with networknt/json-schema-validator. The system prompt enforces this strictly.

### 6. MCP tools

Tools are registered in `ToolFactory.java`. Each tool category provides functions the LLM can call via LangChain4j's tool interface. Tool access per task is controlled via `toolWhitelist`/`toolBlacklist` (regex patterns matching tool names).

## OpenSpec workflow (embedded in repo)

The repository uses OpenSpec for structured change management:

| Directory | Purpose |
|---|---|
| `.ai/mcp/mcp.json` | External MCP server definitions |
| `openspec/` | OpenSpec change specs and workflow config |
| `openspec/` | OpenSpec change specs and workflow config |

When working on changes, use the OpenSpec workflow:
1. `openspec-propose` skill to design a change
2. `openspec-apply-change` skill to implement
3. `openspec-verify-change` skill before archiving
4. `openspec-archive-change` skill to finalize

## Adding a new analysis domain

Create a new directory under `analysis/`, e.g.:

```
analysis/my-domain/
  tasks.yaml
  prompts/
  results/
  macros/     (optional)
  facts/      (optional)
  documentation/  (optional)
```

Then pass `analysis/my-domain` as the analysis directory on `workspace init`.

## Testing

Tests live in `src/test/java/com/framstag/llmaj/`. Currently limited to:

- `JsonHelperTest` — JSON serialization
- `FileToolTest` — filesystem tool behavior
- `SBOMToolTest` — SBOM parsing

## Models

See `Models.md` for tested LLM models. Current recommendation: `qwen2.5:7b` or `gpt-oss:20b` via Ollama. Project uses LangChain4j — supports Ollama, OpenAI, and LocalAI providers.

## Common agent pitfalls

- **Don't add Maven deps for analysis logic** — prompts + schemas + YAML suffice for most new tasks
- **Don't hardcode values** — put static knowledge in `facts/`, dynamic context in `macros/`
- **Don't skip schema validation** — every task MUST have a valid JSON Schema response format
- **Tags = dependency mechanism** — `dependsOn` references tag names, not task IDs
- **State persistence is on by default** — tasks can resume after failure; clear with `state clear` or delete `state.json`