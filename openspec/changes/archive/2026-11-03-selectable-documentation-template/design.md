# Design: Select the document template by name

Retroactive design record for an engine change that already shipped (commit d356800). It is short because the change is a single, backward-compatible CLI option.

## Context

The consumer pipeline's change `configurable-output-language` produces the document in German in two halves: prompt instructions that make the model write its free text in German, and a separate German template, `Documentation_German.adoc.hbs`, under the workspace's `documentation/` directory. The engine has always resolved templates through a `FileTemplateLoader` over that directory with a `.hbs` suffix. What was missing was a way for the caller to choose the template name.

## Decisions

- **The option is the template name without the `.hbs` suffix** (`Documentation.adoc`, `Documentation_German.adoc`): the loader appends its suffix at compile time, so the loader configuration and the template files are untouched, and the value reads the same way the consumer already writes it.
- **The default is `Documentation.adoc`**: exactly the template name that was hardcoded before, so an invocation without the flag renders bit-identical output.
- **No existence precheck**: an unknown name fails with the template loader's compile error. The name is developer-supplied, not user-supplied, and a dedicated diagnostic is not worth the code.
- **One name per invocation**: per-section template selection is out of scope; a document is rendered by exactly one template.

## Consequences

- Any `.hbs` file under the workspace's `documentation/` directory becomes renderable, so a future language variant needs no further engine change.
- The consumer-side contract is the `selectable-document-template` capability in the cloud-sovereignty-analysis store; the requirement added here is the engine-side half of it.
