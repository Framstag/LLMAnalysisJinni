# Tasks

## 1. The fold

- [x] 1.1 Fold the seam candidates by interface name, keeping the appearance that ranks highest as the primary module
      and carrying the other modules beside it; verify with a unit test that an interface in three module reports holds
      one candidate with three modules, and that the rank after it is not pushed down
- [x] 1.2 Fold the entry point candidates by class name the same way; verify with a unit test that a class in three
      module reports holds one candidate naming three modules
- [x] 1.3 Carry the other modules of a candidate in its result entry and name them in its evidence, so a reader sees
      that the element appears in more than one module; verify with a unit test that the result entry and the evidence
      both name them

## 2. The order of the entry points

- [x] 2.1 Rank the entry point candidates of production code above the ones of test or generated code, before the
      evidence of the class is compared; verify with a unit test that a production class with a main method ranks
      before a benchmark harness with a main method and more annotations
- [x] 2.2 Keep a test or generated entry point in the list, below the production ones, with evidence that states its
      code category; verify with a unit test that a test entry point stays in the result and that its evidence names
      its category

## 3. The prompts and the report

- [x] 3.1 Name the modules a candidate was seen in, in the anchor prompt and in the strategy prompt, so that a
      judgement rests on the element and not on one module view of it; verify with `ModernisationAnchorAnalysisTest`
      and `ModernisationStrategyAnalysisTest`

## 4. The run

- [x] 4.1 Re-run the anchor and the strategy analyses of `workspaces/maven` and confirm in the rendered document that
      no element holds more than one rank and that the entry point list is led by production code; verify with the
      document

      Confirmed: the anchor chapter holds 116 rows and no pair of anchor type and element appears twice within a type
      (the same element under two different anchor types is a different anchor and stays). The entry point list is led
      by `Maven3ScopeManagerConfiguration`, `Maven4ScopeManagerConfiguration`, `MavenCli` and `ComparableVersion`
      instead of the JMH benchmark harnesses it held at ranks 1 to 6. The seam rows name the module a candidate was
      also seen in, for example `compat`. Both tasks completed: anchors in 1m05s, strategy in 1m31s, 112.3K input and
      36.7K output tokens together.

## 5. Integration checks

- [x] 5.1 Run `mvn verify` and confirm the build passes; verify with the build
- [x] 5.2 Run the specification validation in the strict mode; verify with `openspec validate --all --strict`

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
