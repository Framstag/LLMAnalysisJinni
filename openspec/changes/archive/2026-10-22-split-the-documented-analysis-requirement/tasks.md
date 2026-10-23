# Tasks

## 1. The requirement

- [x] 1.1 Move the enumeration of the documented analyses out of the text of the requirement "One entry per documented
      analysis" and into a scenario of it, keeping the requirement name, its behaviour and all its other scenarios;
      verify with `openspec validate analysis-reference --type spec --strict` that the requirement holds no warning
- [x] 1.2 Confirm that the reference document, the report and the test that checks them are unchanged, and that the
      documented set still names every analysis the report renders; verify with
      `mvn -q test -Dtest=AnalysisReferenceTest` and with the task table of the README of the analysis

## 2. Integration checks

- [x] 2.1 Run `mvn verify` and confirm the build passes; verify with the build
- [x] 2.2 Run the specification validation of the project in the strict mode and confirm that every specification
      passes; verify with `openspec validate --all --strict`

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
