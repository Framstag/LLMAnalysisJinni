# Design

## Context

See `proposal.md` for the evidence from the Maven run. The constraints that shape the approach:

- `JsonHelper.getObjectDescription` renders the format description of the response schema into the
  prompt. It recurses into `properties` and, for an array whose `items` object has a `title`, into
  those items; enum values are rendered only on the non-recursive branch, so a property whose value is
  an object or an untitled array loses its type detail, its `required` list and its enum values.
- The description is injected in both JSON modes (native JSON and text), so it is the only place the
  model learns the shape of the answer. In native JSON mode the formal schema additionally travels in
  `responseFormat`, but the run's model is an Ollama model, whose native JSON support is a grammar hint
  rather than a full schema.
- `ChatExecutor.evaluateResponse` validates with networknt (`Dialects.getDraft202012()`) and maps the
  result with `Error::getMessage` only. networknt exposes the instance location and the evaluated
  path, and its messages follow the JVM default locale.
- The repair hint is composed where the schema description is composed, and `add-task-step-retries`
  already bounds it in implementation terms; the bound is not yet a stated contract.
- `SBOMLocation` uses the shared `results/Location.json` schema, which already carries `found` and
  `path`, and the run recorded `found: false`. `LoadSBOM` then has an empty path, and the SBOM tools
  answer `No SBOM loaded` or `Error`.
- Tool failures are already returned to the model as tool results instead of failing the step, so a
  missing SBOM cannot fail a step by itself; what fails a task is a response that violates a schema.
- `JavaFileParser` and `ClassFileParser` log an `ERROR` with the exception per file; the module report
  they build already records the files it could not parse.

## Goals / Non-Goals

**Goals:**

- The model sees the whole response shape, including nested item properties, `required` and enums.
- A schema of the domain can always be satisfied by an answer the prompt can describe.
- A violation report is actionable: it names the location and the value, in one language on every
  machine, and the repair hint stays bounded.
- A project without an SBOM produces a complete analysis with stated reasons instead of rejections.
- The log contains the diagnostics that need a human, and the model-recoverable conditions do not.

**Non-Goals:**

- No change to when a response is accepted or retried (`add-task-step-retries` owns that).
- No conditional task skip mechanism in the engine (recorded as an Open Question).
- No change to the tool-round budget, and no new default for `maxToolRoundTrips`.
- No change to the Java parser's results: what it reports about a file stays as it is, only how it is
  logged changes.
- No change to `results/ModuleBatchEvaluation.json` itself: the enum is fine, the description was not.

## Decisions

### Decision 1: The description expands untitled item schemas and names required properties

`getObjectDescription` recurses into an array's `items` whenever those items describe an object, with
or without a `title`; the `title` only decides whether the rendering says `array of <Title>`. Each
object level additionally renders the names the schema requires, so the model can satisfy `required`
even when it is not spelled out field by field. The enum rendering that exists today stays and is
reachable from every level.

Alternatives considered:

- **Add the missing `title` to `ModuleBatchEvaluation.json`.** Rejected: it repairs one schema and
  leaves the trap for the next one; the `title` is a cosmetic name, and the description must not
  depend on it.
- **Put the formal schema into the prompt as JSON.** Rejected: it multiplies prompt size, and the
  existing rendering exists precisely to keep the prompt cheap. A future option, not this change.
- **Render `required` as a separate sentence per object.** Rejected as the only mechanism: the field
  list is what the model reads line by line, so each property line stays the primary place.

### Decision 2: A domain schema must declare what it requires

`results/TechnologyStack.json` loses the undeclared `architecture` requirement. The choice between
declaring the property and dropping the requirement is decided by the prompt: the prompt asks for a
reasoning, a summary, the technologies and the derivations, and the model answered exactly that in all
three attempts, so the requirement is the defect. A repository test walks every
`analysis/*/results/*.json` and fails when a `required` name has no `properties` entry, which is the
part that keeps the defect from returning with the next schema.

Alternative considered: validate the schema at task load time and refuse to run the task.
Rejected: the engine cannot know whether an undeclared required property is a mistake or an
intentional use of `additionalProperties`, so refusing a run at load time would turn a domain typo
into an engine error; the domain-side test reports it where it is cheap to fix.

### Decision 3: The violation report carries location and value, and is locale-independent

The report is built from the validator's own error objects: the instance location, the evaluated
property, the rejected value, and the keyword. Enum violations name the permitted values. The message
text is produced independently of `Locale.getDefault()`, so the same payload yields the same report on
every machine, and the German messages of the Maven run cannot appear again.

Alternatives considered:

