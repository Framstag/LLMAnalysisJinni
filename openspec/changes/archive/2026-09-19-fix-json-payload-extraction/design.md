# Design

## Context

See `proposal.md` - Why for the observed failures.

Current state that shapes the approach:

- `JsonHelper.extractJSON()` is a pure string operation with no knowledge of JSON structure. It
  removes ` ```json ` when the response *starts* with it, removes ` ``` ` when the response *ends*
  with it, then runs a per-line quote heuristic (`fixJsonLine()`), and finally logs the whole
  result at WARN as `Corrected JSON String to: …`.
- The call site is `ChatExecutor.executeMessages()`: the extracted string is parsed with a
  `JsonParser`, then validated against the response schema (validation failures are warnings only,
  see `llm-response-schema-validation`). Only schema validation is wrapped in `try`/`catch`; a
  parse error propagates out of `executeMessages()`.
- `AnalyseCmd` catches that exception, calls `markTaskAsFailed(task)` and hands
  `e.getMessage()` to `displayManager.onTaskError(...)`, which is why the TUI row shows raw
  Jackson text such as `Unrecognized token 'I': wa…`. The path for a `null` result instead reports
  `No response from chat model`, which is wrong for a response that simply had no payload.
- `JsonHelper.extractJSON()` runs before any parse attempt, so a repair that produces garbage is
  indistinguishable from a repair that worked.
- Recorded evidence used as the test baseline: 36 final responses in
  `workspaces/spring-petclinic/logs/`, of which 22 are bare payloads, 12 start with a code fence
  and 2 (`BuildSystems.log`, `LicenseEvaluation.log`) have a prose sentence in front of a fenced
  payload. The 2 prose cases are exactly the 2 tasks marked `FAILED` in that workspace's
  `state.json`.

Constraints:

- No new dependency. The project deliberately keeps analysis logic dependency-light.
- The response schema root is an object (`JsonHelper.createTypeDescription` requires it), but the
  locator must still not choke on a top-level array.
- Unit tests are the default test level and use in-code or static test data
  (`guidelines/TestApproach.md`); there is no LLM endpoint or model server available in tests.

## Goals / Non-Goals

**Goals:**

- A payload that is present in a response is found, whether or not the model wrapped it in prose,
  a fence, or both.
- Normalisation cannot make a parseable response unparseable, and cannot silently turn one
  document into a different one.
- "No payload in the response" and "payload found but not parseable" are distinct, bounded,
  actionable reports.
- The behaviour is verifiable without an LLM: the location, normalisation and reporting logic is
  testable on strings.

**Non-Goals:**

- No extra model call, no retry loop, no change to prompt or schema text (see proposal - What
  Changes). Prompting the model to stop chatting is a separate, complementary change at best.
- No change to the acceptance policy for schema-conformant-but-incomplete payloads; that remains
  `llm-response-schema-validation`'s diagnostic-only behaviour.
- No attempt to repair structurally broken JSON (missing commas, truncated objects). Such a
  response is reported, not guessed at.
- Not a general JSON repair library, and no configurable knobs for tolerance.

## Decisions

### Locate by bracket matching, not by fence position

The payload is located structurally: candidates are collected from the response and each candidate
is sliced from its opening bracket to its matching closing bracket, with a scan that is aware of
strings and escapes (a `{` or `[` inside a string, or an escaped quote, does not change nesting
depth).

Candidate order:

1. fenced blocks first, last block first - a model that reasons before answering puts its final
   answer in the trailing block;
2. then bare bracket starts in the order they occur.

Only maximal regions are candidates: a bracket inside an already located region belongs to that
region and is never a candidate of its own. Without that rule, a fragment of a malformed payload (one
element of a broken array, say) is itself a parseable document and would be stored as the task result
instead of the malformed payload being reported.

Preferred is a candidate that parses to a non-empty top-level value: an object with at least one
field, or an array with at least one element. An empty fragment quoted in prose therefore loses
against the payload that follows it. The preference is not a filter: when no candidate carries
anything, the empty document is accepted, because `{}` is a valid answer to a schema without required
properties and is the whole payload of such a response. The number of candidates tried is capped, so
a pathological response cannot make extraction quadratic.

Alternatives considered:

- *Regex over fences only* - fails the observed case when the prose precedes the fence and the
  model forgets the fence, and needs a second code path for unfenced payloads.
- *First bracket only* - breaks as soon as the prose itself contains a brace, for example a
  sentence quoting a JSON fragment.
- *Whole-response `readTree` with a pre-processing regex* - the same position problem with extra
  steps.

### Normalisation runs after location, and only as a fallback

Order: trim the response, collect candidates, then for each candidate try a plain parse first. The
existing quote repair (`fixJsonLine()` on the located slice) runs only when that candidate does not
parse as-is. Because the repair can only be reached for input that is already unparseable, it can
no longer corrupt a valid payload, which is what the "normalisation does not add defects"
requirement asks for.

Fence handling moves out of the string-prefix test into candidate collection: a fence mark
(backticks or tildes, with or without an info string such as `json`) is content to skip, not a
position to require.

The per-line quote heuristic was tightened while wiring it up, because as written it assumed one
key/value pair per line that starts at the line's first quote:

