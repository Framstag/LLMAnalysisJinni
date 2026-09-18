# Proposal

## Why

The shipped uber-jar (`target/LLMAnalysisJinni-jar-with-dependencies.jar`) runs with **no console diagnostics at all** and cannot create StAX XML factories. A normal `analyse` invocation completes its LLM calls while printing zero bytes to stdout and stderr, including its own error messages, so a failing or hanging run is indistinguishable from a run that does nothing.

Cause is a build-time one: `maven-shade-plugin` runs with `minimizeJar=true`, which keeps only classes reachable by bytecode analysis. logback locates its configurators through `Class.forName`, so `DefaultJoranConfigurator`, `BasicConfigurator`, all joran classes, `ConsoleAppender` and `PatternLayoutEncoder` are removed from the jar. logback then fails to read `logback.xml`, the root logger stays at `DEBUG` with **no appenders**, and every SLF4J event is discarded. The same mechanism removed the woodstox StAX implementations, so `XMLInputFactory.newInstance()` and `XMLOutputFactory.newInstance()` throw `FactoryConfigurationError`.

This is not a new regression: `minimizeJar` has been enabled since the uber-jar was first added, and JLine already needed explicit include filters when it broke the same way. The `mvn exec:java` workflow documented in `AGENTS.md` uses the full classpath and therefore hides the defect, which is why it went unnoticed.

## What Changes

- Stop the uber-jar from silently losing runtime-loaded classes: either remove `minimizeJar`, or replace it with explicit keep rules for the artifacts that load classes reflectively (logback, woodstox).
- Make the fix verifiable: add a check that runs the **built jar** and asserts (a) at least one log line reaches stdout/stderr and (b) the XML input/output factories instantiate. Tests that only exercise `target/classes` cannot catch this class of defect.
- Record the invariant in the packaging build so a future dependency bump cannot reintroduce the loss unnoticed.
- Rebuild the artifact; the currently checked-in/available jar predates the fix and also predates recent `analyse` CLI changes.

## Capabilities

### New Capabilities

- `shaded-jar-integrity`: the packaged uber-jar is a runnable substitute for the full classpath, meaning classes loaded reflectively or through `ServiceLoader` survive packaging, logging reaches the console, and XML-based parsing works when run from the jar.

### Modified Capabilities

None. No analysis or CLI behavior changes; the requirements of `llm-interaction-logger` and `live-progress-display` are unchanged and are supposed to hold for the packaged artifact as well.

## Impact

- `pom.xml` — `maven-shade-plugin` configuration (`minimizeJar` and/or `filters`), possibly an additional `keep`/include rule set for logback and woodstox.
- Build verification — a new jar-level smoke test (Maven integration/verify step or a test that executes the packaged jar), because the current unit tests all run against `target/classes`.
- `AGENTS.md` / `README.md` — run instructions should state whether the uber-jar or `mvn exec:java` is the supported path, and that jar output can only be trusted after the smoke check passes.
- No production Java code change is expected. No new runtime dependency; the fix keeps classes that are already on the classpath.
- Out of scope: logback version bumps, the choice of logging framework, and any redesign of the console trace/TUI behavior.
