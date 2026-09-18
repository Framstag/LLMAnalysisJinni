# Test concept

## Test approach

- We use JUnit for unit tests as the default and the only form of test for analysis
  logic. The packaged artefact is additionally covered by an integration test, see below.

## Test levels

### Unit tests (default)

- Cover analysis logic, tools, configuration and rendering.
- Run against the compiled classes and the full dependency classpath, so they cannot observe
  what the packaged artefact does or does not contain.

### Artefact smoke test

- The packaged artefact is verified by `smoke/JarSmokeIT`, run by `maven-failsafe-plugin` in
  the `verify` phase. It exists because classes that are only resolved at runtime are
  invisible to the classpath-based unit tests: it launches the built jar as its own process
  and asserts that its diagnostics reach the console and that XML parsers can be created
  from it.
- The test must stay offline and fast: it must not need an LLM endpoint, a model server or
  network access, and it must not modify a checked-in workspace.
- A failure names the artefact, so a silent jar run is reported as a packaging defect.

## Test style guide

### Unit Tests

- We use JUnit for unit test harness.
- Unit tests use in code test data or static tests data in files.
- Unit tests have at least one assertion.
