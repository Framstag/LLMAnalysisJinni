# Keep the list of diagrams that were not drawn a list

## Why

The dependency diagram section of the report lists the diagrams it did not draw, one bullet per omission. The
template trimmed the block away from the paragraph above it:

```
Not drawn:

{{~#each notDrawn}}
* {{this}}
{{~/each}}
```

The trim on the opening tag removes the blank line, so the rendered document holds the paragraph directly
followed by the bullets:

```
Not drawn:
* No class detail for group 13 of 75 (2 class(es), ...): the budget of 12 class detail diagrams is used up.
* No class detail for group 14 of 75 (2 class(es), ...): the budget of 12 class detail diagrams is used up.
```

AsciiDoc needs a blank line before a list. Without it the markers are part of the paragraph, so the reader of
the report gets one run-on line with literal asterisks instead of the list of everything the diagram budget
left out:

```
Not drawn: * No class detail for group 13 of 75 ... used up. * No class detail for group 14 of 75 ... used up.
```

The list is the only place the report admits what it did not draw, so the one section whose purpose is
disclosure was the one that did not render.

## What changes

- The list is emitted with its blank line, one bullet per line, and the trimming is gone.
- A test renders the template with a module whose diagram budget is used up and requires the blank line, the
  bullet per omission, and that no bullet anywhere in the rendered report is glued to a paragraph.

## Impact

- One template block and one test in `DocumentationTemplateTest`.
- The Maven report of the workspace lists 33 omissions that until now collapsed into one paragraph, and the
  same section of every other report regains its shape on the next `document` run.
- No model involvement and no state change: the analysis state already held the list, only its rendering was
  wrong.
