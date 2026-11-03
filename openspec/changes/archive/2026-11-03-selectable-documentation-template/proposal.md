# Select the document template by name

## Why

The pipeline's consumer (the cloud-sovereignty-analysis repository, change `configurable-output-language`, archived 2026-11-03) needed the final document in German. For that, the engine had to be able to render a named template instead of the hardcoded `Documentation.adoc`. The engine half of that feature — the optional `-t`/`--template` option of the `document` command — has shipped (commit d356800) and was verified in a full pipeline run, but it was not specified in this repository. This change backfills the specification.

## What Changes

- The `document` command accepts an optional template name (`-t`/`--template`), naming the `.hbs` template under the `documentation/` directory of the working directory to render, without the `.hbs` suffix.
- The option defaults to `Documentation.adoc`, the previously hardcoded template, so every existing invocation renders the same document at the same output file as before.
- This is a retroactive backfill: the implementation exists (commit d356800) and the tasks are done; the change records the behaviour in the specification.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-template`: the document command renders a template selected by name in addition to the default template, and keeps the default when the option is omitted.

## Impact

- `DocumentCmd` of the `document` subcommand: one new optional CLI option, passed to the template compilation. Implemented in commit d356800.
- Nothing changes in template discovery (the `FileTemplateLoader` over the working directory's `documentation/` directory), state loading, output file naming, or any other command.
- Verified end-to-end by the consumer's pipeline run, which rendered `Documentation_German.adoc` from the German template.
