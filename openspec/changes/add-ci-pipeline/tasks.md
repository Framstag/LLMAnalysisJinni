# Tasks

## 1. Workflow file

- [x] 1.1 Create `.github/workflows/ci.yml` with a workflow named `CI` and triggers `push` restricted to `main`, `pull_request`, and `workflow_dispatch`; verify the file is valid YAML (it parses without error) and that the Actions tab lists the workflow by name once pushed
- [x] 1.2 Add the `verify` job on `ubuntu-latest` with `actions/checkout` and `actions/setup-java` (`distribution: temurin`, `java-version: '25'`, `cache: maven`); verify the run log shows the provisioned JDK as version 25
- [x] 1.3 Add the build step `mvn -B verify` and nothing else; verify the run log reaches the `verify` phase and that the failsafe `JarSmokeIT` and the SBOM tests are executed in that run

## 2. Gate matches the local gate

- [x] 2.1 Run `mvn verify` locally on the change branch and confirm the gate is green before trusting the pipeline, including `smoke/JarSmokeIT` against the packaged jar and the SBOM tests over `target/bom.json`; verify the build output names both
- [x] 2.2 Confirm the workflow contains no reduced substitute for the gate (no `-DskipTests`, no bare `test` goal, no `package` instead of `verify`); verify by reading the run log for the surefire and failsafe phases and by grepping the workflow file for the Maven invocation

## 3. Documentation

- [x] 3.1 Document in `AGENTS.md` (Build section) that pushes to `main` and pull requests run `mvn verify` in the `CI` workflow, that this is the same gate as the documented local build, and that the check only blocks merges once branch protection requires it; verify the text names both the workflow path and the exact command
- [ ] 3.2 Add the workflow status badge to `README.md`; verify the badge image URL resolves and reports the state of the `CI` workflow

## 4. Integration verification

- [x] 4.1 Push the branch and open a pull request, then confirm a `CI` check appears on the pull request and that its run is associated with the pull request's head commit; verify by reading the check name and the commit SHA from the pull request page
- [x] 4.2 Prove the check fails when the gate fails: on a throwaway branch, introduce an intentionally failing test, push, and confirm the check turns red and names the failing check; then discard the throwaway branch and confirm `main` is untouched
- [x] 4.3 Confirm an on-demand run: start the `CI` workflow manually for the change branch without pushing a commit; verify a run appears for that branch's head commit

## Workflow follow-up

- Enable branch protection on `main` and require the `CI` check, then confirm the repository setting lists it as a required status check.
- Decide separately whether `openspec validate --all --strict` becomes a pipeline gate; if it does, clean the current 11 WARNING-level findings (over-long requirement descriptions, placeholder `## Purpose` in `batch-java-metric-evaluation` and `batch-java-report-collection`) first, and unarchive the five completed changes before touching their spec files.
- Archive this change after review.
