## Why

LLM responses sometimes produce JSON structurally similar to the task's schema but not precisely conformant — wrong attribute names, missing enum values, or extra fields. The root causes are: no post-hoc validation catching violations, missing enum constraints in text-mode schema descriptions, thread-safety gaps in parallel loop execution (shared mutable state), and dead code confusing maintainability.

## What Changes

- **Schema validation:** Validate every LLM response against its JSON schema after parsing, log violations
- **Dual schema cues:** Always append schema text description to user message even with native JSON mode — LLM gets formal `responseFormat` + verbal description
- **Thread safety:** Deep-copy analysis state per worker thread instead of sharing mutable references
- **Dead code:** Remove unused `loopValue`/`loopIndex`/`loopIterator` fields from `StateManager`
- **Typo fix:** Correct "orrect JSON" → "correct JSON" in system prompt

## Capabilities

### New Capabilities
- `llm-response-schema-validation`: Validate LLM JSON responses against declared schema, log warnings on mismatch

### Modified Capabilities
- *None* — no spec-level requirement changes, only implementation hardening

## Impact

- **Files:** `AnalyseCmd.java`, `ChatExecutor.java`, `StateManager.java`, `JsonHelper.java`, `systemprompt.md`
- **Risk:** Low — all changes are additive or cleanup
- **Dependencies:** Uses existing `networknt/json-schema-validator` (already on classpath)