- **Keep the validator's message and only add the location.** Rejected: the message is the part that
  changes with the locale, and mixing a translated message with an engine-made location gives an
  unstable report that is hard to test.
- **Report a structured list into the chat log instead of the engine log.** Rejected: the report has to
  be visible while the run is watched, and the chat log of a rejected attempt is a file, not a live
  channel.

### Decision 4: The repair hint is bounded and states the total

The hint carries at most a fixed number of violations and a fixed number of characters, and states how
many violations the response had. The bound is a constant, chosen in implementation; the total count is
what tells the model whether the repair is one field or the whole shape.

Alternative considered: carry all violations, as today. Rejected: a 220-violation report is a prompt
block that costs more than the repair and can push a context window that was already close to its
limit.

### Decision 5: An absent SBOM is answered with an empty result

The chain keeps its shape: `SBOMLocation` records `found: false` and the path it searched for, the load
task reports that it did not load anything (its result is not SBOM data), and the tool call without a
file names the missing input so the model can stop asking. The tasks that depend on SBOM data answer
with an explicitly empty result plus the reason, which is what the Maven run already produced for the
technology stack. The prompts and, where a schema forbids it, the schemas of those tasks are adjusted
so that the empty answer is a valid answer.

Alternatives considered:

- **Skip the SBOM-dependent tasks when no SBOM is present.** Rejected for this change: the engine has
  no conditional task mechanism, and adding one is an engine capability of its own (recorded as an
  Open Question). It is also not clearly better: "no dependency data" is a finding a document can
  render, while a skipped task is silence.
- **Fail the load task, so dependents stay blocked.** Rejected: the run would lose the license and
  technology sections for a project that simply does not ship an SBOM, and a blocked task looks like a
  defect to a reader of the docs.

### Decision 6: Tool diagnostics follow what the caller can do about the condition

A condition the model can act on (a path that does not exist, arguments to correct) is a tool result,
logged at most once per run at `INFO`/`DEBUG`, without a stack trace. A condition a module report can
count (a source or class file that cannot be parsed) is logged at most once per module below `ERROR`,
with the per-file detail at `DEBUG` and the exception available for the diagnosis. Genuine tool defects
(unexpected exceptions of the tool itself) stay `ERROR`.

Alternatives considered:

- **Filter the records in the logging configuration.** Rejected: a per-component rule in `logback.xml`
  cannot express "once per module" or "the same condition once per run", and it would also silence the
  genuine defects of those components.
- **Drop the records entirely.** Rejected: the count of unparsable files is diagnostic information, and
  a user who wonders why a module's metrics are thin needs it.

## Risks / Trade-offs

- **A bigger prompt.** The expanded description adds lines for nested structures; it is bounded by the
  schema, and the affected schemas are the ones whose answers were rejected, so the trade is prompt
  tokens against a rejected attempt plus a repair attempt.
- **Schema descriptions can now be long for deeply nested schemas.** Mitigated by the nesting bound
  chosen in implementation; recorded as an Open Question.
- **Dropping the `architecture` requirement changes what `TechnologyStack` produces.** The stored
  property keeps its other fields; a consumer that reads `architecture` from `analysis.json` would
  find nothing, and no such consumer exists in the domain's templates (to be confirmed in the tasks).
- **The report format changes what a user sees in the log.** Messages are English and carry a path;
  a familiar German message disappears, which is the point.
- **Empty results look like missing analysis in the documentation.** The prompts require a stated
  reason, and the documentation template renders it; a reader sees why the section is empty.
- **Lowering the level of parse failures can hide a real regression of the parser.** Mitigated by the
  per-module count that stays visible and by the existing parser tests.

## Migration Plan

- No configuration or state migration. The stored `analysis.json` of the Maven workspace keeps its
  properties; a re-run of the affected tasks writes the corrected shapes.
- A workspace with a `FAILED` `TechnologyStack` re-executes that task on the next run, because a failed
  task is retried.
- Rollback requires reverting the domain files and the code; the schema description change has no
  configuration switch.

## Open Questions

- The exact nesting bound of the rendered description, and whether deep nesting should be summarised
  instead of expanded.
- The bounds of the repair hint (violations and characters), and whether the chat log should keep the
  full violation list while the hint stays bounded.
- Whether the SBOM chain should be restructured (the tool reads the location from the state instead of
  the model asking), which would remove the empty-file call and the `LoadSBOM` task.
- Whether a task whose input does not exist in the project should be skipped rather than executed for
  an empty result; that needs a conditional task mechanism in the engine.
- Whether the parse-failure count belongs in the module report as a separate field, so that a thin
  metric can be explained without reading the log at all.
