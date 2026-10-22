# Proposal

## Why

Four of the fifty-one main specs no longer describe the system. Two of them name a file that was deleted
in the move to AsciiDoc (`Documentation.md.hbs`, renamed to `Documentation.adoc.hbs`), one of them still
calls the output a markdown report, one of them states a count of prompt files that has been wrong since
three capabilities were added, and one of them carries a note about how its own delta was written - a
scaffolding remark that ended up in a main spec and reads as a requirement to whoever finds it.

The drift is not harmless. A spec that names a file that no longer exists sends a reader to look for
something that is not there, and a spec that names a count that is wrong cannot be used to decide whether
the requirement it describes is met. The duplicate that the drift left behind is worse: two capabilities
state the same template-rendering rule, so a change to the rule has two places to be made and one of them
will be missed.

## What Changes

- Correct the two `## Purpose` sections that name the deleted template file and the deleted markdown
  output. A delta cannot reach them: the archive ignores a delta's Purpose for a spec that already has
  one, so these two lines are edited in the main specs directly, as the OpenSpec command itself instructs.
- Fold the template-rendering requirement of `enforce-evaluation-object-format` into
  `documentation-template`, which already states it, and keep one place where the rule lives.
- Rename `enforce-evaluation-object-format` to `enforce-evaluation-prompt-format`, because after the
  duplicate is removed it states one concern only: that the evaluation prompts require `evaluations[]`
  items as JSON objects rather than plain strings. The name and the Purpose of the capability are changed
  to say that.
- Replace the scenario that counts the evaluation prompts with one that states the rule, so the
  requirement cannot go stale when the next capability adds a prompt.
- Remove the note about how the `analysis-reference` delta was written from the requirement it sits in.

## Capabilities

### New Capabilities

- `enforce-evaluation-prompt-format`: the evaluation prompts require `evaluations[]` items as JSON objects
  with all required fields rather than plain strings.

### Modified Capabilities

- `documentation-template`: the requirement that the template renders `evaluations[]` items as objects
  states the path of the template it applies to, and the purpose of the capability names the AsciiDoc
  output instead of the Markdown output that was replaced.
- `analysis-reference`: the requirement that the reference holds one entry per documented analysis no
  longer carries a note about how its delta was written.

### Retired Capabilities

- `enforce-evaluation-object-format`: the template-rendering requirement it stated is now stated once, by
  `documentation-template`. The capability is replaced by `enforce-evaluation-prompt-format`, which states
  the prompt-side concern that remains.

## Impact

- `openspec/specs/`: four spec files are edited, one spec directory is removed and one is added. No
  requirement changes what the system does.
- `analysis/software-architecture/`: unchanged. The prompts, the schema and the template already state
  the object format; only the specs that describe them were behind.
- No Java code, no test and no configuration change. The requirements that are corrected were already
  met by the implementation, which is why no verification against the running system is needed for them.
