# Tasks

## 1. Preview guidance in the root README

- [x] 1.1 Add a "Previewing the generated documentation" subsection to the `document` command section of `README.md` that names the toolchains which render the emitted diagram blocks (VS Code AsciiDoc extension; IntelliJ AsciiDoc plugin with a diagram renderer) and those that show them as literal code (plain Asciidoctor, Asciidoctor.js without a diagram extension, Pandoc). Verify: every toolchain named in this task appears in the section, and a reader can tell from it that a literal block is expected behaviour rather than a broken document.
- [x] 1.2 Add the VS Code enablement steps to that subsection, naming the current setting `asciidoc.extensions.enableKroki` first and the older `asciidoc.use_kroki`, the server-URL setting `asciidoc.extensions.kroki.serverUrl`, and the editor reload the change requires. Verify: the setting ids are spelled exactly as published by the extension, and both keys are present.
- [x] 1.3 Add the local-server option to that subsection: `:kroki-server-url:` in the document header or an `.asciidoctorconfig` at the reader's side, plus a local Kroki instance. Verify: the section states that the source goes to the configured server, that the public service is the default, and that a local server avoids it.
- [x] 1.4 Add the throughput note to that subsection: one rendering request per diagram and no availability guarantee on the shared public instance, with `workspaces/maven/Documentation.adoc` (172 diagrams) as the case that shows it. Verify: the per-diagram request cost and the local-server recommendation are stated in the same subsection.
- [x] 1.5 Verify group 1 by following the section as written: open `workspaces/maven/Documentation.adoc` in the VS Code AsciiDoc extension with diagram rendering off and confirm the diagram blocks appear as literal source while the rest of the document renders, then enable rendering against a local server, reload, and confirm the diagrams are drawn. Record in the change how many diagrams rendered and whether any request was throttled. Result, reported by the operator on 2026-10-07: the preview works, all 172 diagrams of `workspaces/maven/Documentation.adoc` rendered in the VS Code AsciiDoc extension against the default public `kroki.io` server, and no request was throttled. The check ran against the public server rather than a local one, so the local-server instructions are documented but were not exercised by this verification.

## 2. Pointer from the analysis documentation

- [x] 2.1 Add a pointer from the diagram task row(s) in `analysis/software-architecture/README.md` to the preview subsection in the root README. Verify: the link resolves to the subsection in `README.md` and the row still matches its task in `tasks.yaml`.

## 3. Integration checks

- [x] 3.1 Run `mvn verify` and confirm it still passes, so the docs-only change is shown not to touch the build or the tests. Verify: the build is green.
- [x] 3.2 Run `openspec validate "document-diagram-preview-toolchain" --strict` and confirm the change validates. Verify: no validation warnings or errors.

## Workflow follow-up

- Archive the change once the documentation edits are merged and the preview check in 1.5 has been recorded.
