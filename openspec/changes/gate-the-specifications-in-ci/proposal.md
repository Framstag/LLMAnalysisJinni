# Proposal

## Why

The specifications of the project drift away from the system, and nothing notices. The verification run of the
project builds the artefact and runs the tests, and it never reads a specification. Three changes in the
repository exist only because the drift was found by a person or an agent reading the specifications after
the fact: one corrected four specifications that named a file that had been deleted, one corrected a count that
had been wrong since three capabilities were added, and one retired a capability whose rule was stated twice.

The drift is cheap to introduce and expensive to find. A requirement that names a file that no longer exists
sends the reader to look for something that is not there, and a requirement that names a count that is wrong
cannot be used to decide whether the requirement it describes is met.

## What Changes

- The verification run validates the specifications of the project, in the strict mode, and fails when a
  specification does not validate.
- The validation covers the changes that are in flight as well, so that a delta that cannot be archived is
  reported before the archive is attempted and not by it.
- The version of the validation tool is pinned in the repository, for the runner and for the developer, and a
  test keeps the two pins equal.
- The validation runs before the build, so that a specification that does not validate fails the run in
  seconds instead of after the whole build.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `continuous-integration`: a requirement is added that states that the verification run validates the
  specifications of the project, in the strict mode, with a pinned version of the validation tool.

## Impact

- `.github/workflows/ci.yml`: one step is added, and the Node runtime it needs is provisioned.
- `mise.toml`: the validation tool is pinned, so that the version the developer validates with is the version
  the runner validates with.
- `src/test/java/`: one test is added that keeps the two pins equal.
- The verification run now depends on the network, because the validation tool is fetched from the npm
  registry. The build gate does not depend on it.
- The specifications of the project have to validate in the strict mode when this change lands, which is the
  state the change `split-the-long-requirements` leaves them in.
