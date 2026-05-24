## Current Goal

* The project may have one or more build modules.
* Evaluate the method visibility distribution of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_visibility_distribution_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding method visibility.
* This should include:
  * The distribution of visibility levels (PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE) across production code.
  * The ratio of public methods to total methods as an indicator of encapsulation quality.
  * The proportion of static methods as an indicator of procedural-style design.
  * The proportion of final methods as an indicator of design for inheritance.
  * Pay attention to differences between production, test, and generated code.
  * A high percentage of public methods (more than 50 percent) in production code may indicate encapsulation leaks.
  * A very high percentage of public methods (more than 80 percent) combined with few or no private or protected methods may indicate an anemic domain model.
  * A high proportion of static methods (more than 40 percent) in production code may indicate a procedural programming style.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
