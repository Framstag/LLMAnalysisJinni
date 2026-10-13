## Current Goal

* We want one ubiquitous language glossary for the whole project.
* Every module is judged against this glossary afterwards, so each term has to carry exactly one meaning before the first module is judged.
* This task runs once for the project, not once per module.

## Facts

{{> macros/list_of_modules.md}}

The modules describe themselves as follows:

{{#modules.modules}}
* Module {{name}}: {{purpose.purpose}}
{{/modules.modules}}

## Solution Strategy

* Call **`java_get_all_domain_vocabulary_reports`** once. It returns the counted vocabulary of every Java module, the leading candidate terms per module and the tier each term got from the lexicon.
* Build the glossary from the leading candidate terms of the modules and from the module descriptions above.
* Classify each term as `domain`, `technical` or `generic`:
  * `domain` when the term names something the module and its project is about.
  * `technical` when the term names a mechanism, a pattern or a technology, whatever the module purpose is.
  * `generic` when the term names shape rather than meaning.
* Name in `modules` the modules that establish the term. A term belongs to a module when the module's purpose is about it and the term is one of its leading terms.
* When two modules use the same term for different things, record the term in `ambiguousTerms` with one entry per meaning and the modules of that meaning. Do not silently decide for one of the meanings.
* Record in `synonyms` other terms that the project uses for the same concept.
* Do not turn the glossary into a term list: a term that names nothing carries no meaning and is left out.
* Bound the glossary: at most 120 terms and at most 20 ambiguous terms, each meaning in one sentence. A longer response is cut off before its JSON is complete, so it cannot be parsed and the task is rejected and retried; an exhaustive glossary is not a better glossary.

## Hints

* The lexicon of the analysis classified the terms it is sure about. A term it does not list is a candidate, not a decision; the decision is yours, and this glossary is where it is written down.
* Judge a term from the purpose of the modules that use it, not from the way it looks.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `DomainGlossary` response schema.
* Return at most 120 `terms[]` entries and at most 20 `ambiguousTerms[]` entries.
* Each `terms[]` entry MUST be a JSON object with fields: `term`, `classification`, `meaning`, `modules` and optionally `synonyms`. Do NOT use plain strings.
* Each `ambiguousTerms[]` entry MUST be a JSON object with the fields `term` and `meanings`, and each meaning MUST name its meaning and its modules.
* Write the `meaning` of a term in one sentence and in the language of a software architect.
