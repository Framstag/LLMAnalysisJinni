## Current Goal

* The project may have one or more build modules.
* Evaluate the method complexity metrics of each module separately, cross-referenced with cyclomatic complexity data.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_method_complexity_report' tool with the module name as parameter to get parameter count and lines of code distributions.
* Additionally, call the 'java_get_cyclomatic_complexity_module_report' tool with the module name as parameter to get cyclomatic complexity data for cross-referencing.
* Evaluate the result in relation to common architecture guidelines regarding method complexity.
* This should include:
  * The distribution of parameter counts per method — methods with more than 7 parameters indicate an excessive parameter list smell.
  * The distribution of lines of code per method — methods with more than 30 AST statements may indicate god methods.
  * Cross-reference methods with high cyclomatic complexity (above 10), high parameter count (above 5), and high lines of code (above 30) — these are high-priority refactoring targets.
  * Methods with low cyclomatic complexity (below 5) but high lines of code (above 40) may indicate sequential operations without branching (e.g., long initialization blocks).
  * Pay attention to differences between production, test, and generated code.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
