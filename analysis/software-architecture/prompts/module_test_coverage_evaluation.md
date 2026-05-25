## Current Goal

* The project may have one or more build modules.
* Evaluate the test coverage of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_test_coverage_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding test coverage.
* This should include:
  * The total number of production classes, tested classes, and untested classes.
  * The test-to-production ratio — a ratio below 50% may indicate insufficient test coverage.
  * The list of untested production classes, especially core domain classes.
  * Modules with production code but no test BuildUnits have no test coverage at all.
  * Pay attention to differences between production and test code.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
