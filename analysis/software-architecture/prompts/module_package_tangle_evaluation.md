## Current Goal
* The project may have one or more build modules.
* Evaluate circular dependencies between packages in each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_package_tangle_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding package-level circular dependencies.
* This should include:
  * The number of package-level cycles found in the module.
  * Each cycle with the packages involved and the cycle length.
  * Cycles involving more than 3 packages are particularly problematic and should be flagged as major architecture erosion concerns.
  * For each cycle, consider whether restructuring package boundaries or extracting shared interfaces could break the dependency.
  * Package-level cycles are often more entrenched than class-level cycles — they indicate deeper modularization issues.
  * Pay attention to cycles involving core domain packages vs infrastructure or utility packages.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}