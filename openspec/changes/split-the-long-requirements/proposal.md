# Proposal

## Why

Twelve of the fifty-four main specifications hold a requirement whose text is longer than five hundred
characters, and `openspec validate --all --strict` reports each of them as a failure. Twelve of them did so
before the last drift fix as well, so the debt is not decreasing on its own.

The length is not the real problem. Three of the twelve long requirements carry statements that the code does
not implement, and one of them states a tool that does not exist: the import diversity report is specified to
return three pairs of distributions, one external and one internal per kind of code, and to fall back to the
most common package prefix of the module when the package namespace cannot be determined. The tool returns
three distributions, each holding external and internal entries side by side, and it takes the package of the
first class it finds as the namespace. The requirement was written in the same commit as the tool, so it never
described it.

A requirement that cannot be read in one sitting is a requirement nobody checks. A requirement that states a
behaviour the code does not have is worse than a long one, because a reader trusts it.

## What Changes

- Shorten the twelve long requirements so that every one of them states one behaviour in a text that stays
  under the length the strict validation allows. The detail that does not fit is moved into scenarios, which
  is where the validator asks it to go, or into a requirement of its own when the detail is a behaviour in its
  own right.
- Correct the statements that the code does not implement, in the capabilities that hold them: the import
  diversity report, the nesting depth measurement and the classification of the imports of the inter-module
  dependency report.
- Give the two duplicated statements of the execution trace one home each. The console side moves to the
  capability that owns the console output, the display side stays with the capability that owns the display
  mode, and the rule that the two capabilities stated together is stated once.
- Keep every scenario that exists today. No requirement loses a scenario, because the archive refuses a
  modified requirement that drops one, and because every one of them states behaviour that holds.

## Capabilities

### New Capabilities

None. Every requirement that is added belongs to a capability that already exists.

### Modified Capabilities

- `annotation-density`: the requirement that states the annotation distribution report loses the two
  sentences about built-in annotations and about annotations that only appear in the import graph, and states
  them as scenarios instead.
- `class-dependency-graph`: the requirement that states the raw module report keeps the record and the
  format version, and drops the two clauses that its own scenarios already state.
- `import-diversity`: the requirement that states the import diversity report is corrected to the tool that
  exists, and the rule that categorises the import sources becomes a requirement of its own.
- `inter-module-dependency-analysis`: the requirement that states the inter-module dependency report tool
  keeps the report and the matrix, and the rules that classify the imports become a requirement of their own;
  the requirement that states the evaluation task keeps the task and states the criteria in prose.
- `java-method-complexity-analysis`: the requirement that states the method complexity report keeps its
  content and loses the redundant words.
- `java-method-nesting-analysis`: the requirement that states the nesting depth report keeps the report, and
  the rule that measures the nesting depth becomes a requirement of its own.
- `live-progress-display`: the requirement that states that the execution trace disables the TUI keeps the
  display rules and loses the definition of the trace, which the capability that owns the console output
  states; the requirement that states the diverted engine log keeps the routing and the bound, and loses the
  record fields, which become a scenario.
- `llm-interaction-logger`: the requirement that states the console execution trace is replaced by a
  requirement that states only what the console output does under the trace.
- `llm-response-retry`: the requirement that states the repair hint keeps the conversation rules and states
  the hint of a payload failure as a scenario.
- `package-tangle-detection`: the requirement that states the package level tangle report keeps the report,
  and the rule that derives a package from an import becomes a requirement of its own.

## Impact

- `openspec/specs/`: ten specifications change. Four requirements are added to capabilities that already
  exist, one requirement is removed, and the rest are rewritten. No requirement changes what the system does,
  and no requirement that describes a behaviour the code has is lost.
- `analysis/software-architecture/`: unchanged. The prompts and the schemas are not touched.
- No Java code, no test and no configuration change. The three capabilities whose statements were wrong
  describe code that already behaves as the corrected statements say.
- The strict validation of the specifications passes after this change, which is the precondition for the
  change that puts the validation into the continuous integration run.
