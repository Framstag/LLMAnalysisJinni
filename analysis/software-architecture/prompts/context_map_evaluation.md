## Current Goal

* We want the boundaries of the project drawn from the language the modules speak, not only from the code they share.
* Propose the bounded contexts of the project, name the relation between them, and name what could not be placed.

## Facts

{{> macros/list_of_modules.md}}

The glossary that fixes the meaning of a term for the whole project:

{{#domainGlossary.terms}}
* Term "{{term}}" is {{classification}} and is established by: {{#each modules}}{{this}} {{/each}}
{{/domainGlossary.terms}}

{{#if domainGlossary.ambiguousTerms}}
Terms that different modules use with different meanings:

{{#domainGlossary.ambiguousTerms}}
* Term "{{term}}": {{#each meanings}}"{{meaning}}" in {{#each modules}}{{this}} {{/each}}; {{/each}}
{{/domainGlossary.ambiguousTerms}}
{{/if}}

## Solution Strategy

* Call **`java_get_domain_language_overview`** once. It returns the terms more than one module uses with their span, the terms a module borrows from another one with the owner of the term, the module pairs with their imports and their shared language, and the number of pairs per classification.
* Group the modules into bounded context candidates by the language they share:
  * Modules that share leading candidate terms and import each other are one context, or two contexts in a customer/supplier relation; decide by whether they share a domain or only a mechanism.
  * Modules that share no language at all belong to different contexts, however much they import from each other.
  * A module that is a technical mechanism, such as a binding, a container or a renderer, is a context of its own even when it is small; its language is the mechanism it implements, and folding it into the domain context beside it hides that the domain is spoken somewhere else.
* Name the relation between two contexts and **state the counted evidence behind it**: the import count between the pair and the shared leading terms, which the overview reports for the pair.
* Read the quadrant the overview reports:
  * `candidate domain leak` - the two modules share a domain vocabulary and do not import each other. This is the finding the analysis exists for: nothing in the code connects them, and they still talk about the same thing. Name the terms they share.
  * `translation` - the two modules import each other and share no vocabulary. The boundary pays for its own translation; say so and do not recommend a change.
  * `coupled` - both sides are present. Name the relation that the code and the language agree on.
  * `independent` - neither side is present. Two separate ways is a legitimate answer.
  * `structural evidence missing` - the inter-module dependency analysis has not written its matrix, so no pair was classified by it. Say that the structural side is unknown instead of reading the quadrant.
* A term that the glossary records as ambiguous marks a boundary that carries no translation. Name it and name the contexts that use the two meanings.
* A term one module borrows from another one is a leak only when neither the borrower's purpose nor a boundary role explains it. The overview reports for every intruding term whether the owner's description carries it; use that.
* Name in `unplacedModules` every module you could not place, with the reason. A module whose candidate vocabulary is below the minimum carries no shares and cannot be judged from metrics; say so.
* A project with one module has exactly one context and no relation. Do not invent a relation for it.

## Hints

* The full evidence is written as CSV beside the reports: the term rows, the term span, the pairs, the intruding terms and the quadrant. Use the values the tool reported and name a term as the tool spelled it.
* The module names of the overview are the keys of the contexts. Do not rename them.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `ContextMap` response schema.
* Each `contexts[]` entry MUST be a JSON object with fields: `name`, `modules`, `language` and `reasoning`. Do NOT use plain strings.
* Each `relations[]` entry MUST be a JSON object with fields: `fromContext`, `toContext`, `relation`, `evidence`, `sharedLanguage` and `structurallyCoupled`.
* Each `unplacedModules[]` entry MUST be a JSON object with the fields `module` and `reason`.
* Report at most 12 contexts and at most 20 relations. A longer response is cut off before its JSON is complete, so it is rejected and retried.
