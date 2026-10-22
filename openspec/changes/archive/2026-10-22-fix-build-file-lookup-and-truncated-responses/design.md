# Design

## Context

See `proposal.md` - Why for the motivation. What shapes the approach is the following.

### The knowledge exists, but the prompt that needs it does not use it

The analysis carries a fact that maps a build system to the wildcard of its build file:

```
facts/build_system_wildcards.md

| Build System | Variant    | Wildcard Expression |
|--------------|------------|---------------------|
| Maven        |            | "pom.xml"           |
| Gradle       | Classic    | "*.gradle"          |
| Gradle       | Kotlin DSL | "*.gradle.kts"      |
```

Three prompts already include it: `module_purpose.md`, `module_architecture.md`,
`locate_modules.md`. The prompt of the build file analysis, `module_analysis_buildfiles.md`, is the
one that reads the build file itself, and it is the one that does **not** include it. The fact is
therefore available and unused exactly where it is needed.

```
module_analysis_buildfiles.md          module_purpose.md
  (does NOT have the fact)               (has the fact)
        +                                     +
  reads the build file                  lists the directory
```

### The three module tasks that read files, and the tools they get

```
ModulePurpose              filesystem_get_matching_files_in_dir_recursively
ModuleArchitecture         filesystem_get_matching_files_in_dir_recursively
ModuleBuildfileAnalysis    fileio_read_file, filesystem_file_exists
```

The first two list a directory. The third reads a file, and to read the right file it must first
find it, but it has no tool that finds anything. `filesystem_file_exists` answers one path at a
time.

### What the model actually did

From the chat log of a failing module of the 90 module project, attempt 3:

```
-- Message 2 (AI) --
  → Tool fileio_read_file: {"file":"pom.xml"}                    <- root build file, 54 KB
  → Tool fileio_read_file: {"file":"api/maven-api-core/pom.xml"} <- another module's build file
```

The module was `api.maven-api-cli`. Its own build file is 2.9 KB; the root one is 54 KB, so 1457
lines of XML went into the context. After that the model had lost its bearings and probed:

```
"api/maven-api-cli/src/main/java/org/apache/maven/api/cli/Cli.java"
"api/maven-api-cli/src/main/java/org/apache/maven/api/cli/Invoker.java"
"api/maven-api-cli/src/main/java/org/apache/maven/api/cli/InvokerRequest.java"
"api/maven-api-cli/src/main/java/org/apache/maven/api/cli/InvokerException.java"
"api/maven-api-cli/src/main/java/org/apache/maven/api/cli/Logging.java"
```

One tool call per file, until the tool round bound was reached.

The model's own reasoning, recovered by asking the same model the same question with the reasoning
enabled:

```
"Let's check the directory structure. Let's look for build files."
  → filesystem_file_exists: api/maven-api-cli/build.gradle
  → filesystem_file_exists: api/maven-api-cli/build.gradle.kts
  → filesystem_file_exists: api/maven-api-cli/.mvn
```

The model was looking for the build file, because the prompt did not tell it where the build file is.

### The engine does not look at why the model stopped

`ChatResponse.metadata().finishReason()` and `ChatResponse.finishReason()` are never read. A
response that the model cut in the middle of is handled like a complete answer, and the resulting
parse error is reported as `PAYLOAD_NOT_PARSEABLE` - a payload that does not parse. The real cause,
that the answer was cut, is not reported.

The cut is real and reproducible. A direct call with the same prompt, the same schema and the same
tool produced, in one of five trials, a JSON answer whose text stopped in the middle of a word:

```
trial  3: TRUNCATED  reason=<not stop>  len=1831
   tail: 'ry, indicating no other source file types are tracked for this module.'
```

The response used far less of its budget than it had: 4207 input tokens, 765 output tokens, a
context window of 65536. The engine sets `numCtx` to `maximumTokens` and asks for
`maxOutputTokens` of the same size, so the engine is not the one that stopped the model.

