# Design

## Context

The specifications of the project are read by two kinds of readers: a person who wants to know what the system
does, and the archive command, which applies a change to them. The second reader is the harder one, and it
was read before anything was written, because its rules decide the shape every delta in this change has to
have.

Three rules of the archive command were established by running it against a throwaway project.

1. **A modified requirement replaces the whole block, and the block has to hold every scenario the current
   specification still has.** A modified block that omits a scenario is refused, and the archive aborts
   without changing a file. A scenario that is renamed counts as an omitted one.
2. **A scenario cannot leave the requirement it belongs to.** Moving a scenario into another requirement does
   not satisfy the rule above, because the requirement it left still lacks it.
3. **A requirement cannot be removed and added again under the same name.** A removed block and an added
   block that carry the same name are rejected before the archive runs.

A fourth fact decides how the length is measured: the strict validation counts the text of a requirement up
to its first scenario. The scenarios of a requirement are free; its prose is not.

The current state of the twelve long requirements was read against the code before the change was drafted.
Ten of them describe code that behaves as they say. Three of them do not:

| capability | statement in the specification | behaviour of the code |
|---|---|---|
| `import-diversity` | the report returns three pairs of distributions, one external and one internal per kind of code | the report returns three distributions, each holding the external and the internal entries side by side, marked with a label |
| `import-diversity` | the package namespace is determined from the module path and the source directories | the package namespace is the package of the first class the tool finds |
| `import-diversity` | when the namespace cannot be determined, the most common package prefix is used | there is no such fallback; a module without a package namespace marks every import source as external |
| `java-method-nesting-analysis` | a depth of 0 means no control structure or all of them at the top level | a control structure at the top level of a method already has a depth of 1 |
| `inter-module-dependency-analysis` | the evaluation uses an instability of 30 or less as stable | the evaluation prompt uses an instability below 30 as stable and 30 to 70 as balanced |

The import diversity statements and the nesting depth statement were checked against
`src/main/java/com/framstag/llmaj/tools/java/JavaTool.java`, `JavaFileParser.java` and the evaluation prompt
`analysis/software-architecture/prompts/inter_module_dependency_evaluation.md`.

## Goals / Non-Goals

**Goals:**

- Every requirement of the project states one behaviour, and states it in a text the strict validation accepts.
- Every statement of a requirement is true of the code, and the correction of a false statement describes the
  code rather than a wish.
- No scenario that exists today disappears, and no rule that a scenario states today is lost when the text
  that repeated it is shortened.
- A rule that two capabilities stated together ends up in one capability.

**Non-Goals:**

- No change to the behaviour of the system. Every requirement that is written describes code that already
  exists; the three that were wrong are corrected towards the code, not the other way round.
- No change to the prompts, the schemas or the documentation template. The evaluation prompts are read to
  confirm what the evaluation does, not to change it.
- No rewrite of the wording of the requirements that are already correct. Only the twelve long ones are
  touched, and only as far as the length and the correctness demand.
- No gate in the continuous integration run. That is the next change, and it needs this one to be archived
  first.

## Decisions

### The detail that does not fit goes into a scenario, unless it is a rule of its own

Three shapes are used, and which one a requirement gets follows from what the moved text is.

- **The text states something that a scenario of the requirement already states.** The text is dropped. The
  requirement `Raw module report carries the weighted reference record` said that a report of an older format
  version is not reused, and two of its scenarios said the same; the clause is dropped and the scenarios keep
  the rule.
- **The text states a rule that has a behaviour of its own.** A requirement of its own is added, with new
  scenarios. The rules that classify the imports of the inter-module dependency report, that categorise the
  import sources, that measure the nesting depth and that derive a package from an import are four rules with
  four behaviours, and each of them becomes a requirement.
- **The text states a detail of the behaviour of the requirement.** A scenario is added. The fields the engine
  log records, the repair hint of a payload failure, the built-in annotations of the annotation distribution
  and the balanced band of the instability are details, and a scenario states them in the form the
  specification uses everywhere else.

