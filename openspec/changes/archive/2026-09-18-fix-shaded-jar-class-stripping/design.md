# Design

## Context

See `proposal.md` — Why. Relevant current state, measured on this checkout:

- Packaging: `maven-shade-plugin` 3.6.2 builds `LLMAnalysisJinni-jar-with-dependencies`, `minimizeJar=true`, `ServicesResourceTransformer`, and two `include` filters that keep all of `org.jline:jline-terminal` and `org.jline:jline-terminal-ffm` (added when JLine broke the same way in `2026-07-05-live-progress-display`).
- The minimized jar contains 7562 classes. logback is reduced from 181 (`logback-classic`) + 485 (`logback-core`) classes to a handful: `DefaultJoranConfigurator`, `BasicConfigurator`, the joran package, `ConsoleAppender`, `PatternLayoutEncoder` are all absent. Root logger ends at `DEBUG` with no appenders.
- StAX impls (`com.ctc.wstx.*`) declared in `META-INF/services/javax.xml.stream.*` are absent from the jar, so `XMLInputFactory.newInstance()` / `XMLOutputFactory.newInstance()` throw `FactoryConfigurationError`. Same for nashorn and the woodstox MSV/DTD factories.
- Every existing test runs against `target/classes` plus the Maven dependency classpath, so no test can observe the packaged artefact. `AGENTS.md` documents `mvn exec:java` as the run path, which uses the full classpath and also cannot observe it.
- Dependency jars on the compile classpath total 30.2 MiB (63 artifacts); the minimized uber-jar is 20.3 MB.

## Goals / Non-Goals

**Goals:**

- The packaged artefact is behaviourally equivalent to the classpath launch for logging and XML factory lookups.
- A future dependency bump or shade configuration change cannot lose a runtime-loaded class unnoticed.
- The verification is cheap, offline, and does not need an LLM endpoint.

**Non-Goals:**

- Jar size optimization, class minimization by a different mechanism, or module system (JPMS) packaging.
- Changing the logging framework, log format, log level defaults, or the console trace/TUI behaviour.
- Redesigning the `mvn exec:java` workflow or removing it as a supported path.
- Fixing unrelated stale-artefact concerns (the checked-out jar being older than the sources) beyond documenting that the artefact must be rebuilt.

## Decisions

### D1: Remove `minimizeJar` instead of adding keep rules

Shade's minimizer decides reachability by bytecode analysis. logback resolves configurators through `Class.forName` on a string constant, and XML parser implementations are resolved through `META-INF/services`; neither is visible to that analysis. Adding `include` filters for logback and woodstox fixes today's two symptoms but leaves the mechanism in place: the next dependency that loads a class reflectively (as JLine already did) fails the same way and produces the same silent symptom.

Decision: drop `minimizeJar`. Keep `ServicesResourceTransformer` and the existing `module-info.class` / `META-INF/*.MF` exclusions. Remove the JLine `include` filters, which only exist to work around minimization and become redundant.

Alternatives considered:

- *Add keep rules for logback + woodstox*: smaller jar, but a per-dependency whack-a-mole list that is silent when incomplete. Rejected as the primary mechanism.
- *Keep minimizing and add a build-time reachability audit*: an audit that must itself know every reflective lookup is not more reliable than not removing classes.
- *Ship the jar as a thin launcher with a dependency directory*: changes the distribution shape and the documented run path; out of scope.

Cost of the decision: the uber-jar grows from 20.3 MB to roughly the 30 MiB of dependency content (≈1.5x). Accepted; the artefact exists for convenience of running the analysis, not for distribution size.

### D2: Verify the artefact with maven-failsafe-plugin, launching the real jar

Verification must run after `package` (the jar does not exist during `test`), so it belongs in the `integration-test` phase: `maven-failsafe-plugin` bound to `integration-test`/`verify`, executing a test-scoped probe main class that spawns `java -jar target/LLMAnalysisJinni-jar-with-dependencies.jar`.

The probe is a test-scoped class (`src/test/java/.../smoke/`), not production code, and is started via `java -cp <jar>:target/test-classes <probe>`. This keeps the probe out of the shipped artefact — the alternative, a hidden CLI subcommand, would add production surface purely for testing.

The probe performs two independent checks:

1. **Logging path** — run the real CLI against a workspace directory that has no configuration. The run must exit non-zero *and* print the cause. This exercises logback configuration loading, `ConsoleAppender`, and the encoder, and it needs no network and no model endpoint. A second assertion greps the output for the expected level/logger prefix so a bare `System.out` write cannot satisfy it.
2. **Factory path** — assert `XMLInputFactory.newInstance()` and `XMLOutputFactory.newInstance()` succeed from within the packaged classpath, and that the SBOM tool parses an SBOM document from that same classpath, so the JSON-based SBOM path is verified as well as the XML factory path.

