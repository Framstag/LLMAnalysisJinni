# Design

## Context

See `proposal.md` — Why. The current state that shapes the approach:

- `DocumentCmd` builds a `FileTemplateLoader` over `config.getAnalysisDirectory().resolve("documentation")` with the
  `.hbs` suffix and compiles the literal name `Documentation.adoc`. Every template name therefore ends with `.hbs` on
  disk, while the compiled name carries no suffix.
- `Config.analysisDirectory` is the path that `workspace init --analysis` stored, as it was given. It is resolved
  against the process working directory, not against the workspace, so the template directory belongs to the analysis
  directory of the project (for example `analysis/software-architecture/documentation`) and not to the workspace.
- The output file is `workingDirectory.resolve("Documentation%s".formatted(documentPostfix))`. The postfix is
  configurable since the `.adoc` default was introduced; the stem `Documentation` is not.
- A consumer pipeline supplies the translation as `Documentation_German.adoc.hbs` next to `Documentation.adoc.hbs`, and
  keeps prompt-side language instructions in its own change. The engine only has to render the file.

## Goals / Non-Goals

**Goals:**

- A named template, compiled by the existing loader, with the previous behaviour as the default.
- One option, one code path: the document command keeps exactly one template per run.

**Non-Goals:**

- Per-section templates, a template directory outside the analysis directory, or a template listing command.
- Changing the output file name, the state that is rendered, or the template discovery.

## Decisions

### D1. The option value is the template name without the `.hbs` suffix

The loader appends its suffix at compile time, and the consumer already writes the name that way
(`Documentation_German.adoc`). Accepting the suffix as well would mean stripping it first, so that a name that is
wrong for the loader and a name that is wrong for the directory would both have to be diagnosed.

### D2. The default is the name that was hardcoded

`defaultValue = "Documentation.adoc"` keeps the default invocation bit-identical to the previous build, and it keeps
the option's help text honest about what happens when it is omitted. A default computed from the postfix would couple
two independent options.

### D3. No existence precheck, and no validation of the name

The value is developer-supplied, not user-supplied: it comes from a pipeline configuration, not from the analysed
project. An unknown name therefore fails with the template loader's own error, before anything is written, and that is
reported as the command's failure. A dedicated message would duplicate a diagnostic that the loader already produces,
and a check that the name contains no path separator would suggest a security boundary that this option does not have
— the caller can already name any file it wants through the analysis directory. If the option ever becomes user-facing,
the precheck has to come with it.

Accepted consequence: a name given with the suffix (`X.adoc.hbs`) fails as `X.adoc.hbs.hbs`, and a name with a path
separator fails as a lookup outside the template root. Both messages name the template the loader tried to load.

### D4. `-t` keeps the short name the consumer examples use

`workspace init` uses `-t` for `--modelProvider`. The two options live in different subcommands, so picocli cannot
confuse them, and no invocation carries both. Consistency across subcommands is worth less here than matching the
documented consumer invocation.

## Consequences

- Every `.hbs` file under the analysis directory's `documentation/` directory is renderable, so a language variant needs
  no further engine change.
- A run with an alternate template overwrites the document of the default template in the same workspace, because the
  output name does not follow the template. The consumer that keeps both documents has to copy or rename between runs;
  that is recorded in the requirement rather than solved here.
- The `documentation-coverage` requirement is unaffected: it constrains the template's content, not the template's name.