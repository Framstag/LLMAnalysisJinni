# Design

## Context

See `proposal.md` - Why. This design assumes the change `2026-10-13-add-domain-vocabulary-analysis` is in place: the tokenizer, the lexicon tiers, the per-module counts and the CSV writer shape all come from there, and the per-module evidence is the input of everything below.

What the current state adds for this change:

- `InterModuleDependencyMatrix.csv` already holds `From,To,ImportCount` for every module pair that shares an import, written by `java_get_inter_module_dependency_report` and stored under `InterModuleDependencyMatrix/`.
- `GlossaryCandidates` style data does not exist yet, but the per-module evidence from the vocabulary analysis holds every term with its tier, its channels and its occurrences, so aggregation needs no new parse.
- The batch tools all follow `{metric, reports[], skipped[]}`, and `AnalysisReference` requires a steering table naming each configuration value with its seeded default.
- A module without a Java report appears in no per-module evidence, so the project-level tool must be able to answer for a project that has one module, and must name what it left out.

## Goals / Non-Goals

**Goals:**

- Answer the relational question with values that already exist: which modules share language, which one's language leaked into which, and which ones are coupled without sharing language.
- Keep the project-level evidence in files that survive the run, because the term-by-module table is the join key a human needs to check any of the other views.
- Bound the model input while keeping the corpus on disk.

**Non-Goals:**

- No clustering algorithm, no graph partitioning, no automatic context detection. The overview reports values; the model proposes the contexts, and the design deliberately does not put a partitioner between the two.
- No temporal analysis: no vocabulary drift over commits, no version comparisons. There is no history in the evidence.
- No change to the inter-module dependency analysis or to the vocabulary analysis; this change reads both.
- No new response schema for the per-module findings: they stay in `ModuleBatchEvaluation`. Only the project-level context map gets a schema of its own.

## Decisions

### D1: Aggregate the evidence, do not recompute the tokenization

The overview tool walks the Java modules of the state, asks the vocabulary provider of the previous change for the evidence of each module, and aggregates. The provider caches per module, so a run that computes both the per-module evaluation and the overview pays the tokenization once per module.

Alternative: read the per-module CSVs back from disk. Rejected - the CSVs are evidence for a human, not an interchange format, and a run that reads its own intermediate files gets a second failure mode (a stale file from an earlier run).

### D2: Term span as the deduplication key

The project view keys a term by its token string and not by module or tier, so `TermTotals.csv` answers "how many modules use this word" in one row. Tiers can differ per module for the same term; the rollup names the dominant tier and the per-module rows keep their own, so a term that is technical in one module and domain in another stays visible.

Alternative: key by module and term only (no rollup). Rejected - the span is the most useful single number of the whole analysis, and it is one pass to compute.

### D3: Intrusion is measured against ownership, not against a dictionary

A term is intruding in module `i` when it is established in module `j` and used by `i`, where established means it belongs to the leading candidate terms of `j` and `j != i`. Its share is its occurrence count in `i` over the candidate domain occurrences of `i`.

Ownership is a set, not a single module: a term established by two modules is owned by both, and the intrusion row names the owners as a list. A term owned by the module itself is never an intrusion, whatever its share. The report says for each row whether the owners' descriptions carry the term, so a term that belongs to another module's stated purpose is distinguished from a term that is merely frequent in it.

Alternative: a global technical/domain dictionary decides ownership. Rejected - the dictionary cannot know which module a domain word belongs to, and the whole point is the relation between modules.

### D4: The quadrant is a join, and it states its two axes explicitly

```
                        lexical overlap with the other module
                        none                        present
                 +--------------------------+--------------------------+
  import         |  independent             |  CANDIDATE DOMAIN LEAK   |
  count 0        |  (reported, not flagged) |  shared words, no code   |
                 |                          |  path between them       |
                 +--------------------------+--------------------------+
  import         |  translation             |  coupled                 |
  count > 0      |  (a boundary that pays   |  (shared language,       |
                 |  for itself)             |  shared code)            |
                 +--------------------------+--------------------------+
```

Thresholds: the lexical side is present when the pair shares at least one candidate domain term that is a leading term in both modules, and the overlap value is reported beside the classification so a reader can apply their own bound. The structural side is present when the import count is greater than zero. Both values appear in the row; the classification is derived and is named as a candidate, never as a finding of fact.

A pair that shares vocabulary but has no import record is reported with an import count of zero, not omitted - which is exactly the leak case, so omitting it would drop the answer.

### D5: One project-level report directory, one file per view

