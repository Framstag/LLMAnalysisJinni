# Tasks

## 1. The solver configuration

- [x] 1.1 Give the source type solver of the report collection a parser configuration of its own with the comment
      attribution switched off, while the parse of the module keeps the configuration it uses today; verify with a
      unit test that the solver is constructed with a configuration whose comment attribution is off and that the
      module parse still attributes comments
- [x] 1.2 Confirm that the report of a module is unchanged: the same classes, methods, references, annotations and
      documentation counts as before the change; verify with `RawModuleReportFormatTest`,
      `ExistingMetricReportCompatibilityTest` and the metric tests of the engine

## 2. The evidence

- [x] 2.1 Re-collect the reports of the workspace `workspaces/maven`, whose collection did not complete before this
      change, and confirm that it completes and writes the reports; verify with the report files of that workspace
      carrying the current format version
- [x] 2.2 Record in the change what the collection of the largest module cost before and after the change, so that
      the fix rests on a measurement and not on a suspicion; verify that the change names both figures

      Measured on `workspaces/maven` (82 Java modules):

      - Before: the collection wrote 13 of 82 reports in the first four minutes and then stopped producing a report
        at all; a thread dump after twenty minutes of no progress showed the solver inside
        `JavaParserTypeSolver.parseDirectory` -> `JavaParser.parse` -> `CommentsInserter.insertComments`.
      - After: the same workspace collected all 82 reports, and the whole run including its two model calls took 53
        seconds (06:20:38 to 06:21:31 in the run log).

## 3. Integration checks

- [x] 3.1 Run `mvn verify` and confirm the build passes; verify with the build
- [x] 3.2 Run the specification validation in the strict mode; verify with `openspec validate --all --strict`

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
