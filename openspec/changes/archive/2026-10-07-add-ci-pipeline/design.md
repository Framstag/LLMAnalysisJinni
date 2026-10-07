# Design

## Context

See `proposal.md` - Why. Three constraints shape the approach:

- The gate is already defined and already runs locally: `mvn verify`. It is the only phase that reaches the failsafe integration test `smoke/JarSmokeIT` (against `target/LLMAnalysisJinni-jar-with-dependencies.jar`) and the SBOM checks, which parse `target/bom.json` after generation in `process-classes`. The pipeline must not invent a second gate.
- The toolchain is pinned twice in-tree: `mise.toml` (`java = "openjdk-25"`, `maven = "3"`) and `pom.xml` (`maven.compiler.source/target = 25`). `AGENTS.md` documents `mise install` as the local setup.
- The build needs no secrets and no network beyond Maven Central and the GitHub action marketplace. Nothing in the test suite calls a model provider.

`AGENTS.md` also warns that `minimizeJar` must stay disabled in the shade configuration; the failsafe test exists precisely because classes resolved only at runtime are invisible to unit tests. Any pipeline that skips `verify` re-opens that hole.

## Goals / Non-Goals

**Goals:**

- One workflow that runs the existing gate on pushes to the default branch, on pull request commits, and on demand.
- A failure that is attributable: the run must make it obvious which phase failed.
- A run that finishes fast enough to stay on by default; dependency downloads cached.

**Non-Goals:**

- Deciding whether branch protection requires the check (repository setting, applied after the workflow exists).
- An `openspec validate` gate (see Decisions - deferred).
- Multi-OS or multi-JDK build matrices. One Linux runner on the targeted JDK answers "did this commit build" without multiplying cost.
- Publishing release artefacts, tagging, or container images. This change produces a verdict, not an artefact.

## Decisions

### Provision the toolchain with `actions/setup-java`, not `jdx/mise-action`

`setup-java` reads nothing from the repository, but `java-version: '25'` plus `distribution: temurin` reproduces the `mise.toml` pin, and its `cache: maven` gives dependency caching in one line.

Alternative considered - `jdx/mise-action`, which would install exactly what `mise.toml` pins and keep one source of truth for tool versions. Rejected for the first cut: it adds a third-party action to read a file whose only content relevant here is the JDK, and the Maven cache then has to be wired by hand. Accepted cost of the choice: the JDK version now appears in two places, so a future `mise.toml` bump must be mirrored in the workflow. The spec's "verification uses the project's Java language level" requirement is what would catch a drift that turns into a failure, but it will not catch a drift that silently keeps passing.

### `on: push` to the default branch, `pull_request`, and `workflow_dispatch`

`pull_request` alone would leave direct pushes to `main` unverified; `push` on all branches would spend runs on branches that are still being reshaped. Restricting `push` to `main` and letting `pull_request` cover the rest matches how this repository works: feature branches arrive as pull requests.

`workflow_dispatch` is included so a maintainer can verify a branch or re-run the gate without an empty commit - the "started on demand" requirement.

### `mvn -B verify` as the single build step

No `mvn test` step, no separate artefact job. `verify` already subsumes compile, unit tests, SBOM generation, packaging and the artefact smoke test. Splitting it into stages would create steps that can pass while the gate fails.

`-B` keeps the log free of download progress and makes the Maven output linear enough to read in the web UI.

### No `concurrency` cancellation in the first cut

Cancelling superseded runs would save minutes on frequently pushed branches, but it also means the check for a superseded commit ends as "cancelled", which can be mistaken for "not run". Leaving runs to finish keeps the commit-to-result mapping unambiguous. This is a cheap thing to add later if run volume becomes a problem.

### The `openspec validate --all --strict` gate is deferred

`openspec validate --all --strict` currently reports 31 passed and 11 failed of 42 items; the findings are WARNING-level (requirement descriptions over 500 characters, and placeholder `## Purpose` sections in `batch-java-metric-evaluation` and `batch-java-report-collection`). Folding that gate into this workflow would make the pipeline red on the day it lands, for reasons that have nothing to do with the code it is meant to protect. The decision to adopt it, and the cleanup it requires, belongs in a change of its own.

## Risks / Trade-offs

- [The workflow file itself is the only thing verified by nothing] -> The first run on the pull request that introduces it is the check; the workflow is small enough to be read and the run either builds the project or it does not. If it passes on the PR, `main` will pass too, because the gate is the local gate.
- [JDK version duplicated between `mise.toml` and the workflow] -> Accepted above; the mitigation is the spec requirement plus review of `mise.toml` bumps, not an automation. If the duplication proves painful, migrating the step to `mise-action` is a one-line change inside this workflow.
- [An advisory check is easy to ignore] -> The proposal deliberately leaves branch protection to the owner. Until it is enabled, the check informs but does not block. This is a known gap, not an oversight.
- [`mvn verify` on a cold cache is slow] -> First runs pay the full download; the cache makes subsequent runs ordinary. No test in the suite needs network beyond dependency resolution.
- [A failing run reports a Maven phase name rather than a sentence] -> `-B` plus Maven's phase-labelled output already identifies the failing plugin; reading it is a skill, not a barrier. The spec requires the failing check to be named, which Maven does.
