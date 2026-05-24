## Current Goal

* The project may have one or more build modules.
* Evaluate the method nesting depth of each module separately, cross-referenced with cyclomatic complexity data.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_method_nesting_depth_report' tool with the module name as parameter to get nesting depth distribution.
* Additionally, call the 'java_get_cyclomatic_complexity_module_report' tool with the module name as parameter to get cyclomatic complexity data for cross-referencing.
* Evaluate the result in relation to common architecture guidelines regarding method readability.
* This should include:
  * The distribution of nesting depth values across methods.
  * Methods with nesting depth greater than 4 should be flagged as readability refactoring candidates.
  * Cross-reference with cyclomatic complexity:
    - Methods with high CC (greater than 10) but low nesting depth (less than 3) are "flat complex" — high complexity but readable structure.
    - Methods with low CC (less than 5) but high nesting depth (greater than 5) are "nested spaghetti" — low complexity but deeply nested, poor readability.
    - Methods with both high CC (greater than 10) and high nesting depth (greater than 5) are the worst case — complex AND hard to read.
  * Pay attention to differences between production, test, and generated code.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
