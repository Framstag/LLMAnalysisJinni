## Current Goal
* The project may have one or more build modules.
* Evaluate import diversity and dependency hygiene in each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_import_diversity_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding import diversity and dependency hygiene.
* This should include:
  * The ratio of external imports (frameworks, libraries) to internal imports (project's own packages).
  * If a module has more than 80% external imports, flag this as low internal code reuse — the module may be too thin or rely excessively on frameworks.
  * If more than 50% of external imports resolve to a single framework prefix, note this as potential framework lock-in.
  * Heavy reliance on `java.*` or `javax.*` imports (more than 40% of external) is a neutral finding but context-dependent — it may indicate the module handles standard library tasks without abstraction.
  * Unusual import prefixes that suggest dependency sprawl — many different external libraries for similar concerns.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}