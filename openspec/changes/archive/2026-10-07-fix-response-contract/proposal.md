# Proposal

## Why

The same Apache Maven run that produced the loop-task flood (`fix-concurrent-loop-execution`) also
rejected model answers it did not have to reject, failed a task that could never pass, and hid its own
diagnostics in log noise.

**1. The response format description does not describe nested structures.** The run recorded 17
rejections, most of them with error counts between 154 and 220, all of the same shape. For
`VisibilityEvaluationAll`, attempt 1 was rejected with 178 violations; the model had returned
`"urgency": "low"` 77 times and `"medium"` 12 times (and the same for `criticality`) while
`results/ModuleBatchEvaluation.json` declares `enum: ["NONE", "LOW", "MEDIUM", "HIGH"]`. The reason is
what the engine told the model to produce:

```text
"reasoning": (Overall reasoning for this metric across all modules; type: string),
"moduleEvaluations": (One evaluation entry per affected module; type: array)
```

`JsonHelper.getObjectDescription` (`JsonHelper.java:45-53`) expands an array's item schema only when
that item schema carries a `title`. `ModuleBatchEvaluation.json` has none, so the nested properties,
their `required` list and their enum values never reach the prompt. The model guesses the spelling,
every entry violates the schema, the whole answer is rejected, and the repair attempt costs a second
model call. The same latent defect exists in every schema with untitled item objects.

**2. One task can never pass.** `results/TechnologyStack.json` lists `architecture` in `required` and
does not declare it. All three attempts of `TechnologyStack` were rejected with `erforderliche
Eigenschaft 'architecture' nicht gefunden`, the task is `FAILED` in `state.json`, and the prompt never
mentions such a property, because the description is rendered from `properties`. The dependency on the
SBOM tag is intact, so a correct answer of the model (an empty technology list with a reason) can
never be stored. Checked over all schemas of the domain, this is the only schema whose `required` list
names a property it does not declare.

**3. A violation report cannot be acted on.** `ChatExecutor.java:553-555` maps violations to
`Error::getMessage` only: no location inside the payload, no rejected value, and the message text
depends on the machine locale (the run recorded German messages such as
`erforderliche Eigenschaft 'architecture' nicht gefunden`). The repair hint carries the same text, and
it is unbounded: a 220-violation report is injected into the next attempt as one prompt block.

**4. An absent SBOM is not treated as a result.** The project has no SBOM, and the run recorded that
correctly (`"sbom": {"found": false, "path": ""}`), but the load task still claimed the `sbom` tag
(`"sbom_loaded": {"result": "ERROR"}`) after being called with an empty file name, and the sbom
dependent tasks then spent their attempt budgets against tools that answered `No SBOM loaded`
(9 tool failures). The technology-stack answer was, in substance, right - an empty list plus a reason
naming the missing dependency data - and only the schema defect of point 2 rejected it.

**5. Recoverable conditions are reported as errors.** A tool call that named `README` instead of
`README.md`, and 2,920 source plus 210 class files that cannot be parsed (mostly under
`its/core-it-suite/src/test/resources`, where broken sources are the point), each produced an `ERROR`
record with a stack trace: `FileIOTool`, `JavaFileParser:272`, `ClassFileParser:148`. The model
recovered from the missing file by itself, the affected module reports still list the unparsable
files, and the only effect of the records was to bury the real diagnostics.

## What Changes

- **The response format description covers the whole schema.** The description rendered into the
  prompt SHALL describe every property of every nested object and array item at any depth, whether or
  not the item schema carries a `title`; it SHALL name the properties the schema requires and the
  values an enumerated property permits. `JsonHelper` stops treating an untitled item schema as opaque.
- **A response schema declares every property it requires.** The schemas of an analysis domain SHALL be
  self-consistent, `results/TechnologyStack.json` is fixed (the undeclared `architecture` requirement is
  removed, so an answer the prompt can describe is a valid answer), and a repository test walks every
  `analysis/*/results/*.json` so the defect cannot come back.
- **A violation report identifies location and value, in a locale-independent language.** Each reported
  violation names where the offending value sits in the payload and what the offending value was (or
  that it is missing), the permitted values for an enum, and the report does not depend on the
  machine's default locale. The report stays a `WARN` record per violation, and the repair hint is
  bounded in violations and characters while stating the total count.
