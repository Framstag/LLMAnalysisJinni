# Proposal

## Why

The generated `Documentation.adoc` carries 172 `[plantuml]` blocks in the `maven` workspace, and a reader who opens it in the VS Code AsciiDoc extension sees code blocks instead of pictures: Kroki rendering is off by default there, and the extension sends nothing until it is explicitly enabled. Nothing is broken in the file — the block style is only an instruction to a renderer that the reader has to provide — but the project documents neither which preview toolchains draw the blocks nor how to switch one on. The decision that chose PlantUML was taken against the IntelliJ preview, so a VS Code reader has no recorded path to the same result.

## What Changes

- Add project documentation stating which reader toolchains render the emitted diagram blocks and which show them as literal code (plain Asciidoctor, Asciidoctor.js, Pandoc).
- Add the concrete steps to enable rendering in the supported preview toolchains: the VS Code AsciiDoc extension settings, the reload the change requires, and the local Kroki/PlantUML server option for readers who do not want diagram source sent to a public service.
- State the cost of the documented path where it matters: a document with 172 diagrams issues one render request per diagram, and the public Kroki instance throttles.
- Record that the engine stays renderer-free: it emits diagram source, never an image, and takes no dependency on a rendering binary.

No engine behaviour changes. The generated Asciidoc itself, the emitted PlantUML, and the diagram tool are untouched.

## Capabilities

### New Capabilities

- `documentation-preview-toolchain`: the contract between the emitted Asciidoc diagram blocks and the reader's preview toolchain — that the blocks stay renderer-agnostic, and that the project documentation names the toolchains that render them, how to enable them, and the privacy and throughput limits of the documented path.

### Modified Capabilities

None. `dependency-diagram` describes what the engine emits and that requirement is unchanged; this change only states what the reader needs in order to see it drawn.

## Impact

- `README.md` — the `document` command section gains the preview guidance.
- `analysis/software-architecture/README.md` — the diagram task row points at that guidance.
- No source, no template, no schema, no task YAML, no build or dependency change.
- The archived design of `add-intra-module-dependency-analysis` (D9) selected PlantUML for the IntelliJ preview; it is archived history and stays as it is. This change carries the reader requirements forward to the toolchain actually in use.
