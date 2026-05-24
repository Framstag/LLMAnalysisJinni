## Current Goal

* The project may have one or more build modules.
* Evaluate the inheritance patterns of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_inheritance_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding inheritance.
* This should include:
  * The distribution of inheritance depth across classes — classes with depth deeper than 3 may indicate fragile hierarchies.
  * The distribution of interface implementation counts — classes implementing more than 5 interfaces may violate the Single Responsibility Principle.
  * The overall inheritance depth distribution — a flat distribution with most classes at depth 0 may indicate missing abstraction layers.
  * Pay attention to differences between production, test, and generated code.
  * A module with no classes extending other classes in the same module may indicate missed opportunities for polymorphism.
  * Deep inheritance chains (depth greater than 3) should be flagged as refactoring candidates.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
