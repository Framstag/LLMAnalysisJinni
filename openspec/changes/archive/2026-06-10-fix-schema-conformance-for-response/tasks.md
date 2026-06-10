# Tasks: fix-schema-conformance-for-response

## Task 1: Deep-copy worker state for thread safety

- [x] `AnalyseCmd.java`: Replace shallow `workerState.putAll()` with deep copy via `analysisState.deepCopy()`
- [x] Add `ObjectNode` and `JsonNodeModelWrapper` imports

**Files:** `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java`

---

## Task 2: Validate LLM responses against JSON schema

- [x] `ChatExecutor.java`: Add `SchemaRegistry` + `Schema.validate()` block after `readTree()`
- [x] Serialize schema to string to avoid `tools.jackson` vs `com.fasterxml.jackson` type conflict
- [x] Log warnings with violation details instead of silently accepting bad responses
- [x] Add networknt imports

---

## Task 6: Always append schema text description in user message

- [x] `ChatExecutor.java`: Remove `!config.isNativeJSON()` guard — schema text always appended regardless of native JSON mode
- [x] LLM now gets dual cue: formal `responseFormat` parameter + verbal schema description

**Files:** `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`

**Files:** `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`

---

## Task 3: Remove dead loop state fields

- [x] `StateManager.java`: Remove `loopValue`, `loopIndex`, `loopIterator` field declarations
- [x] Clean up `loopAtIndex()` to use local variable instead of shared field
- [x] Clean up `startLoop()` / `endLoop()` to drop dead assignments
- [x] Remove `Iterator` import

**Files:** `src/main/java/com/framstag/llmaj/state/StateManager.java`

---

## Task 4: Add enum values to schema text description

- [x] `JsonHelper.java`: In `getObjectDescription()`, after printing `type:`, add `if (property.has("enum"))` block
- [x] List allowed values as `"value1", "value2", "value3"` in description string

**Files:** `src/main/java/com/framstag/llmaj/json/JsonHelper.java`

---

## Task 5: Fix typo in system prompt

- [x] `systemprompt.md`: Fix "orrect JSON" → "correct JSON"

**Files:** `analysis/software-architecture/prompts/systemprompt.md`