- the pattern accepts anything before the key, so a payload on a single line is repaired too;
- a quote ends the value only when the first non-whitespace character after it closes the entry
  (`,`, `}`, `]`) or the line ends. The old lookahead scanned the rest of the line for a `,` or `}`,
  which made the first inner quote of a value look like the end of the string;
- `]` counts as an end, so the last element of a quoted array is not left open.

Alternatives considered: *leave the heuristic alone and only accept pretty-printed payloads* -
rejected, because the requirement to repair an unescaped quote does not depend on line breaks; that
every recorded response is pretty-printed is a property of the models used so far, not a contract.

The per-line quote heuristic stays a heuristic, but its result is accepted only when the repair left
the structure of the document alone: the brackets outside strings must be the same before and after.
A heuristic that escapes quotes can otherwise turn a broken object into a string
(`{"a": [{"b": 1} {"c": 2}]}` becomes `{"a": "[{\"b\": 1} {\"c\": 2}]"}`), which parses
and would hand the task a wrong payload. With the guard, the remaining failure mode of the repair is
"still does not parse", which is reported instead of silently accepted.

### Distinguish missing payload from invalid payload, and report both bounded

`ChatExecutor` needs to tell the two conditions apart, because the display shows its message. The
parse step therefore moves into a small unit that returns the parsed result or throws a dedicated
exception carrying:

- a condition (`no payload located` vs `located payload does not parse`),
- a bounded excerpt of the response (a fixed short prefix plus the response length, not the whole
  body),
- for the invalid case, the underlying parser message.

`executeMessages()` keeps its signature, but a response with no payload now raises that exception
instead of returning `null`, so `AnalyseCmd` reports the real cause instead of
`No response from chat model`. The task-failure path is untouched: the exception still lands in
the same `catch`, and `markTaskAsFailed()` still runs, so `task-failure-handling` semantics and the
dependent-tag behaviour do not change.

The `Corrected JSON String to: <whole body>` warning is replaced: successful normalisation is
debug-level noise, and the failure condition logs the bounded excerpt at WARN. Dropping the
whole-body dump also removes one source of the log output that `fix-tui-log-isolation` addresses;
the two changes are independent, but this one should not add a new full-body log line.

### The parse unit also validates what is stored

`ChatExecutor` validates the payload against the response schema as before, but now validates the
re-serialized parsed document instead of the located text. Both are the same document; validating the
parsed form checks exactly what the task will publish, rather than a text form the parser may have
chosen between several candidates.

### Tests

- `JsonHelperTest` (or a new sibling test class) gets cases for: bare payload, leading fence,
  prose + fence, fence + trailing prose, prose that itself contains braces followed by a fenced
  payload, single-line payload with surrounding text, payload containing braces and escaped quotes
  inside strings, prose only, truncated payload, and a payload with unescaped inner quotes.
- The two recorded responses are used as realistic fixtures: a short prose sentence in front of a
  fenced object, and one that additionally exercises nested arrays and strings with punctuation.
  They are inlined as test data (the guideline allows in-code data) rather than reading from
  `workspaces/`, so the test does not depend on a checked-in workspace.
- The reporting behaviour (condition, bounded excerpt, message text) is asserted at the parse unit,
  which needs no LLM stub. A `ChatExecutor`-level test is not added: constructing a usable
  `ToolService`/`ChatModel` double for it buys nothing that the unit test does not already cover.
  The failure still reaches `markTaskAsFailed()` because the exception is an `IOException`, which the
  task runner's `catch (Exception e)` handles; a test asserts that relationship.

## Risks / Trade-offs

- **Prose containing a non-empty JSON fragment.** Requiring a non-empty top-level closes the
  `{}`-in-prose case but not a response that mentions something like `{"a": 1}` in prose before
  the real payload: that fragment is non-empty and would win. Fenced candidates are preferred and
  the fragment would usually fail schema validation, but the failure is a warning, not a rejection
  (diagnostic-only validation), so a wrong payload could be stored. Accepted: no observed response
  in the recorded runs had more than one JSON candidate. What is closed is the worse variant - a
  fragment of a *malformed* payload being kept as the result, which the maximal-region rule and the
  structure-preserving repair guard prevent.
- **Behaviour change on rerun.** Workspaces that currently hold `FAILED` tasks for the prose case
  (for example `workspaces/spring-petclinic`) will succeed on the next run. That is intended.
- **More responses now parse.** More responses also reach schema validation, so more
  schema-violation warnings may become visible. Those are diagnostic by design.
- **Heuristic repair remains.** `fixJsonLine()` is still a per-line heuristic with known weak spots
  (a value that spans several lines). Parse-first ordering plus the structure guard bound the damage:
  but does not fix the heuristic; replacing it is not part of this change.
- **Bracket scan cost.** Bounded by the candidate cap; a very large response is scanned at most a
  fixed number of times per candidate.

## Open Questions

- Is a retry worth it for the residual "no payload" case? Deferred; the proposal lists it as a
  non-goal.- The models ignored the JSON schema they were given (`nativeJSON: true` on every round, including
  the round that produced the prose). Whether Ollama drops the format constraint when tool
  specifications are attached is untested; `--log-request=true` on one workspace would show the
  outgoing request body. This is evidence gathering for the prompt/native-JSON side, not work in
  this change.
