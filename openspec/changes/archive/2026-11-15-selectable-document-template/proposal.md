# Proposal

## Why

The engine renders the final document from one hardcoded template, `Documentation.adoc.hbs`. A consumer that needs the
document in another language supplies that language in two halves: prompt instructions that make the model write its
free text in the language, and a translated template placed beside the English one. The prompts need no engine change.
The template half does: without a way to name the template, a translated template under the analysis directory's
`documentation/` directory is unrenderable, and the consumer would have to rename files or patch the engine.

The gap is small and the fix is smaller than any workaround: the template loader already resolves every name with its
`.hbs` suffix, so only the name that is compiled is fixed in the code.

## What Changes

- The `document` command accepts an optional template name (`-t`/`--template`), given without the `.hbs` suffix, naming
  the template that is compiled under the `documentation/` directory of the analysis directory configured in the
  workspace's `config.json`.
- The default is `Documentation.adoc`, exactly the name that was hardcoded, so an invocation without the option renders
  the same document as before.
- The template name does not become the output file name: the document is still written to `Documentation<postfix>` in
  the workspace, so a run with an alternate template overwrites the document of the default template.
- Explicit non-goals: no per-section template selection (a document is rendered by exactly one template), no second
  template search directory, no existence precheck of the name, and no change to template discovery, state loading or
  output file naming.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-template`: the document command renders the template it is given by name, and keeps `Documentation.adoc`
  as the default, so a template the analysis directory holds becomes renderable without an engine change.
- `asciidoc-documentation`: the command compiles the selected template instead of a fixed one; `Documentation.adoc.hbs`
  stays the default, so the requirement about the Asciidoc template describes the default run instead of the only
  possible one.

## Impact

- `src/main/java/com/framstag/llmaj/cli/DocumentCmd.java`: one option, passed to the template compilation instead of the
  hardcoded name.
- `src/test/java/com/framstag/llmaj/cli/DocumentCmdTest.java`: the default, the named template, the unchanged output
  file name, the postfix that still applies and the failure of a name that matches no template.
- `README.md` and `AGENTS.md`: the option, its default, the directory it is resolved in and the unchanged output name.
- Untouched: the `FileTemplateLoader` over the analysis directory's `documentation/` directory, the analysis state that
  is rendered, the output directory of the document, and every other command.