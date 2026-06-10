## Context

Four independent but related issues were found when debugging LLM schema conformance:

1. **No response validation**: `ChatExecutor.executeMessages()` parsed JSON but never checked it against the schema. The schema was only used as a prompt instruction (via `ResponseFormat` or patched message). Any valid JSON passed through silently.

2. **Missing enum constraints**: `JsonHelper.getObjectDescription()` reported `enum`-constrained fields as bare `type: string`, dropping the allowed values. In non-native JSON mode (text-only description appended to user prompt), the LLM didn't know valid values existed.

3. **Shared mutable state in parallel loop**: `AnalyseCmd.java`'s parallel loop workers shared `analysisState` via shallow copies from `stateManager.getStateObject()`. Each worker's template rendered against the same mutable JsonNode tree concurrently. While writes target different array indices, data races on intermediate template accesses could cause non-deterministic behavior.

5. **Missing verbal schema cue in native JSON mode**: When `nativeJSON=true`, `patchUserMessageWithSchema` was skipped entirely. LLM got only the Ollama `format` parameter (the formal schema). Some models (e.g., `qwen2.5:7b`) don't fully enforce nested object schemas through `format` alone — they flatten objects to strings and add extra fields. Adding the schema text description as a verbal backup improves compliance.
4. **Dead fields in StateManager**: `loopValue`, `loopIndex`, `loopIterator` were written by `loopAtIndex()` from multiple threads but never read — leftover from the old sequential loop. Harmless but creates data race warnings and confuses future readers.

## Goals / Non-Goals

**Goals:**
- Catch schema violations post-hoc and log them for diagnosis
- Make LLM aware of enum constraints in text-mode schema descriptions
- Eliminate cross-worker data races on analysis state
- Remove dead code to improve maintainability

**Non-Goals:**
- Rejecting non-conformant responses (validation is diagnostic only, no breaking change)
- Changing task YAML or prompt templates
- Introducing new dependencies (uses `networknt/json-schema-validator` already in classpath)
- Adding schema text description always (not just in non-native mode)

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Validate at parse time | After `readTree()` in `executeMessages()`, validate result against schema | Single chokepoint; all LLM responses pass through this method |
| Validation severity | Log warning only, return result | Breaking change would require migration; schema violations should inform, not block |
| Schema text -> string for validation | Serialize `responseSchema` (Jackson `JsonNode`) via `ObjectMapper.writeValueAsString()` to avoid `tools.jackson` vs `com.fasterxml.jackson` type mismatch | `SchemaRegistry.getSchema(JsonNode)` expects `tools.jackson` fork type; string API avoids the conflict |
| Deep copy for workers | `analysisState.deepCopy()` → wrap in `JsonNodeModelWrapper` → put into per-worker `HashMap` | Each worker gets an independent snapshot; no race on template access |
| Dead field removal | Just delete the 3 field declarations + their references | No behavioral change; zero risk |

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Networknt validator rejects valid but non-standard JSON | Validation is diagnostic-only via warn log; no pipeline breakage |
| Deep copy overhead for large analysis states | Copy is of JsonNode tree (in-memory); LLM call dominates runtime by orders of magnitude |
| Removing `loopValue` exposes latent dependency | Confirmed zero reads — only writes in `loopAtIndex()` and null assignment in `endLoop()` |

## Migration Plan

Already implemented. Artifact creation is retroactive documentation.

1. `AnalyseCmd.java`: Replace shallow `workerState.putAll(stateManager.getStateObject())` with `workerState.putAll(new JsonNodeModelWrapper(stateManager.getAnalysisState().deepCopy()))`
2. `ChatExecutor.java`: Add `SchemaRegistry` + `Schema.validate()` block after `readTree()`; add networknt imports
3. `StateManager.java`: Remove `loopValue`, `loopIndex`, `loopIterator` fields and `Iterator` import
4. `JsonHelper.java`: In `getObjectDescription()` `else` branch, after `type:` output, add `if (property.has("enum"))` block listing allowed values
5. `systemprompt.md`: Fix typo "orrect" → "correct"

Rollback: `git revert` the 5 commits containing these changes.

## Open Questions

*None.*