A third case runs the CLI itself with `--execution-trace=true --single-step=true` against a workspace whose model endpoint is a closed local port: the display-mode line and a task start line must reach the console, and no model is contacted because the connection is refused. This covers the trace and progress behaviour of the artefact, which the configuration-failure case cannot show.

Alternatives considered:

- *Assert only that the jar contains certain class files*: a class file list check passes on a jar whose config file was dropped or whose service entry points elsewhere; and it hardcodes dependency internals. Rejected as the only check.
- *Run a full analysis from the jar*: covers the most ground but needs an LLM endpoint and minutes of runtime; unsuitable as a build gate.
- *exec-maven-plugin running a shell script in `verify`*: workable, but failsafe gives JUnit reporting and skips/fails integration cleanly with the rest of the build.

### D3: Guard the whole class of defect, not just logback and woodstox

The probe additionally enumerates every `META-INF/services/<interface>` entry in the packaged jar and asserts the named implementation class loads from the packaged jar. This is a generic check for the "declared provider, missing class" defect that produced both symptoms here, and it keeps working when dependencies change.

A `Class.forName`-style lookup such as logback's is not covered by that enumeration; it is covered by check 1 of D2, which asserts the externally visible consequence (diagnostics reach the console) rather than the mechanism, so a future logging backend is verified on the same terms.

A declared provider can be present in the artefact and still not load, because an optional dependency of it is absent from every classpath (logback's servlet integration needs the servlet API, which is not a dependency of this project). The probe therefore separates a class that the artefact does not contain at all, reported as `SERVICE_PROVIDER_MISSING` and failing the verification, from one that is present but unresolvable, reported as `SERVICE_PROVIDER_UNRESOLVED` and informational. Only a packaging loss fails the build, so the guard does not report a defect that a full-classpath run does not have either.

### D4: Document the artefact contract and rebuild

`AGENTS.md` (and `README.md` where the jar is mentioned) gains a short statement: the uber-jar is verified by the build's `verify` phase; a run started from the jar that produces no output at all is a packaging defect, not a silent successful run. The `mvn exec:java` workflow stays as-is. The jar lives under `target/` and is not tracked, so no data migration is involved — the artefact simply has to be rebuilt after the fix.

## Risks / Trade-offs

- [Jar grows ~1.5x] → measured and accepted in D1; if size later matters, reintroduce minimization with a proven keep list plus the D3 guard, in a separate change.
- [Dropping the JLine include filters could regress the TUI] → the filters become no-ops once nothing is removed; the D2 probe plus a manual TUI run confirm the terminal path still works, and `TerminalSupport.detect()` already degrades to `none()` on failure.
- [Probe spawns a JVM in the build; needs `java` on `PATH` and no terminal] → the check uses no TTY and no network; the CLI already degrades to simple output when stdout is not a terminal, and the bogus-workspace path prints before any terminal work.
- [Check 1 asserts on log text that a future change may reword] → assert on the stable part (a non-empty line carrying a log level and logger name plus the substring identifying the failure), not on the full sentence.
- [Other resources (not classes) may still be lost or badly merged by shade] → out of scope for the guard; `ServicesResourceTransformer` and the `logback.xml` at the jar root are covered by the checks, and any further resource defect shows up as a behavioural failure the same way this one did.
- [Uber-jar keeps being the less-tested path while `mvn exec:java` is documented first] → D4 states the contract explicitly so silence from the jar is treated as a bug.
- [The guard's own detection could rot once `minimizeJar` is gone, because nothing in the build removes a runtime-loaded class any more] → the integration test strips a service provider class from a copy of the artefact and asserts that the probe fails and names the missing capability, so the detection is exercised on every build.

## Migration Plan

1. Change `pom.xml` (drop `minimizeJar`, drop the JLine filters).
2. Add failsafe + probe, run `mvn verify`; confirm the probe fails against the old configuration (temporarily) and passes after the change.
3. Rebuild the artefact and re-run the originally reported command to confirm visible output.
4. Rollback: restore `minimizeJar` and the filters; the probe then fails, which is the intended signal, so rollback also means reverting the verification step.

## Open Questions

- Whether a later change should reintroduce minimization with a verified keep list to reduce artefact size.
- Whether the project should publish the uber-jar as a release artefact and, if so, where the smoke verification sits in that pipeline.
