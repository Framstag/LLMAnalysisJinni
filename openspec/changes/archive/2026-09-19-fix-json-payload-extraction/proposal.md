# Proposal

## Why

Models do not always answer with a bare JSON document. In a real run against
`workspaces/spring-petclinic` (deepseek-v4.1-flash, JSON schema requested on every round via
`nativeJSON: true`), 2 of 36 task responses arrived as a prose sentence followed by a fenced
`json` block. `JsonHelper.extractJSON()` removes a code fence only when it sits at the exact
first or last character of the response, so in both cases the prose stayed in front of the
payload and Jackson failed with `Unrecognized token 'I': was expecting (JSON String, Number,
Array, Object or token 'null', 'true' or 'false')`. Both tasks were marked failed, their tags
stayed locked and their dependents were skipped — although the response contained a perfectly
valid payload. The only diagnostic is `Corrected JSON String to:` followed by the uncorrected
body, which claims a repair that never happened.

## What Changes

- Locate the payload structurally instead of by fence position: trim the response, remove a
  code fence wherever it appears, then slice from the first `{` or `[` to its matching close
  bracket, treating brackets inside strings and escaped characters correctly.
- Apply the existing bad-quote repair only to that slice, and only after the payload has been
  located.
- Report a response with no locatable payload with a bounded, explicit diagnostic (strategy
  tried plus a short prefix of the response) at a level that does not print the whole body.
- Report that case to the display and the user as "no JSON payload in response", together with
  the located candidate when there was one, instead of the raw parser message.
- Add regression coverage for prose-prefixed, prose-suffixed, fence-inside-prose and
  single-line responses, including the two responses recorded under
  `workspaces/spring-petclinic/logs/`.
- Non-goal: the number of model calls per task does not change. Re-asking the model for a
  JSON-only answer is explicitly not part of this change; if it turns out to be needed it
  belongs in its own proposal.
- Non-goal: schema conformance stays diagnostic-only as specified by
  `llm-response-schema-validation`. This change only affects whether a payload is found and
  parsed at all.

## Capabilities

### New Capabilities

- `llm-response-extraction`: how the JSON payload is located inside a model response, how it is
  normalised before parsing, and what happens when no payload can be located.

### Modified Capabilities

None. `llm-response-schema-validation` keeps its diagnostic-only behaviour and
`task-failure-handling` keeps its failure semantics; both are unchanged by this proposal.

## Impact

- `src/main/java/com/framstag/llmaj/json/JsonHelper.java` — `extractJSON()`, `fixJsonDocument()`,
  `fixJsonLine()`.
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java` — the parse site and how a
  non-locatable payload is reported back to the caller.
- `src/test/java/com/framstag/llmaj/json/JsonHelperTest.java` — extended cases.
- `workspaces/spring-petclinic/logs/BuildSystems.log` and `LicenseEvaluation.log` — the recorded
  responses used as test fixtures; no analysis artefact is modified.
- No new dependency, no change to prompts, response schemas or task YAML.
- Independent of `fix-tui-log-isolation`: that change removes log output from the TUI, this one
  changes which responses parse. They touch different subsystems and can be applied in any order.
