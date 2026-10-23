# Tasks

## 1. The budget and the anchor candidate tool

- [x] 1.1 Add the workspace property `anchor.candidatesPerType` to `Config` with its seeded value 15, in the style of
      `godClass.rankingLimit`; verify with `ConfigOverridesTest` and `ConfigStorerTest` that the seeded value is
      written and read
- [x] 1.2 Add the tool `java_get_anchor_candidates` that aggregates the candidates of the six tool backed anchor
      types (entry-point, seam, build-unit, cut-point, test-coverage, technology-marker), ranks them per type and
      returns the strongest up to the budget with the total count of each type; verify with a unit test that a project
      with more candidates than the budget returns the strongest ones and states the total, and that a project with
      fewer returns all of them
- [x] 1.3 Let every candidate carry the evidence it rests on and the analysis that counted it, and let the tool read
      a module whose report is missing as skipped with the reason instead of failing; verify with a unit test that the
      candidate names its source analysis and that a skipped module is named
- [x] 1.4 Register the tool in `ToolFactory`; verify that it appears in the tool list of the engine

## 2. The hotspot folds the appearances

- [x] 2.1 Fold the hotspot ranking by qualified class name inside the tool, keeping the highest complexity of the
      appearances and the modules the class appears in; verify with a unit test that a class in three module reports
      holds one row with three modules and its highest complexity, and that the ranks after it are not pushed down
- [x] 2.2 Let the hotspot report and the hotspot schema carry the modules of a class and its highest complexity;
      verify with a unit test that the CSV report holds one row per class and names its modules, and that a rendered
      sample answer validates against the schema

## 3. The prompt and the report

- [x] 3.1 Let the anchor prompt read the bounded candidate payload and the context map result instead of calling the
      eight project wide tools, and let its task whitelist hold only the new tool; verify with a test that the prompt
      calls only `java_get_anchor_candidates`, that it holds no other tool name, and that the task whitelist matches
      the tools the prompt calls
- [x] 3.2 Name the budget in the reference entry of the modernisation anchors with its seeded value; verify with
      `AnalysisReferenceTest` that every seeded value named in the reference is the seeded value of the engine
- [x] 3.3 Render a hotspot row with its modules; verify with `DocumentationTemplateTest` that the rendered table names
      the modules of a class
- [x] 3.4 Move the strategy prompt to the folded hotspot shape, so that it names the modules of a hotspot class instead
      of a single module; verify with `ModernisationStrategyAnalysisTest` that the prompt names no single module field of
      the hotspot result

## 4. The run that was blocked

- [x] 4.1a Rewrite the input section of the strategy prompt so that it calls only `java_get_anchor_candidates` once
      and reads the bounded payload as the counted evidence of the cost, and let the whitelist of the task
      `ModernisationStrategy` hold only that tool; verify with `ModernisationStrategyAnalysisTest` that the prompt
      calls no other tool and that the whitelist matches
- [x] 4.1b Hold the strategy prompt to the new tool set in `ModernisationStrategyAnalysisTest`: the prompt calls only
      `java_get_anchor_candidates`, the whitelist holds exactly that tool, and none of the three project wide evidence
      tools appears in the prompt; verify with `mvn -q test -Dtest=ModernisationStrategyAnalysisTest,ModernisationAnchorAnalysisTest`
- [x] 4.1c Note what the re-run of `workspaces/maven` measured: the anchor analysis completed with 30.5K input tokens
      and 56 anchors after the bound, where the same task was rejected at 1.4M input tokens before it, and the strategy
      analysis then exposed the same defect one level up, which 4.1a fixes
- [x] 4.1 Re-run the two blocked tasks of `workspaces/maven` and confirm that the anchor analysis completes and that
      the strategy analysis follows it; verify with the task states of that workspace and with the size of the anchor
      prompt in the run

      Measured: `ModernisationAnchors` completed with 30.5K input tokens and 56 anchors, where it was rejected at
      1,416,326 tokens before. `ModernisationStrategy` then failed with `prompt is too large to process`, because it
      called the three project wide evidence tools itself - the same defect one level up. After 4.1a it completed in
      25.2 seconds with an 80 KB prompt.
- [x] 4.2 Confirm in the rendered document of `workspaces/maven` that the anchor chapter holds anchors of every type
      the project supports, and that the hotspot chapter holds each class once; verify with the document

      Confirmed: the anchor chapter holds the types entry-point, seam, domain, build-unit, cut-point, test-coverage
      and reports technology-marker as partly present (no SBOM loaded), and the hotspot chapter now holds one row per
      class naming the modules it appears in, for example `Apache_Maven, impl, maven-impl`.

## 5. Integration checks

- [x] 5.1 Run `mvn verify` and confirm the build passes; verify with the build
- [x] 5.2 Run the specification validation in the strict mode; verify with `openspec validate --all --strict`

## 6. The runaway text in a response field

- [x] 6.1 Bound the free-text fields of the response contracts by length, so that a model cannot put a text of
      megabytes into a field that the next analysis renders: `modules` of a hotspot entry, the evidence items and the
      reasoning of an anchor, and the evidence, the cost and the reasoning of a strategy area; verify with
      `AnalysisResponseSchemaTest` and the schema tests of the three analyses
- [x] 6.2 State in the hotspot prompt that the `modules` field holds module names only, never a sentence and never a
      tool output; verify with `HotspotEvaluationAnalysisTest`

      Found by measurement: the hotspot answer carried 2,068,038 characters of prose inside the `modules` field of six
      rows, and the strategy prompt rendered it verbatim, which made the request 20.7 MB. After the bound the strategy
      prompt is 80 KB.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