- **An absent SBOM is a result, not a failure.** The task that locates the SBOM already records
  `found: false`; the load task SHALL NOT claim SBOM data when none was found, a call to an SBOM tool
  without a file SHALL name the missing input in its tool result, and the tasks that depend on SBOM
  data SHALL answer with an explicitly empty result and a stated reason instead of being rejected for
  data that does not exist. The behaviour for a project that has an SBOM is unchanged.
- **A condition the model can correct is not an error.** A tool call that names a path which does not
  exist, and a source or class file that cannot be parsed, SHALL NOT produce an `ERROR` record with a
  stack trace. The tool result still tells the model what went wrong, and the module report still
  records the unparsable files; the log records these conditions at most once per run (missing input)
  or at most once per module (unparsable files), below `ERROR`, with per-file detail at `DEBUG`.
- **No change to task scheduling, loop handling, the DAG, the state format or the engine log file
  bound** (the last one belongs to `fix-concurrent-loop-execution`).

## Capabilities

### New Capabilities

- `response-schema-contract`: how a response schema is turned into the prompt's format description
  (nested objects and array items at any depth, `required` properties, enumerated values) and the rule
  that a domain's response schemas declare every property they require.
- `sbom-optional-analysis`: how a project without an SBOM is analysed - the absence is recorded, the
  load task does not claim data, the SBOM tools name the missing input, and the dependent tasks produce
  an explicit empty result with a reason.
- `tool-diagnostic-levels`: which conditions an analysis tool reports as an error, and which it reports
  as a condition the model can act on.

### Modified Capabilities

- `llm-response-schema-validation`: a new requirement "A violation report identifies where and what was
  rejected" defines the content and the locale independence of the report; a new requirement "The
  repair hint is bounded" defines the bound and the total count. The existing requirements about when a
  response is accepted are left untouched, because `add-task-step-retries` replaces two of them and
  this change does not depend on that outcome.

## Impact

Affected code:

- `json/JsonHelper.java` - the schema description: expand array items and nested objects regardless of
  `title`, name the `required` properties, keep the existing enum rendering, bound the nesting.
- `lc4j/ChatExecutor.java` - the violation report: instance location, rejected value, locale-independent
  message, and the bound on the repair hint.
- `tools/sbom/*` - a call that has no file to load names the missing input.
- `tools/file/FileIOTool.java`, `tools/java/JavaFileParser.java`, `tools/java/ClassFileParser.java` -
  diagnostic levels and the per-module aggregation.
- analysis domain: `analysis/software-architecture/results/TechnologyStack.json`,
  `analysis/software-architecture/prompts/description_of_stack.md`, the license prompt if it needs the
  same tolerance, and the domain README where the tasks are described.
- tests: new tests for the schema description over the domain's schemas, for the required-property
  consistency of every domain schema, for the violation report content and locale, for the bounded
  hint, for the SBOM-less chain, and for the tool diagnostic levels.

Affected docs: `analysis/software-architecture/README.md` (SBOM-less behaviour, technology stack),
`AGENTS.md` where response schemas and tool diagnostics are described.

Not affected: the DAG, the scheduler, loop execution, `state.json`, `analysis.json`, the workspace
configuration, the engine log file bound.

Relationship to the other proposal: `fix-concurrent-loop-execution` fixes the blocking defect (loop
cursor ownership, dispatch spin, bounded engine log) that this run also exposed. The two changes touch
disjoint files and can be applied in either order.

## Open Questions

Recorded rather than decided, so design and tasks can settle them:

- Whether `LoadSBOM` should stay a task at all, or whether the SBOM tool should be told the location
  from the state (an engine change) instead of the model asking for an empty file name.
- Whether a task whose data does not exist in the project should be skipped rather than executed to
  produce an empty result, which needs a conditional task mechanism the engine does not have today.
- Whether `ModuleBuildfileAnalysis` indices 70, 72, 74, 78 and 82 exceeded the tool round bound
  (`maxToolRoundTrips: 10`) because their modules need more evidence, or because the prompt lets the
  model walk the directory tree; raising the default is a separate behaviour change and needs its own
  delta.
- How deep the rendered schema description should nest before it becomes a prompt cost rather than a
  guide.
