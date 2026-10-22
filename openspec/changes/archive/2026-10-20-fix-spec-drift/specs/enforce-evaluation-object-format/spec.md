# Spec Delta

## REMOVED Requirements

### Requirement: Prompt enforces object format for evaluations

**Reason**: The capability that carried this requirement is renamed, because after the duplicate template
requirement is removed it states one concern only: that the evaluation prompts require the object format.
The requirement itself does not change and is carried by the renamed capability.

**Migration**: The requirement is carried by the capability `enforce-evaluation-prompt-format`, which states
it with the same scenarios.

### Requirement: Template only handles object format

**Reason**: The same rule is stated by the requirement `Evaluation table rows render from object fields` of
the capability `documentation-template`, which holds all three of its scenarios and names the path of the
template it applies to. Two capabilities stating one rule means two places to change it, and one of them
is missed.

**Migration**: The statement is carried by the requirement `Evaluation table rows render from object fields`
in the capability `documentation-template`.