### The reasoning of the model is thrown away

`ChatModelFactory` sets `think(false)` and `returnThinking(false)` for the Ollama provider, and the
chat log writes the text of the messages only. The model does write its reasoning, and it is the
part that explains the behaviour above. A reader of a failed step has no way to see it.

## Goals / Non-Goals

**Goals:**

- The build file analysis knows which file to read before it reads anything.
- A step that asks the model for one file after another is recognisable as such in the report.
- A response the model cut short is reported as cut short, not as a malformed answer.
- The reasoning of the model is available in the log of a step.

**Non-Goals:**

- No change to the findings the analysis produces. The same build files are read and judged.
- No change to the tool round bound itself, and no change to the attempt budget.
- No change to the response schemas or the response formats.
- The three tasks that fail for other reasons are not changed. Their failure is intermittent and the
  reporting change makes it visible; the attempt budget already handles it.

## Decisions

### The prompt names the build file, derived from the build system and the module path

**Decision.** The prompt of the build file analysis includes the fact that maps the build system to
the build file wildcard, and tells the model to read the build file of the module it analyses, at
the path of the module.

**Rationale.** The fact exists and is used by the two tasks that read the same kind of file
indirectly. Adding it to this prompt is one line and removes the guessing entirely.

The module path is already in the prompt, through `macros/current_loop_module.md`:

```
The name of the current module to analyze is: "{{name}}"
The path of this build module is: "{{path}}"
```

**Alternatives considered.**

- *A new macro that resolves the build file path.* It would put the whole path, not only the file
  name, into the prompt. The path is a property of the module and the file name is a property of the
  build system, and the prompt already has the path. A macro that joins them would compute in the
  template what the model can do in one line. Rejected as more machinery for the same result, but it
  is the alternative to revisit if the wording of the prompt turns out to be too weak.
- *Let the model keep guessing the file name.* That is the current behaviour and it is the cause of
  the failure.

### The existence check hint is removed

**Decision.** The hint that tells the model to check that a file exists before it reads it is
removed from the prompt.

**Rationale.** The hint is a direct instruction to spend a tool call per candidate file, and the
bound on the tool rounds of an attempt is what the model runs into. The build file of the module is
known after the first decision, so the check is not needed for it. The hint is the mechanism that
turned one wrong guess into thirty tool calls.

**Alternatives considered.**

- *Keep the hint and raise the bound further.* The bound is already at 30 for this workspace and the
  model still reached it, because the model probes without end when it is told to probe. Raising the
  bound treats the symptom of a step that asks for too much.
- *Keep the hint for the files of the module below the build file.* The directory listing tool
  covers those.

### The build file analysis gets the directory listing tool

**Decision.** `filesystem_get_matching_files_in_dir_recursively` joins the tools of the build file
analysis.

**Rationale.** The task has to read the build file, so it needs `fileio_read_file`, and it has to
find the build files of the module, so it needs a way to find files. The listing tool answers "which
files of this name are in this directory" in one call, and it is the tool the two other module tasks
already use for exactly this. The whitelist stays narrow: two tools that find files and one that
reads them.

**Alternatives considered.**

- *A new tool that returns the build file of a module.* The engine would have to know the build
  systems, and the analysis already has that knowledge in a fact file. A tool would duplicate the
  fact in Java and make the two drift apart.
- *Drop `filesystem_file_exists` from the whitelist.* Nothing stops the model from probing with the
  listing tool, but the whitelist no longer offers the tool whose one-path-per-call shape invites
  it. The tool stays available for the model to check a single path it found.

### A response the model stopped in the middle of is reported as truncated

**Decision.** The response handling reads the reason the model stopped. A response that was not
finished is rejected with its own reason, and the message names the reason the model gave.

**Rationale.** The condition is already distinguishable at the point the response arrives, and
reporting it as a parse failure of a half answer sends the reader after the wrong cause. The
message the model sends with the reason is the part that says whether the answer was cut, whether
the model refused, or whether it stopped for another reason, and it belongs in the report.

