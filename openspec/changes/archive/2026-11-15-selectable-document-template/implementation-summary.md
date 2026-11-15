# Implementation Summary — selectable-document-template

## What was built

`DocumentCmd` compiles `-t`/`--template` instead of the hardcoded `Documentation.adoc`. The loader, the analysis state
that is rendered and the `Documentation<postfix>` output name are unchanged.

## Checks

- `openspec validate --all --strict`: 64 passed, 0 failed, with the change validated as an active change before it was
  archived (an archived change is not read by the validator).
- `mvn -B verify`: 810 unit tests, `JarSmokeIT` 6/6, and the SBOM checks pass.
- `DocumentCmdTest` covers the default value, both option names, the rendered default template, the rendered named
  template, the unchanged output file name, the postfix that still applies, and the failure of a name that matches no
  template.

## Note on the first verification run

The first `mvn -B verify` run failed in `FileToolTest.GetAllFilesInDirWithSubdir`: the test pins the recursive listing
of `src/test/java/com/framstag/llmaj`, so the new `DocumentCmdTest.java` had to be added to its expectation. The failure
was caused by the new test file, not by the command change.

## Open

Task 4.3, the consumer pipeline run that renders the German template, is a manual check in the consumer repository and
stays open in `tasks.md`.