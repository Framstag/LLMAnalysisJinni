# Tasks

## 1. Locate and normalise the payload

- [x] 1.1 Extract the payload location into a structural scan: collect candidates (fenced blocks last-first, then bare `{`/`[` starts in document order), slice each from its opening bracket to its matching close with string- and escape-aware nesting, cap the number of candidates. Verify with a unit test that a payload containing `{`, `[` and escaped quotes inside strings is sliced to its real closing bracket.
- [x] 1.2 Skip candidates that do not parse to a non-empty top-level value (object with at least one field, array with at least one element). Verify with a unit test in which prose mentions `{}` before the real payload and the real payload is located.
- [x] 1.3 Reorder normalisation: parse each candidate as-is first, and run the existing `fixJsonLine()` repair only for a candidate that does not parse. Verify with a unit test that an already-parseable payload with inner escaped quotes and punctuation is returned unchanged.
- [x] 1.4 Drop the string-prefix/suffix fence tests in favour of fence handling during candidate collection (backticks and tildes, with or without an info string). Verify with unit tests for a leading fence, prose + fence, and fence + trailing prose.

## 2. Report the outcome instead of guessing

- [x] 2.1 Introduce the parse unit that returns a parsed result or raises a dedicated exception carrying the condition (`no payload located` vs `located payload does not parse`), a bounded excerpt (fixed prefix plus response length), and, for the invalid case, the parser message. Verify with unit tests asserting the condition and the excerpt bound for a prose-only response and for a truncated payload.
- [x] 2.2 Call that unit from `ChatExecutor.executeMessages()` so a response without a locatable payload raises the exception instead of returning `null`. Verify with a unit test on the parse unit plus a compile/behaviour check that the failure still reaches `markTaskAsFailed()` and no dependent tag is unlocked.
- [x] 2.3 Replace the `Corrected JSON String to: <whole body>` warning: successful normalisation logs at debug level, the failure condition logs the bounded excerpt at WARN. Verify with a test that captures log events and asserts the full response body is never logged for an unlocatable payload.
- [x] 2.4 Make the failure reachable message name the missing payload rather than leaving the generic `No response from chat model` text. Verify by inspecting the message produced for a prose-only response.

## 3. Regression coverage

- [x] 3.1 Add the recorded response shapes as in-code fixtures: a prose sentence in front of a fenced object, and one with nested arrays and strings containing punctuation. Verify with a test that both now yield a parsed document.
- [x] 3.2 Keep the existing `JsonHelperTest` cases (identity, ` ```json{}``` `, bad quotes) green. Verify with `mvn -q -Dtest=JsonHelperTest test`.

Fixture provenance: the shapes follow the prose-wrapped responses of the run at 20:04 (the one whose
`Corrected JSON String to: I now have all the information needed …` warning was recorded), read from
`workspaces/spring-petclinic/logs/BuildSystems.log` and `LicenseEvaluation.log`. The fixtures are
abridged reproductions of those shapes, not copies: those logs have since been overwritten by later
runs of the same two tasks (`LicenseEvaluation.log` at 21:07, `BuildSystems.log` at 23:57, both
fence-only responses), so the prose case is pinned at unit level only.

## 4. Verification

- [x] 4.1 Run `mvn verify -DskipTests` then `mvn verify` and confirm the aggregate SBOM build, unit tests and the artefact smoke test all pass in one run.
- [x] 4.2 Run `openspec validate fix-json-payload-extraction --strict` and confirm the change validates with no unresolved items.
- [x] 4.3 End-to-end check against a real model: `state drop BuildSystems` then `analyse -o BuildSystems workspaces/spring-petclinic` on 2026-09-18. The task completed (`✓ BuildSystems (13,3s)`), its state is `[x]` again and its result is stored in `analysis.json`. Caveat, recorded on purpose: the response of this run was fence-only (`logs/BuildSystems.log` starts with a ```json line), so this run does not itself demonstrate the prose-preamble case; that case is covered at unit level (see the provenance note under 3.1). The model's habit of explaining itself first varies per run and cannot be forced from the CLI.

## 5. Decisions the implementation forced

Raised while wiring up tasks 1.2 to 1.4, each implied by a requirement of this change. Recorded in design.md under Decisions.

- [x] 5.1 Make the non-empty rule a preference rather than a filter: the empty document is accepted when it is all the response carries, because `{}` is a valid payload for a schema without required properties and `JsonHelperTest` pins that behaviour. Two passes over the candidates, non-empty first. Verify with `parsesPayloadThatIsTheWholeResponse`, `acceptsAnEmptyDocumentWhenItIsAllTheResponseCarries` and `prefersThePayloadOverAnEmptyFragmentInProse`.
- [x] 5.2 Collect only maximal regions: a bracket inside an already located region is part of it. Verify with `doesNotUseAFragmentOfABrokenPayloadAsTheResult`, in which a malformed array would otherwise hand back its first, well-formed element as the task result.
- [x] 5.3 Generalise the existing `fixJsonLine()` repair so a payload on a single line is repaired too: accept anything before the key in the pattern, end the value only at the first non-whitespace character that closes the entry (`,`, `}`, `]`) or at the line end, and accept `]` as an end. Verify with `repairsUnescapedQuotesInALocatedPayload` and with the unchanged `JsonHelperTest` bad-quotes case.
- [x] 5.4 Guard the repair with the structure check requirement 2 asks for: accept a repaired document only when the brackets outside strings are unchanged, so the heuristic cannot turn a broken object into a string. Verify with `doesNotUseAFragmentOfABrokenPayloadAsTheResult` and `reportsALocatedPayloadThatDoesNotParse`.

## 6. Verification follow-ups

Raised by the verification pass after the tasks above were complete. No behaviour change.

- [x] 6.1 Assert that an already-parseable payload is returned unchanged, the verification task 1.3 named: `locatedPayloadThatParsesIsReturnedUnchanged` compares `payloadText()` with the payload of a response whose value contains escaped quotes, braces, brackets and punctuation.
- [x] 6.2 Correct the fixture provenance in this file: the prose responses came from the 20:04 run, the fixtures are abridged shapes rather than copies, and the source logs have since been overwritten (see the note under 3.1).
- [x] 6.3 Mark the four `taskResultJson == null` branches in `AnalyseCmd` as defensive: the parser raises instead of returning no result, so they are not reached by a response without a payload (comments at the loop branch and the non-loop branch).
- [x] 6.4 Hoist the candidate list out of the debug argument in `ResponsePayloadParser.parseFirstMatching`, which recomputed it on every successful fenced or prose parse.
- [x] 6.5 Record in design.md that schema validation now runs on the re-serialized parsed document, so what is validated is what is stored.
- [x] 6.6 Document `JsonHelper.extractJSON()` (and the structure guard on `fixJsonDocument()`) as the string-level entry point, since the parsing callers use `ResponsePayloadParser`.
