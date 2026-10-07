# Design

## Context

See `proposal.md` - Why. Three facts shape the approach.

The template already emits a correct Asciidoc diagram block, and the escaping is already right: `HandlebarsFactory` uses `EscapingStrategy.NOOP`, so the PlantUML source reaches the file verbatim. `workspaces/maven/Documentation.adoc` holds 172 blocks, every one of them a well-formed `@startuml` / `@enduml` script. There is no defect to fix in the emitter.

What renders `[plantuml]` is entirely a property of the reader's preview:

```
[plantuml] ----> asciidoctor-diagram  -> needs PlantUML binary or server
           ----> asciidoctor-kroki    -> needs a Kroki server
           ----> nothing loaded       -> literal block, no error
```

The VS Code AsciiDoc extension renders through Kroki, and it ships with that off. The decision that picked PlantUML (`add-intra-module-dependency-analysis`, D9) was taken against the IntelliJ preview, where a diagram renderer is bundled. That archive is history; the reader in use is VS Code, and no document says what VS Code needs.

Constraint from the project: the engine stays free of a rendering binary — the shaded jar is not going to shell out to PlantUML or Graphviz, and the run must not depend on a network service.

## Goals / Non-Goals

**Goals:**

- A reader who opens `Documentation.adoc` in a preview can tell in one place whether an unrendered diagram is expected or a defect, and can switch rendering on if they want it.
- The documented instructions name the settings exactly, because a reader cannot guess them and the extension's default is off.
- The privacy and throughput cost of the documented path is visible before a reader uses it, not discovered afterwards.

**Non-Goals:**

- Changing the emitter, the template, the emitted PlantUML, the diagram tool, or any schema. The specs require that this stays renderer-agnostic; nothing here touches it.
- Writing a config file into a workspace so previews work without setup. See D1.
- Making the documentation render correctly in every Markdown or plain-Asciidoctor consumer. Asciidoc plus a diagram renderer is the supported path, and the guidance says what the others do.
- Building a renderer into the engine or into CI.

## Decisions

### D1 - Guidance is documentation, not an emitted editor config

Rejected: having `document` write an `.asciidoctorconfig` next to `Documentation.adoc`, or a `:kroki-server-url:` attribute into the document header, so a preview needs no manual step. It puts an editor's configuration into the reader's workspace, picks a server URL on the reader's behalf, and makes the generated document carry a setting that is not analysis output. It also has no natural value to choose: the public service is the wrong default for private source and there is no local server to assume exists.

The setting belongs to the reader's editor profile. If manual setup turns out to be real friction, emitting the config is a later change with its own requirements — this design keeps that door open by not inventing a config format now.

### D2 - The guidance lives in the root README, at the document command

The `document` command section already explains the output file and the `--document-postfix` option, so that is where a reader looks for how to read the result. A separate document would be a second place to keep in step. The analysis README only gains a pointer from the diagram task row, so the diagram reader finds it from the task that produces it.

Rejected: extending the archived design of `add-intra-module-dependency-analysis`. Archived changes are history and are not edited; the stale IntelliJ premise stays recorded there as the reason PlantUML was chosen.

### D3 - Kroki is the documented path for VS Code

The VS Code extension implements diagrams through asciidoctor-kroki. asciidoctor.js cannot execute the asciidoctor-diagram extension, so a PlantUML binary on the reader's machine does not help that preview at all. Documenting an `asciidoctor-diagram` setup would send a VS Code reader down a path that cannot work.

Alternative rejected: telling the reader to install a different editor extension. The supported extension is the one the project's reader uses, and Kroki is what it has.

### D4 - Both setting generations are named

The current extension enables diagrams with `asciidoc.extensions.enableKroki` and takes the server from `asciidoc.extensions.kroki.serverUrl`. Older builds used `asciidoc.use_kroki`. The reader's version is unknown when the sentence is written and a wrong key looks like a broken document, so the guidance names both and says the current one first.

### D5 - The local server is presented as an equal option, not a footnote

The default server is the public `kroki.io`, which receives the diagram source — for an analysis of a private codebase that is a real disclosure. The local option is therefore part of the instructions, not a caveat at the end: `:kroki-server-url:` in the document header or an `.asciidoctorconfig` at the reader's side, and a local Kroki instance. No URL is recommended as a project-wide default, because none exists.

### D6 - One setting change needs a reload, and that is stated

The extension reads the Kroki setting at startup, so a reader who enables it and refreshes the preview sees no change and concludes the guidance is wrong. The reload requirement is part of the instructions; this is the failure mode most likely to waste a reader's time.

### D7 - PlantUML stays

Worth recording because the toolchain question reopens it: Kroki draws Mermaid and Graphviz as well, so a VS Code-only reader would lose nothing by switching.

Staying is still right. The choice is isolated in one emitter, the 172 stored sources in existing workspaces are PlantUML, and a switch invalidates them and the archived decision without making any diagram visible that is not visible now - rendering depends on the reader's setting either way. The change would be a separate one with its own reason, not a side effect of documenting a preview.

### D8 - Verification is a manual preview check, not a test

The requirements are about documentation content a human reads. A unit test asserting that the README contains a given setting string would break on every legitimate rewording and would prove nothing about whether a reader can act on the paragraph.

Alternative rejected: a documentation test in the style of the template tests. Those assert on generated output from the engine; the README is not generated.

Consequence to accept: this change is not enforced by `mvn verify`. The tasks carry the preview checks so the verification is at least written down and repeatable.

## Risks / Trade-offs

- The extension renames the settings again and the guidance silently rots → the guidance names both generations and lives in one place; the spec requires the settings to be named, so a rename is a spec-visible change rather than an edit nobody notices.
- A reader previews the 172-diagram `maven` report against the public service and gets throttled or waits → the per-diagram request cost is stated with the recommendation to use a local server.
- A reader without a renderer reads the literal `@startuml` blocks as an engine defect → the non-rendering toolchains are listed explicitly, so a literal block is documented expected behaviour, not a fault to report.
- Documenting a renderer path invites the assumption that the project supports rendering → the specs keep the engine's side at renderer-agnostic emission and say so; no build, dependency, or runtime change is made here.
