## Current Goal
* The project may have one or more build modules.
* Evaluate annotation usage in each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_annotation_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding annotation usage.
* This should include:
  * The most commonly used annotation types in production code.
  * The ratio of methods annotated with @Override — low ratios in classes that extend superclasses or implement interfaces suggest missing @Override discipline.
  * If a single framework accounts for more than 60% of annotations (e.g., org.springframework.*), note this as potential framework lock-in.
  * Classes with an unusually high number of annotations compared to the module average.
  * Note that annotations parsed from class files may differ from source-level annotations — consider this in evaluation.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}