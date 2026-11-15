# Tasks

## 1. The option

- [x] 1.1 Add `-t`/`--template` to `DocumentCmd` with the default `Documentation.adoc`, and compile the option value
      instead of the hardcoded name.
- [x] 1.2 Verify with `DocumentCmdTest` that the default value is the previously hardcoded template and that both the
      short and the long option name set the value.

## 2. The rendered document

- [x] 2.1 Verify with `DocumentCmdTest` that an invocation without the option renders `Documentation.adoc.hbs` and one
      with `--template Documentation_German.adoc` renders `Documentation_German.adoc.hbs`, both against the same
      analysis state.
- [x] 2.2 Verify that the template name does not become the output file name: the document is written to
      `Documentation.adoc`, and `--document-postfix .txt` still writes `Documentation.txt`.
- [x] 2.3 Verify that a name that matches no template fails and writes no document.

## 3. Documentation

- [x] 3.1 Document the option, its default, the directory it is resolved in and the unchanged output name in
      `README.md`, and name the option in the `document` section of `AGENTS.md`.

## 4. Verification

- [x] 4.1 Run `openspec validate --all --strict` and confirm the change and the specifications pass.
- [x] 4.2 Run `mvn verify` and confirm the build gate passes, including the specification validation and the artefact
      smoke test.
- [ ] 4.3 Run the consumer pipeline with the German template and confirm the rendered document is the translated one.
      Manual check in the consumer repository, not reproducible in this repository.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied, and sync the two modified specifications
  with the archived requirements.