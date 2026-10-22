# Design

## Context

The verification run of the project does one thing: it runs `mvn verify`, which builds the packaged artefact
and runs the checks bound to that artefact. The run reads no specification, and nothing in the pipeline
compares a requirement with the code it describes.

The drift this misses is not hypothetical. The archive of the repository holds three changes that exist only
to correct specifications that no longer described the system: `fix-spec-drift` corrected four of them,
`fix-build-file-lookup-and-truncated-responses` and the changes around it corrected the counts and the file
names that the same drift had broken. Every one of them was found by reading the specifications, not by a
check.

The state of the validation itself decides the shape of the gate:

- `openspec validate --all --strict` reports the fifty-four specifications of the project, and it exits with
  a failure when one of them reports a warning. Twelve of them did so until the change
  `split-the-long-requirements` corrected them, and the gate can only be strict after that change is
  archived.
- `openspec validate --all` exits with a success even when the specifications report the twelve warnings, so
  it would catch a broken specification but not a decaying one.
- `openspec validate --archived` reports the archived changes, and it reports four of them as failed today
  because their task lists hold one to seven items that were never ticked. Those archives are older than the
  workflow that is now used, and their unfinished items are a matter of the past, not of the present.
- The validation tool is not pinned anywhere in the repository. The version that is used on this machine,
  1.14.3, is pinned in a local configuration file that the repository ignores, so a runner that fetched the
  newest version would run a different validation than the developer does.

## Goals / Non-Goals

**Goals:**

- A specification that does not validate fails the verification run.
- A specification that reports a warning fails the verification run.
- A delta that the archive would refuse fails the verification run, before the archive is attempted.
- The runner and the developer validate with the same version of the tool, and the repository records it.

**Non-Goals:**

- The archived changes are not gated. The four archives with unfinished task lists are a matter of the past,
  and gating them would mean rewriting the history of four changes to make a new check pass.
- The build gate is not touched. `mvn verify` stays the gate for the artefact, and the specification
  validation is a gate of its own.
- The specifications are not rewritten by this change. They have to validate, and the change that makes them
  validate is a change of its own.
- No new tool is introduced to the project. The validation tool is the one the project already uses.

## Decisions

### The gate runs the strict validation over the specifications and the changes in flight

The step runs `openspec validate --all --strict`. The `--all` covers the main specifications and the changes
that are in flight, and the strict mode turns every warning into a failure.

The strict mode is the one that matters here. The twelve long requirements that the previous change corrected
were not broken specifications; they were valid specifications that nobody read, and only the strict mode
reports them. The plain mode reports a success for them.

The alternative considered was to run the plain mode and to accept that the gate only catches a broken
specification. It was rejected: the drift that actually happened in this repository was not a broken
specification, it was a specification that stayed valid while it stopped being true, and the plain mode says
nothing about that.

### The validation runs before the build

The step is placed before the build step, not after it. The validation takes a second, the build takes
minutes, and a specification that does not validate should not cost the whole build to find out.

### The tool version is pinned twice, and a test keeps the two pins equal

The version is pinned in `mise.toml`, which is what the developer uses, and in the workflow, which is what the
runner uses. A test reads both files and fails when the versions differ.

The alternative considered was to let the runner use mise, so that `mise.toml` is the only place the version
is written. It was rejected: it would add a third-party action to the pipeline to install a tool chain that
the build does not need, and it would install the whole tool chain of `mise.toml` to run one command. The
test is cheaper than the action and it catches the same drift, which is a pin that drifts apart.

The alternative considered was to pin the version only in the workflow. It was rejected: the developer would
then validate with whatever version is on the machine, which is the situation the change is meant to end. The
local configuration file that pins the version today is not in the repository, so the pin has to be moved
into it.

### The archives are not part of the gate

`openspec validate --archived` reports four archived changes as failed, because their task lists hold items
that were never ticked. Those archives predate the current workflow, and their unfinished items say nothing
about the present state of the project. Gating them would mean editing the history of four changes to make a
new check pass, which is worse than the check.

A later change can complete those task lists, or can decide that they stay as they are, and the gate can then
grow by one flag.

## Risks / Trade-offs

**The gate fails the verification of a branch that carries an unfinished change** → That is the intent: a
delta that the archive refuses is worth knowing about before the archive is attempted. The delta of the change
that is in flight is itself validated by the gate, so the change that introduces the gate is the first thing
the gate has to survive.

**The gate depends on the network** → The validation tool is fetched from the npm registry on every run. The
build gate does not, and a run that fails in the fetch is a failed run and not a silent pass, because the
step fails when the command fails.

**The pinned version grows stale** → The test keeps the two pins equal, but nothing keeps the version itself
current. A later change can raise the pin, and the test then keeps the two pins equal to the new version.

**A specification that is valid but false passes the gate** → The gate checks the form of a specification, not
its truth. The truth of a requirement is checked by the change that writes it, and the tasks of the change
that corrects the drifting requirements require every new statement to be verified against the code.

## Migration Plan

The gate lands after the change `split-the-long-requirements` is archived, because the strict validation
fails on the specifications as long as the twelve long requirements are in place. The order of the two
changes is the order of their dependencies, and the dependency is stated in the task list of each of them.
