## Current Goal

* The project may have one or more build modules.
* Evaluate circular dependencies in each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_circular_dependency_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding circular dependencies.
* This should include:
  * The number of cycles found in the module.
  * Each cycle with the classes involved and the cycle length.
  * Cycles involving more than 3 classes are particularly problematic and should be flagged as critical architecture smells.
  * For each cycle, consider whether shared interfaces or restructuring could break the dependency.
  * Pay attention to cycles involving core domain classes vs infrastructure or utility classes.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
