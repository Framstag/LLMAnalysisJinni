# AGENTS.md — AI Agent Guide for LLMAnalysisJinni

## Project

LLMAnalysisJinni is a Java/Maven engine for scriptable LLM-based code analysis. It runs a DAG of small tasks instead of one huge prompt. Each task can use Handlebars prompts, JSON Schema responses, tag dependencies, loops, and MCP tool access.

Current domain: `analysis/software-architecture/`.

## Repo map

```text
.
├── AGENTS.md
├── LICENSE
├── Models.md
├── README.md
├── LLMAnalysisJinni.iml          # local IDE file
├── mise.toml
├── mise.local.toml               # local Mise overrides
├── openspec/
├── analysis/software-architecture/
├── examples/
├── guidelines/
│   ├── CodeStyles.md
│   └── TestApproach.md
├── src/
├── target/                       # generated build output
└── workspaces/                   # local analysis workspaces
```

## Main Java areas

```text
src/main/java/com/framstag/llmaj/
  Main.java               # Picocli entry point
  AnalysisContext.java    # project/workspace/state context
  cli/                    # workspace, analyse, document, state, tools
  config/                 # config.json load/store
  handlebars/             # prompt/result template factory
  json/                   # Jackson/ObjectMapper helpers
  lc4j/                   # LangChain4j execution and tool filtering
  state/                  # analysis.json persistence and loop state
  tasks/                  # YAML task loading, DAG scheduling, state.json
  tools/                  # MCP tool registration and implementations
```

## Guidelines

Do not duplicate project rules here. Use:

- `guidelines/CodeStyles.md`
- `guidelines/TestApproach.md`
- `Models.md`
- `README.md`
- `analysis/software-architecture/README.md`

## Environment

```bash
mise install
```

Stack:

- Java 25
- Maven 3
- Node 22
- Pandoc

## Build

```bash
mvn verify -DskipTests
mvn verify
```

First run generates SBOM artifacts used by tests.

## CLI

### Init workspace

```bash
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- \
  workspace init \
  --modelProvider=OLLAMA \
  --modelUrl=http://localhost:11434 \
  --model="qwen2.5:7b" \
  -j=true \
  --project=<project-dir> \
  --analysis=analysis/software-architecture \
  <workspace-dir>
```

Writes `config.json` into the workspace.

### Analyse

```bash
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- analyse <workspace-dir>
```

Outputs:

- `analysis.json`
- `state.json`

### Document

```bash
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- document <workspace-dir>
```

Outputs `Documentation.md`.

### State helpers

```bash
# show task state
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- state dump <workspace-dir>

# clear task state, keep analysis.json
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- state clear <workspace-dir>

# drop selected task states
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- state drop <workspace-dir> TaskId

# run selected active tasks only
mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -- analyse -o TaskId <workspace-dir>
```

## Analysis pipeline

`analysis/software-architecture/tasks.yaml` defines the DAG. Rough flow:

1. tool/version
2. README locate
3. project summary
4. build systems
5. module discovery
6. per-module purpose/architecture/build/language analysis
7. SBOM load/dependency/license/technology analysis
8. file statistics/code size
9. Java parser reports
10. Java metric evaluations
11. documentation render

See `analysis/software-architecture/README.md` for task table and quality ratings.

## Task YAML

New analysis features usually need only YAML, prompts, and schemas.

```yaml
---
id: MyTaskId
name: Human-readable name
systemPrompt: prompts/systemprompt.md
prompt: prompts/my_task.md
responseFormat: results/MySchema.json
responseProperty: myProperty
active: true
dependsOn:
  - some_tag
tags:
  - my_tag
loopOn: /modules/modules
toolWhitelist:
  - filesystem_get_all_files_in_dir
```

Rules:

- `dependsOn` references tags, not task IDs.
- `tags` unlock dependent tasks.
- `active` defaults to `true`.
- `loopOn` is a JSONPath into `analysis.json`.
- `responseProperty` stores the result.
- `toolWhitelist` / `toolBlacklist` are regex filters.

Validation checks required fields, tag references, dependency cycles, active `executeOnly` targets, and JSON Schema syntax.

## Prompt model

Handlebars templates can use:

- `context` — project root, workspace, config, analysis state
- `analysis` — `analysis.json`
- `currentLoopElement` — current loop item
- `loopIndex` — current loop index
- partials like `{{> macros/list_of_modules}}`

Template root is the active analysis directory.

## LLM execution

Each task expects JSON matching `responseFormat`.

Runtime:

- prompt is patched with schema description
- tool calls may happen before final answer
- Ollama can use native JSON when `-j=true`
- OpenAI uses tool calls first, then final JSON-only call
- JSON is extracted, parsed with Jackson, then schema-validated
- schema violations currently log warnings and do not stop execution

Do not assume invalid JSON aborts a run.

## MCP tools

Registered in `ToolFactory.java`:

- `info`
- `fileio`
- `filesystem`
- `sbom`
- `filestatistics`
- `java`

Use narrow tool whitelists. Broad wildcards need justification.

Adding a new MCP tool requires Java changes.

## State

Workspace:

```text
<workspace-dir>/
  config.json     # model, paths, runtime options
  analysis.json   # accumulated analysis results/context
  state.json      # task execution state
```

Task states:

- `PENDING`
- `PROCESSING`
- `SUCCESSFUL`
- `FAILED`

Successful task tags unlock dependents. Loop tasks remember successful indices and skip them on rerun.

## Add analysis domain

Create:

```text
analysis/my-domain/
  tasks.yaml
  prompts/
  results/
  macros/       # optional
  facts/        # optional
  documentation/# optional
```

Initialize with:

```bash
--analysis=analysis/my-domain
```

For normal analysis tasks, add prompts, schemas, YAML, update domain README, run tests. Add Java only for new tools or engine changes.

## Testing

Use JUnit unit tests. See `guidelines/TestApproach.md`.

Current tests cover JSON helpers, filesystem tools, Java tools, SBOM tools, task definitions, and documentation templates.

## OpenSpec

Use OpenSpec for structured changes.

```text
openspec/config.yaml
openspec/changes/
openspec/specs/
```

Workflow: propose → apply → verify → archive. Keep specs small and testable.

## Common pitfalls

- Do not add Maven deps for analysis logic.
- Put static knowledge in `facts/`, dynamic snippets in `macros/`.
- Use tags in `dependsOn`, not task IDs.
- Every task needs `responseFormat`.
- `active: false` tasks do not run by default.
- JSON schema validation only warns today.
- Use `state clear`/`state drop` for task reruns; do not delete `analysis.json` unless intended.
- Avoid broad tool wildcards.
- Do not change Java parser behavior without tests.

## Models

See `Models.md`.

Useful Ollama models:

- `qwen2.5:7b` — OK, small context
- `qwen3:4b` — bigger context, more creative
- `gpt-oss:20b` — better results, slower

Use `-j=true` when native JSON mode is supported.