**Alternatives considered.**

- *Repair the truncated payload and accept it.* A half answer cannot be completed without inventing
  the rest of it, and the invented part would be stored as a finding.
- *Report it as a parse failure but mention the reason in the message.* This keeps the wrong
  condition name in the report and in the repair hint the next attempt is given, so the next attempt
  is told that its answer did not parse rather than that it was cut.

### The reasoning of the model is kept in the chat log of a step

**Decision.** The chat log of a step holds the reasoning of the model next to the messages, and the
reasoning is not part of the payload of the step.

**Rationale.** The reasoning is where the model explains itself, and it is the only place that says
why the model asked for what it asked for. Without it a failed step can only be read as a sequence
of tool calls. The log is the place for it: the log is the artefact a reader opens to understand a
step, and it is already written per attempt.

**Alternatives considered.**

- *Write the reasoning to the engine log.* The engine log is overwritten per run and is meant for
  the diagnostics of the engine, not for the transcript of a step. The reasoning belongs next to the
  messages it belongs to.
- *Ask the model for the reasoning in the answer.* The response schemas are a contract with the
  documentation, and a reasoning property per finding is not the same thing as the reasoning of the
  model about its own tool use.

### The tool round bound reason names the tools the model asked for

**Decision.** The rejection of an attempt that reached the bound names the tools the model asked for
and how often it asked for them.

**Rationale.** "the model requested more than 10 tool round(s)" says that the bound was reached. It
does not say that the model asked for the existence of single files thirty times, which is a
different defect from a model that asks for ten different things. The names make the difference
visible without a reader having to count the tool calls in the transcript.

**Alternatives considered.**

- *Only the number of the tool calls.* Cheaper, but "the model asked for the same file 30 times" and
  "the model asked for 30 different things" are the same count and very different problems.

## Risks / Trade-offs

- **The prompt names the build file only for the build systems the fact file lists.** A project
  with a build system that is not in the fact file gets no name. → The fact file is the same one the
  other prompts use, so a build system that is missing from it is missing from all of them. Adding
  a build system is one row in one file.

- **A model that ignores the named build file may still probe.** A prompt does not forbid a tool
  call. → The tool round bound still applies, and after this change the reason for reaching it names
  the tool, so such a step is recognisable in the report.

- **The reasoning in the chat log makes the logs bigger.** The reasoning of a step is a paragraph or
  two per attempt. → The logs are per attempt and per step and are already the largest artefact of a
  run. The reasoning is what makes them worth reading.

- **Keeping the task means every request carries it.** A step with many tool rounds sends the task
  again with every round. → The task is two messages. The request grows by two messages per round
  instead of losing the task, which is the difference between a step that answers and a step that
  does not.

- **The chat log now holds the whole conversation of an attempt.** The log of a long step is bigger
  than the window. → The log is the artefact a reader opens to understand a step, and a log that
  starts in the middle of a conversation cannot be read.

- **Reporting a truncated response as its own condition changes the failure names in the report.**
  A reader who greps for the parse failure finds it less often. → The new condition is named, and
  the repair hint the next attempt is given names the truncation, so the next attempt is told to
  finish the answer.

- **The change does not make the three intermittently failing tasks reliable.** They fail because
  the model sometimes answers in one piece and sometimes does not. → The attempt budget of the step
  already retries them, and the reporting change makes the cause visible where it was hidden.

## Migration Plan

None needed. The prompt change and the whitelist change are read at the start of a run, and the
response handling change applies to every response. A workspace whose state is already filled keeps
its state; the tasks it has not run yet run with the new prompt.

## Open Questions

None. The decisions that shape the approach - naming the build file in the prompt, dropping the
existence check, giving the task the listing tool, reporting a truncated response as such, keeping
the reasoning in the log, and naming the tools in the bound reason - are settled, and the specs
follow them.