The alternative considered was to keep every requirement in one piece and let its text carry the detail, with
the length rule met by writing more concisely. It was rejected for the three requirements that would still not
fit: the inter-module dependency report tool, the import diversity report and the package level tangle report
state more than one behaviour each, and no amount of tightening makes one requirement out of them.

### The false statements are corrected towards the code

The import diversity requirement described a tool that does not exist. The decision is to correct the
specification, not the tool.

The alternative considered was to change the tool to return a pair of distributions per kind of code and to
fall back to the most common package prefix. It was rejected: the tool is what the model reads, the prompt
never states the shape of the result, and nothing in the repository shows that the pair was ever intended
rather than described by mistake. Changing the tool would change the analysis output of every project for the
sake of a sentence in a specification. The corrected requirement states what the tool returns, including the
marking of the external and the internal entries and the rule that a module without a package namespace marks
every import source as external.

The nesting depth requirement is corrected the same way: a depth of 0 means that the method holds no control
structure, and a control structure at the top level of a method has a depth of 1. The evaluation task of the
inter-module dependency analysis is corrected the same way: an instability below 30 is stable, 30 to 70 is
balanced, and the criterion is taken from the evaluation prompt, which is what the model reads.

### The duplicated execution trace rule is split by concern, and the console side is rewritten

Two capabilities stated the same rule: the effective value of the execution trace follows the configuration
precedence order and defaults to disabled, an effective trace keeps the TUI from starting, and the TUI keeps
the console quiet from the frame it first paints.

The rule is given one home per concern. The capability that owns the console output states what the console
does under the trace, and the capability that owns the display mode states what the display does under the
trace. The generic precedence order itself is not restated by either of them, because the capability
`cli-config-precedence` already states it for every overridable option, the console execution trace among
them.

The old requirement of the logger capability is removed and a new one is added under a different name, because
the archive refuses a removed and an added block that carry the same name. The scenarios of the old
requirement are not carried over verbatim: the new requirement gets new scenarios that state the console
behaviour, and the display side keeps the scenarios it has, among them the one that states that the trace and
the TUI never render at the same time.

The alternative considered was to keep one of the two requirements and remove the other. It was rejected
because the surviving requirement would have to carry both concerns to keep its own scenarios true, which
leaves the duplication in place under one name instead of two.

### No scenario is dropped, and every added scenario is checked against the code

A scenario is a claim about the system, and an added scenario is a new claim that nobody has checked yet.
Every scenario this change adds is verified against the implementation before the change is archived, and the
verification is written down in the tasks.

The alternative considered was to add the scenarios and to let the next reader trust them. It was rejected
because three of the twelve long requirements turned out to state behaviour the code does not have, which is
the failure mode this change exists to remove.

## Risks / Trade-offs

**A new scenario states something the code does not do** → Every added scenario is checked against the
implementation before the archive, and the tasks name the file that proves it. A scenario that cannot be
proven is not added; the detail it carries stays in the text of the requirement, and the requirement is split
instead.

**The archive refuses the change because a modified block drops a scenario** → The spikes that established
the rules of the archive were run before the deltas were written, and every modified block in this change
carries the scenarios of the requirement it modifies, with the new ones added. The validation of the change
reports a dropped scenario as an error before the archive runs.

**The correction of a false statement is read as a change of behaviour** → The corrected statements describe
code that already behaves that way, and the tasks record the file and the line that prove it. No Java code
changes in this change, so a corrected statement cannot have changed the behaviour.

**The length of a requirement is measured differently by the validator than by the author** → The acceptance
check is the validator itself: `openspec validate --all --strict` has to report all fifty-four specifications
as valid after the archive. The text of every rewritten requirement is measured before the archive with the
same rule the validator uses.

**The removal of the old requirement of the logger capability leaves a gap in it** → The new requirement
states the console behaviour that the old one stated, with scenarios of its own, and the scenarios that
concerned the display are kept by the capability that owns the display. The tasks check that the removed
requirement leaves no behaviour of the console unstated.

## Migration Plan

None. The change touches no code, no configuration and no running state. It corrects the descriptions of
behaviours that already exist and splits descriptions that were too long to read.
