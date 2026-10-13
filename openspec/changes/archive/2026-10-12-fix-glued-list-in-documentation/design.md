# Design

## Why the blank line disappeared

Handlebars trims whitespace around a tag on the side the tilde sits on. `{{~#each notDrawn}}` trims the
whitespace *before* the tag, which in this template is the blank line the paragraph needs:

```
Not drawn:\n\n{{~#each notDrawn}}\n* {{this}}\n{{~/each}}
```

The trailing `~` on `{{~/each}}` trims the newline after the last bullet, which is why the original author used
the pattern: the block was written to produce tight output and the blank line went with it. The result is
`Not drawn:\n* item\n* item`, which AsciiDoc reads as a paragraph whose text carries asterisks.

## Decision: write the bullet on the line of the block tag

```
{{#if notDrawn}}
Not drawn:

{{#each notDrawn}}* {{this}}
{{/each}}
{{/if}}
```

The body of the block is then exactly `* {{this}}\n`, so the blank line of the template survives, every item
gets its own line, and no item is separated from the next by a blank line. Nothing is trimmed, so the rendering
no longer depends on a subtle interaction between two tildes.

The alternative — keeping the tildes and inserting an explicit blank line inside the block — works, but it
leaves the same trap for the next edit and it puts a blank line between every pair of items.

## Decision: assert the invariant, not only this list

`DocumentationTemplateTest` now renders the template with a module whose diagram budget is used up and requires
the blank line and one bullet per omission. A second test asserts the general rule over the fixture document:
every line that starts a bullet is preceded by a blank line, another bullet, an attribute line, or an indented
continuation.

The second test would have caught this defect in any list of the template, which is the point: the trim was
correct-looking code, and only the rendered shape shows what it did. The check is skipped for an indented
preceding line, because that is the continuation of the item above it and a new item may follow it directly.

## Out of scope

Bullets inside the evaluation text of a module come from the model, not from the template. Where such text
opens a level two bullet next to a level one bullet the document renders a nested list, which is what the text
asks for. The template invariant only covers what the template emits.
