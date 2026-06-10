## Why

When a task with `loopOn` (e.g., `ModuleBuildfileAnalysis`) executes across multiple module iterations, LLM responses are stored in memory via `updateLoopState()` but never persisted to `analysis.json`. On re-run only `state.json` knows indices are "successful" — but `analysis.json` is empty or stale, losing all findings.

## What Changes

- Add `stateManager.saveState()` call after loop completes (post `endLoop()`, pre `markLoopTaskAsSuccessful`) in `AnalyseCmd.java`
- Ensure analysis state is flushed to disk exactly once per loop task, avoiding race conditions from parallel thread writes
- No schema or YAML changes — pure fix in execution orchestration

## Capabilities

### New Capabilities
- *None* — this is a bug fix, no new capability introduced

### Modified Capabilities
- *None* — no spec-level requirement changes, only implementation fix in execution orchestration

## Impact

- **File:** `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java`
- **Change:** Add one `stateManager.saveState()` call after `endLoop()` in the loop branch
- **Risk:** Low — single-line addition, `saveState()` already proven in non-looping path
- **No API, dependency, or config changes**