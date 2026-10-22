# Tasks

## 1. Correct the Purpose sections a delta cannot reach

- [x] 1.1 Correct the Purpose of `openspec/specs/documentation-coverage/spec.md`, which names the deleted template file `Documentation.md.hbs`, so that it names `Documentation.adoc.hbs`; verified: the spec names `Documentation.adoc.hbs`, that file exists in `analysis/software-architecture/documentation/`, and the spec no longer names `Documentation.md.hbs`
- [x] 1.2 Correct the Purpose of `openspec/specs/documentation-template/spec.md`, which names the output `Documentation.md` and calls it a markdown report, so that it names the AsciiDoc output and the AsciiDoc report; verified: the spec names `Documentation.adoc` and the AsciiDoc report, and holds neither `Documentation.md` nor `markdown`
- [x] 1.3 Verify that neither corrected Purpose is touched by the archive; verified by reading both files after the archive: `documentation-coverage` still names `Documentation.adoc.hbs` and `documentation-template` still names `Documentation.adoc` and the AsciiDoc report

## 2. Carry the corrections through the deltas

- [x] 2.1 Verify the delta of the new capability `enforce-evaluation-prompt-format`: it names the template file `Documentation.adoc.hbs` in no requirement, holds the requirement `Prompt enforces object format for evaluations` with its two scenarios, and its scenario is named `Every evaluation prompt states the format` instead of naming a count of prompt files; verified by reading the delta and the main spec it created
- [x] 2.2 Verify the delta of `documentation-template`: its requirement holds all three of its scenarios, and the path of the template is folded into the existing scenario `Template has no conditional fallbacks`; verified by reading the delta and comparing the scenario names with those of the main spec: `Template renders full table row from object`, `Template has no conditional fallbacks`, `Malformed data produces empty cells`
- [x] 2.3 Verify the delta of `analysis-reference`: the note about how the delta was written is gone and all five of its scenarios are present; verified by reading the delta and comparing the scenario names with those of the main spec, which holds five
- [x] 2.4 Verify the delta of the retired capability `enforce-evaluation-object-format`: it removes both of its requirements, each with a reason and a migration; verified with `openspec validate fix-spec-drift --strict`, which reported the change as valid

## 3. Verify the corrections against the implementation

- [x] 3.1 Verify that the template named by the corrected specs exists and holds no fallback branches; verified: `analysis/software-architecture/documentation/Documentation.adoc.hbs` exists and holds neither `{{#if aspect}}` nor `{{#if finding}}`
- [x] 3.2 Verify that every file matching the prompt glob carries the object format requirement, so that the scenario that no longer counts them is satisfied; verified: all twenty files matching `analysis/software-architecture/prompts/*_evaluation_all.md` hold the bullet in their Response Requirements section
- [x] 3.3 Verify that the reference holds one entry per documented analysis, so that the requirement the corrected `analysis-reference` states holds; verified with `mvn test -Dtest=AnalysisReferenceTest`, which passed with 4 tests: the reference holds 23 entries and the documented set holds 23 analyses

## 4. Apply the change and check the result

- [x] 4.1 Run `mvn verify` and confirm the build, the unit tests, the SBOM check and the jar smoke test pass; verified: BUILD SUCCESS with 519 unit tests and 6 smoke tests, no failure and no error
- [x] 4.2 Run `openspec archive fix-spec-drift --yes` and confirm the archive retires `enforce-evaluation-object-format`, creates `enforce-evaluation-prompt-format` and modifies the two other capabilities; verified: the archive named the four capabilities, reported `Retiring openspec/specs/enforce-evaluation-object-format/spec.md: all requirements removed`, and the retired directory is absent from `openspec/specs/`
- [x] 4.3 Run `openspec validate --all --strict` and confirm the specifications this change touches validate; verified: `documentation-coverage`, `documentation-template`, `analysis-reference` and `enforce-evaluation-prompt-format` each report as valid under `--strict`. Twelve specifications outside this change report a strict warning for a requirement text longer than 500 characters; twelve of them did so before the change as well, and the removal of the note from `analysis-reference` brought the count of failing specifications from thirteen down to twelve
- [x] 4.4 Confirm the archived result: the new capability holds the requirement with its two scenarios and a Purpose that names the prompt concern, the retired capability holds none of its requirements, and no specification of the project names `Documentation.md.hbs` nor a count of evaluation prompts; verified by reading the four affected specification files and by searching all of `openspec/specs/`, which reports no match for either

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied. — done: archived as `2026-10-20-fix-spec-drift`
- Verify the archived result: the retired capability is gone, the new capability exists as a main spec, and the two corrected Purpose bodies survived the archive. — done, as recorded in tasks 1.3 and 4.4
