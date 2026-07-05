## Context

Documentation generation uses a single Handlebars template `Documentation.md.hbs` in `analysis/software-architecture/documentation/`. The `DocumentCmd.java` compiles it with `.hbs` extension and writes output as `Documentation.md` (default `--document-postfix=.md`).

The template uses Markdown: `#`/`##`/`###` headings, `|` pipe tables, `` ` `` inline code, `    ` indented code blocks, `>>` blockquotes, `[TOC]` table of contents.

No external dependencies on Markdown rendering — output is plain text. Same applies for Asciidoc: no render-time deps needed.

## Goals / Non-Goals

**Goals:**
- Template uses Asciidoc syntax instead of Markdown
- `document` CLI command outputs `.adoc` by default
- All existing sections render identically in structure
- Test verifies Asciidoc output

**Non-Goals:**
- No Asciidoctor/PDF pipeline integration (future concern)
- No changes to analysis pipeline tasks or JSON schemas
- No changes to other analysis domains

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Template file name | `Documentation.adoc.hbs` | Keeps `.hbs` extension for Handlebars loader, `.adoc` signals format |
| Default output postfix | `.adoc` | Matches template format |
| Table syntax | Asciidoc `|===` delimited tables | Handles complex cell content better than pipe tables |
| Headings | `= ` (level 0), `== ` (level 1), `=== ` (level 2), `==== ` (level 3) | Asciidoc standard |
| Code blocks | `----` fenced blocks | Replaces indented code blocks |
| Inline code | `` `backticks` `` | Same as Markdown, valid in Asciidoc |
| Blockquotes | `____` | Replaces `>>` |
| TOC | `:toc: left` document header | Replaces `[TOC]` |
| No structural changes | Keep same section hierarchy | Minimizes diff, preserves readability |

## Risks / Trade-offs

- **Risk: Handlebars `{{...}}` syntax clashes with Asciidoc attribute references** → Mitigation: Asciidoc uses `{name}` for attributes, Handlebars uses `{{name}}` — no overlap since Handlebars uses double braces. Single-brace `{set:name}` Asciidoc constructs are rare and not used in this template.
- **Risk: Table cell content with pipes** → Mitigation: Asciidoc `|===` tables don't use pipes as delimiters inside cells, so content like `|aspect|` renders fine.
- **Risk: Existing `.md` consumers break** → Mitigation: Output postfix is configurable via `--document-postfix`. Users can still get `.md` output if needed (though template is Asciidoc).