```
DomainLanguageOverview/
   TermByModule.csv       module,term,channel,tier,occurrences
   TermTotals.csv         term,occurrences,moduleCount,modules,dominantTier
   TermOverlap.csv        fromModule,toModule,sharedTerms,overlap,exclusiveFrom,exclusiveTo
   TermIntrusion.csv      module,term,ownerModules,occurrences,domainShare,ownersCarryTerm
   StructuralVsLexical.csv fromModule,toModule,importCount,overlap,classification
   ContextCandidates.csv  term,moduleCount,modules,dominantTier,inOwnersDescription
```

This is the one spec-level change of this proposal beyond the new capability: the existing `report-directory-structure` rule gives a project-level report a file named after the report, which cannot hold six views with different columns. The delta adds the view naming rule.

Alternatives: one directory per view (six more top-level directories in the workspace, for a report family that is read together); one file with unioned columns (six schemas in one table, unreadable). Both rejected.

Same rules as the per-module evidence: sorted, no timestamps, no run identifier, so two runs diff cleanly. `TermByModule.csv` is the join key of the other five: every module or term that appears in them can be looked up there, which is what makes the model's claims checkable by hand.

### D6: The model gets the classification counts, not the whole pair list

The overview tool returns: the leading terms with their module span, the intruding terms with owners and shares, the pairs with both values when either side is present, the classification counts, and the skipped modules. Every list is bounded by a configured leading count, and the full list stays in the CSV.

For a large project the pair list is quadratic in the modules; the returned list is therefore filtered to the pairs where at least one side is present, which is the set the judgement needs, and is bounded per classification. The spec requires every pair with vocabulary to be reported in the CSV, not in the tool result.

### D7: The context map is one task with its own schema

`ContextMapEvaluation` depends on the vocabulary evaluation tag, the module description tag and the inter-module dependency evaluation tag. It calls the overview tool once and stores `contexts[]`, `relations[]` and `unplacedModules[]` under its own response property via `results/ContextMap.json`.

Alternative: reuse `ModuleBatchEvaluation`. Rejected - a context groups modules, so a per-module entry cannot express a context without duplicating it once per member, and the relation between two contexts has no place in that schema at all.

The prompt requires each relation to name the counted evidence it rests on (import count, overlap, intrusion share), which is what keeps the DDD vocabulary from becoming decoration.

### D8: Documentation and reference

- `Documentation.adoc.hbs`: a context map chapter with the context candidates, the relations, the unplaced modules, and the quadrant as a table, preceded by the pointer to its reference entry.
- `AnalysisReference.adoc.hbs`: the context map entry - both axes of the quadrant, the intrusion definition, the caveats (no partitioner, a candidate is not a finding, identifier-only evidence, quadratic pair list bounded in the tool result), and the steering table with the seeded bounds.
- `analysis/software-architecture/README.md`: task row and the new project-level report directory in the listing.

### D9: Tests

Unit tests on a fixture project of three or four hand-built module evidences pin: the term rollup with a term spanning two modules, intrusion with a self-owned term excluded, the quadrant classification for all four combinations, the zero-import pair being reported rather than dropped, the CSV view file names and byte-identical reruns, the single-module project answering without pairs, and the skipped list.

## Risks / Trade-offs

- [The quadrant is read as a verdict] -> the classification is named a candidate, the values are printed beside it, and the reference entry says a candidate is not a finding; the evaluation prompt must name the counted evidence behind each relation.
- [Ownership by leading terms is arbitrary at the boundary of a term list] -> leading terms are configured and seeded, the owners are a list rather than one name, and the CSV holds the values so a reader can re-derive ownership with another bound.
- [The pair list grows quadratically] -> the tool result is filtered and bounded while the CSV holds everything, which is the case the disk evidence exists for.
- [Vocabulary overlap between an `api` module and its implementations is expected, not a leak] -> the quadrant reports a relation, and the model is required to state whether the overlap follows the module purpose, which is where the api/impl case is separated from a genuine leak.
- [A module without vocabulary evidence is silently absent from a context] -> unplaced modules are a named part of the result and of the rendered chapter.
- [The delta for `analysis-reference` is written against the post-vocabulary-change text] -> the change depends on `2026-10-13-add-domain-vocabulary-analysis` being archived first, and says so in the delta and in its proposal impact.

## Migration Plan

Additive. The context map task stays inactive until the vocabulary analysis has run once on a workspace, because it has nothing to aggregate before that; its dependency on the vocabulary tag enforces the order anyway. Rollback is setting the task to `active: false`. The new report directory stays behind, which is harmless because the next run overwrites the view files.

## Open Questions

- Whether the intrusion definition should use leading terms in both modules or leading terms in the owner only. Deferred: the values allow re-deriving it, and the seeded bound can be tuned after the first real run.
- Whether the quadrant deserves a rendered picture like the dependency diagrams. Deferred: the table is the contract, a diagram is decoration that can be added once the numbers are trusted.
