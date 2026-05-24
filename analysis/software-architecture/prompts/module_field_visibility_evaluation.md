## Current Goal

* The project may have one or more build modules.
* Evaluate the field visibility distribution of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_field_visibility_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding field design.
* This should include:
  * The distribution of field visibility levels (PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE).
  * Public fields on domain classes (excluding static final constants) indicate encapsulation leaks.
  * A class with all fields public and only getters/setters as methods may be a data class / anemic domain model.
  * A class with many fields (more than 10) and many methods (more than 20) may be a god class.
  * The proportion of static and final fields (constants) vs instance fields.
  * Pay attention to differences between production, test, and generated code.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
