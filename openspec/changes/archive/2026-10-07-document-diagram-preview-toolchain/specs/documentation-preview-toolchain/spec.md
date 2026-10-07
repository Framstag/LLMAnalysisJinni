# Spec Delta

## Purpose

Defines the contract between the Asciidoc diagram blocks the engine emits and the preview toolchain a reader has to supply, and requires the project documentation to name the toolchains that render them, how to enable them, and the limits of the documented path.

## ADDED Requirements

### Requirement: Diagram output stays renderer-agnostic source

The engine SHALL emit diagram content as Asciidoc diagram blocks carrying diagram source, never as a rendered image, and SHALL not depend on a rendering binary. Drawing the diagram is the reader's toolchain.

#### Scenario: A reader without a diagram renderer still sees the source

- **WHEN** the generated documentation is opened in a preview that does not render diagram blocks
- **THEN** the diagram source is shown as a literal block and the rest of the document renders normally

#### Scenario: No image artefact is produced

- **WHEN** the `document` command runs
- **THEN** it writes the documentation file only, and no image file for a diagram

#### Scenario: Rendering a diagram is not part of the run

- **WHEN** a run emits a diagram without a rendering binary installed
- **THEN** the run succeeds and the diagram source is complete in the generated documentation

### Requirement: Renderable reader toolchains are documented

The project documentation SHALL name the reader toolchains that draw the emitted diagram blocks and those that show them as literal code, so a reader can tell a missing renderer from a broken document.

#### Scenario: Renderable toolchains are listed

- **WHEN** the documentation section on previewing the generated document is read
- **THEN** it names the toolchains that render the diagram blocks, including the VS Code AsciiDoc extension and the IntelliJ AsciiDoc plugin with a diagram renderer

#### Scenario: Non-rendering toolchains are listed

- **WHEN** the documentation section on previewing the generated document is read
- **THEN** it states that plain Asciidoctor, Asciidoctor.js without a diagram extension, and Pandoc show the diagram blocks as literal code rather than failing

### Requirement: Enabling a renderer is documented with exact settings

The project documentation SHALL state how to enable diagram rendering in the named toolchains, naming the settings that switch it on and the local server option, and SHALL state any restart the change requires.

#### Scenario: VS Code settings are named

- **WHEN** the documented instructions for the VS Code AsciiDoc extension are read
- **THEN** they name the setting that enables diagram rendering and the setting for the server URL

#### Scenario: A local server is documented

- **WHEN** the documented instructions are read
- **THEN** they state how to point rendering at a local server instead of the public diagram service

#### Scenario: Restart requirement is stated

- **WHEN** the documented instructions are read
- **THEN** they state that the preview picks up a changed diagram setting only after the editor is reloaded

### Requirement: Limits of the documented path are stated

The project documentation SHALL state that the documented rendering path sends diagram source to the configured server and issues one rendering request per diagram, and SHALL state that the public instance is a shared service without an availability guarantee, so a reader can judge the privacy and the reliability cost of previewing a large report.

#### Scenario: Privacy of the public service is stated

- **WHEN** the documented instructions name the public diagram service as the default server
- **THEN** they state that the diagram source is sent to that server and that a local server avoids it

#### Scenario: Per-diagram request cost and public-instance reliability are stated

- **WHEN** the documentation describes previewing a report that contains many diagrams
- **THEN** it states that each diagram costs its own rendering request and that the public instance is shared and carries no availability guarantee
