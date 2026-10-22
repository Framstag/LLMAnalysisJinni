# Design

## Context

The drift is not uniform. Two of the four stale statements sit in a `## Purpose` section, and two sit in a
requirement body. The distinction decides how each can be corrected, and it is a property of the OpenSpec
archive command rather than of this project.

Three facts about the archive command shape the approach. They were established by reading the command's
implementation and confirmed by running the command against a throwaway project:

1. **A delta's `## Purpose` is ignored for a spec that already has one.** The archive prints
   `delta Purpose ignored; <spec> already has one. Edit <target> directly to change it.` and leaves the
   main spec's Purpose untouched. A delta therefore cannot correct a Purpose of an existing capability.
2. **A `MODIFIED` requirement replaces the whole block.** The archive refuses to apply one that omits a
   scenario the current spec still has, and validation reports the same. A scenario cannot be renamed
   inside a `MODIFIED` block; the archive treats the old name as dropped.
3. **A requirement cannot be both `REMOVED` and `MODIFIED` under the same name**, and a `REMOVED` plus
   `ADDED` pair must use different requirement names.

Fact 2 has a consequence that shapes one decision below: a scenario whose *name* has to change cannot be
changed in place. It has to be carried by a requirement that is added whole.

## Goals / Non-Goals

**Goals:**

- Every statement in the main specs names a file that exists, or states a rule without naming a file.
- Every rule the specs state is stated in one place.
- The corrections are recorded where the archive can apply them, so that the change history says why a
  requirement changed and not only that it did.

**Non-Goals:**

- No change to what the system does. Every requirement that is corrected was already met by the
  implementation, which is why none of them needs a verification against a running system.
- No test is added for a corrected requirement. The corrected statements are about file names and about
  which capability owns a rule; the behaviour they describe is already covered.
- The wording of the requirements that are correct is left alone. The drift is corrected, not rewritten.

## Decisions

### The two Purpose sections are edited in the main specs directly

The archive ignores a delta's Purpose for an existing spec, so there is no delta that can carry the
correction. The alternative considered was to correct the Purpose lines in the same commit that archives
the change, which is what this design does - the direct edit is committed together with the archive, so
one commit carries the whole correction. The other alternative, a separate commit after the archive, was
rejected because it splits one correction across two commits for no gain.

A direct Purpose edit survives a later archive: the archive rewrites the requirement blocks of a spec and
leaves the Purpose of the same file alone. This was confirmed by editing a Purpose in a throwaway project
and archiving a change that modified a requirement of the same spec.

### The duplicate requirement is removed, not the count in it

`documentation-template` and `enforce-evaluation-object-format` stated the same rule: that the template
renders `evaluations[]` items as JSON objects and has no fallback branches. The rule is kept in
`documentation-template`, whose requirement holds three scenarios and states the rule in full. The
requirement in `enforce-evaluation-object-format` is removed, and the one detail it carried that the
survivor did not - the path of the template - is folded into the survivor's existing scenario
`Template has no conditional fallbacks`, which named the template only as "the template source".

Folding into the existing scenario rather than adding a new one keeps the survivor at three scenarios, and
keeps the archive's scenario-balance guard satisfied without renaming anything.

The alternative considered was to keep the requirement in `enforce-evaluation-object-format` and remove it
from `documentation-template`. It was rejected: the capability that keeps it would then be named after the
template, and the capability named after the template is the one that also states what the other
capabilities render, so it is the natural home for a rule about how the template renders.

### The capability is renamed by retiring it and recreating it under the new name

OpenSpec has no operation that renames a capability. `RENAMED` applies to requirements only, and a
capability is a directory. The path that works is the one the archive already supports: a delta with only
`REMOVED` requirements, together with `retire_capabilities: true` in the change's `.openspec.yaml`, deletes
the capability's main spec, and a delta with `ADDED` requirements under the new capability path creates
the new one, carrying the Purpose from the delta.

The marker is required because the deletion is not recoverable from the working tree - only from git - so it
is the author's call and not an inference from the shape of a delta.

Because the capability is retired and recreated, the two corrections that would otherwise need a
`MODIFIED` block are written into the `ADDED` block of the new spec: the template file name, and the
scenario name. Neither needs the "cannot rename a scenario" workaround, because the new spec's scenarios
are new names from the start.

The alternative considered was to keep the old capability name and only remove the duplicate requirement.
It was rejected: the capability would then be named `enforce-evaluation-object-format` while stating
nothing about the template, and the name would describe a concern the capability no longer has.

### The count in the scenario name is removed instead of corrected

The scenario `All 17 evaluation prompts are updated` was written when seventeen `*_evaluation_all.md`
prompts existed. There are twenty now, and the count has been wrong since three capabilities were added,
without anything recording that it was wrong. The `WHEN` clause of the scenario already says "all files
matching `analysis/software-architecture/prompts/*_evaluation_all.md`", so the number adds nothing but a
value that decays.

The scenario is renamed to `Every evaluation prompt states the format`, which states the rule. The
alternative considered was to write the current count, twenty. It was rejected for the same reason: it
would decay again the next time a capability adds a prompt, and it would decay silently.

### The note about how the delta was written is removed from the requirement

`analysis-reference` held the sentence "This delta is written against the requirement as it reads after
the change `2026-10-13-add-domain-vocabulary-analysis` has been archived, because that change adds the
domain vocabulary analyses to the same enumeration." It describes how a change was written, not what the
system does, and it was true only while that change was in flight. It is removed from the requirement
body, and the requirement keeps all five of its scenarios.

## Risks / Trade-offs

**The retired capability's spec file is deleted by the archive** → It is recoverable from git, and the
retire marker in `.openspec.yaml` makes the deletion an explicit decision of this change rather than an
inference. The archive prints the `git checkout` command that restores it.

**The new capability's Purpose is carried from the delta, and the archive warns when it is too brief** →
The Purpose is written to be longer than the threshold the archive warns about, and `openspec validate
--strict` is run after the archive to confirm it.

**The new spec's heading is generated in the form `# enforce-evaluation-prompt-format Specification`**
while its neighbours that were written by hand use a title in the form `# Enforce Evaluation Prompt
Format`. The two forms are both in use in the repository. The generated form is left as the archive
produces it, and the heading is corrected in the same commit that archives the change, so that the
specification directory holds one style for the specs written by hand.

**A corrected requirement could mask a real defect** → Every corrected statement was checked against the
implementation before it was corrected: the template named in the two file references exists under the
name the correction gives it, all twenty files matching the prompt glob carry the object format bullet,
and the analysis reference holds one entry per documented analysis.

## Migration Plan

None. The change touches no code, no configuration and no running state. It is a correction of the
specifications that describe what the system already does.
