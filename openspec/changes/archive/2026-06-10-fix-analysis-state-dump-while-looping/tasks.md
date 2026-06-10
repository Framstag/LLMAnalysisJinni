## 1. Fix: Add missing saveState() in loop path

- [x] 1.1 In loop worker (AnalyseCmd.java lines 209-218), wrap `updateLoopState` + `saveState` in `synchronized(stateManager)` block
- [x] 1.2 Remove redundant post-loop `saveState()` after `endLoop()` (no longer needed)
- [x] 1.3 Build project with `mvn verify -DskipTests` and verify no compilation errors
- [x] 1.4 Run end-to-end analysis on a test project with loopOn tasks, verify `analysis.json` contains findings per completed index