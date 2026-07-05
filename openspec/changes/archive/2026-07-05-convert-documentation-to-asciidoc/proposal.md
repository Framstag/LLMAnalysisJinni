## What Changes

Switch the documentation generation pipeline from Markdown to Asciidoc. The Handlebars template (`Documentation.md.hbs`) is rewritten as `Documentation.adoc.hbs` using Asciidoc syntax. The `document` CLI command defaults to `.adoc` output postfix and compiles the Asciidoc template. The test is updated to verify Asciidoc output.

Markdown has corner-case problems with complex tables, nested formatting, and code blocks inside list items. Asciidoc handles these robustly and provides better cross-referencing, include directives, and PDF export via Asciidoctor.

## Capabilities

### New Capabilities

- `asciidoc-documentation`: Documentation generation produces Asciidoc (`.adoc`) output instead of Markdown (`.md`). The template uses Asciidoc table syntax, headings, code blocks, and conditional sections.

### Modified Capabilities

- *(none — this is a format switch, no spec-level behavior changes)*

## Impact

- **`DocumentCmd.java`**: Change default `--document-postfix` from `".md"` to `".adoc"`. Change compiled template name from `"Documentation.md"` to `"Documentation.adoc"`.
- **`Documentation.md.hbs`** → **`Documentation.adoc.hbs`**: Rewrite all Markdown syntax to Asciidoc. Tables use `|===` delimiters, headings use `= ` / `== ` / `=== `, code blocks use `----`, inline code uses backticks, blockquotes use `____`.
- **`DocumentationTemplateTest.java`**: Update assertions to check Asciidoc output (e.g., `|===` table delimiters, `===` headings).
- **`analysis/software-architecture/documentation/`**: Rename template file from `Documentation.md.hbs` to `Documentation.adoc.hbs`.
