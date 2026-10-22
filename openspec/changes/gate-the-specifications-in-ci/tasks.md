# Tasks

## 1. Pin the validation tool

- [x] 1.1 Add the validation tool to the `tools` section of `mise.toml` with its version pinned, and add the Node runtime the tool needs, so that `mise install` provides the tool; verified: `mise install` succeeds and `mise exec -- openspec --version` reports the pinned version. The tool is written as the key `"npm:@fission-ai/openspec"` with the version as its value, because the backend prefix has to be part of the key.
- [x] 1.2 Pin the same version in the validation step of `.github/workflows/ci.yml`, so that the runner uses the version the developer uses; verified: the workflow names the same version as `mise.toml`

## 2. Add the validation to the verification run

- [x] 2.1 Provision the Node runtime in `.github/workflows/ci.yml`, so that the validation tool can be fetched; verified: the workflow has a step that sets up Node before the validation step
- [x] 2.2 Add a step that runs the strict validation of the specifications and the changes in flight, and place it before the build step, so that a specification that does not validate fails the run without costing the build; verified: the step is placed before the build step and the command it runs is the strict validation
- [x] 2.3 Confirm that the validation step fails the run when a specification does not validate, by running the command of the step against a specification that is temporarily made invalid and by observing its exit code; verified: the command exits with a failure and names the specification, and the specification is restored afterwards

## 3. Keep the two pins equal

- [x] 3.1 Add a test that reads `mise.toml` and `.github/workflows/ci.yml` and fails when the pinned versions of the validation tool differ, and place it with the other tests of the project; verified: `mvn test -Dtest=ContinuousIntegrationGateTest` passes with the two pins equal, and fails when one of them is changed
- [x] 3.2 Verify that the test reads the version from the files themselves and not from a constant, by changing one of the two pins and observing that the test fails; verified: the test fails and names the file whose pin differs

## 4. Verify the gate against the repository

- [x] 4.1 Run `openspec validate --all --strict` and confirm that every specification of the project and every change in flight validates; verified: the command reports all specifications as valid and exits with a success
- [x] 4.2 Run `mvn verify` and confirm that the build, the unit tests, the SBOM check and the jar smoke test pass; verified: the build succeeds with 607 unit tests and 6 smoke tests, no failure and no error
- [x] 4.3 Confirm that the gate does not report the archived changes, by reading the command the step runs; verified: the command validates the specifications and the changes in flight, and does not carry the flag that validates the archived changes

## 6. Keep the repository in step with the gate

- [x] 6.1 Update the description of the continuous integration run in `AGENTS.md`, which named only `mvn -B verify` and would have described the run wrongly after the gate was added; verified: the description names the validation of the specifications and the pin that the test keeps equal
- [x] 6.2 Update the fixture of `FileToolTest.GetAllFilesInDirWithSubdir`, which lists the test tree of the repository and does not know the package of the new test; verified: the fixture names the new package and the test passes

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- The change `split-the-long-requirements` has to be archived first, because the strict validation fails on the specifications as long as the twelve long requirements are in place.
- A later change can complete the task lists of the four archived changes that report an unfinished task, and the gate can then be extended to the archived changes.
