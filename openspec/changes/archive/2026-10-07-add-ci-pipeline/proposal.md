# Proposal

## Why

The repository has no build verification in GitHub Actions. The only workflow the GitHub API reports is the auto-generated dynamic `Dependabot Updates` workflow, and its three runs (2025-11-10, 2026-06-20, 2026-07-02) are dependency-update jobs, not builds. There is no `.github` directory on the remote (the contents API returns 404), none in the working tree, and none in the history of any remote branch. The Actions tab therefore stops at 2026-07-02 while `main` has carried 24 commits and three pull-request merges (#12 on 2026-09-18, #13 on 2026-09-28, #16 on 2026-10-07) since then.

Every one of those merges ran zero tests. The project's gate is `mvn verify`, and only `verify` reaches the checks that catch the defects this project has actually shipped: the failsafe `smoke/JarSmokeIT` that launches the packaged uber-jar, and the `process-classes` SBOM generation whose tests parse `target/bom.json`. `shaded-jar-integrity` already requires that verification to happen; nothing requires it to happen on a merge, so it happens only when someone remembers to run it.

## What Changes

- Add `.github/workflows/ci.yml`: a `verify` job that runs on pushes to `main`, on every pull request, and on manual dispatch.
- The job checks out the repository, provisions JDK 25 and Maven with dependency caching, and runs `mvn -B verify`. No separate `test` step: `verify` is the existing, documented gate and is what makes the packaged artefact and the SBOM part of the check.
- Tool versions come from the JDK the project already compiles against (`maven.compiler.source/target = 25`, `mise.toml` pins `openjdk-25` and Maven 3). The workflow does not introduce a second, competing version source for the project itself.
- Excluded from this change, deliberately:
  - Enabling branch protection that requires the new check. That is a repository setting rather than a file, and it can only be applied after the workflow exists. The change delivers the check; the owner turns it into a required one.
  - A step running `openspec validate --all --strict`. That command currently reports 31 passed and 11 failed of 42 items, all WARNING-level (over-long requirement texts, placeholder `## Purpose` sections in `batch-java-metric-evaluation` and `batch-java-report-collection`). Adding it now would make the first run red for reasons unrelated to the code. Whether the gate is added at all, and whether the eleven findings are cleaned up first, is an open decision for the owner.
  - Any cleanup or archiving of the completed-but-unarchived changes (five of them, and they touch the same spec files the `--strict` findings name).

No source, `pom.xml`, or dependency changes. The build gate itself is unchanged.

## Capabilities

### New Capabilities

- `continuous-integration`: the repository verifies its own build on every push to `main` and on every pull request, using the same gate a local release uses, and reports the outcome on the pull request. This is a durable behaviour of the repository rather than a one-off setup step, which is why it is captured as a spec: it states what must be verified, on which events, and that the verification must be the packaged-artefact gate rather than a compile-only substitute.

### Modified Capabilities

None. `shaded-jar-integrity` already requires the build to verify the packaged artefact and to fail when a runtime-loaded class is missing; CI reuses that gate instead of weakening or extending it, so its requirements do not change.

## Impact

- New file `.github/workflows/ci.yml`. Nothing else in the repository changes.
- Consumers of the repository: pull requests gain a status check. Until branch protection requires it, the check is advisory.
- Runtime and quota: each run is one Linux job performing a full `mvn verify` (surefire over 54 test classes, failsafe `JarSmokeIT`, SBOM generation). Dependencies are cached; first runs on a cold cache are the slow ones.
- Secrets: none. No model provider, API key, or network service is needed to build or to run the tests.
- Out-of-tree: a GitHub repository setting (branch protection / required status checks) is affected but is not part of this change.
