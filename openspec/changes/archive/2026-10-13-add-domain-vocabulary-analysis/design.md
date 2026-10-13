# Design

## Context

See `proposal.md` - Why.

What the current state gives us, measured on the real Maven workspace rather than assumed:

- The report collection already writes one raw report per module to `Java_<moduleName>.json`; `JavaTool.getModuleReport(moduleName)` reads and caches it. `maven-core` alone holds 735 classes, 4,755 methods and 5,678 fields in 5.6 MB of JSON.
- `BuildUnit` already carries `production`, `generated`, `imports` and the weighted `references` list between build units, so both the code/test split and the class reference edges exist without a new parse.
- `Clazz` and `Method` already carry their documentation text, so the comment channel needs no new parsing either.
- Every batch metric follows the same shape: `getAll*Reports()` walks `getJavaModuleNames(state)`, calls a per-module report provider that writes its CSV, and returns `{metric, reports[], skipped[]}`. `getAllModuleMetricReports` already collects the modules it could not process.
- Analysis bounds are seeded into `config.json` (`seedAnalysisReportBudgets`), so a reader can see the numbers a report was produced with. The reference document is required to name them.
- Constraint from `AGENTS.md`: no new Maven dependency for analysis logic. That removes latent semantic indexing and any vector library from the table.
- Constraint: only Java is parsed, so a non-Java module has no report at all and cannot be judged.

## Goals / Non-Goals

**Goals:**

- Measure the language of a module from data the engine already has, without a second parse and without a new dependency.
- Keep every counted fact on disk in a form a human can sort, grep and pivot, so a model finding can be checked against its evidence.
- Keep the model's input bounded, because the vocabulary of a module is thousands of terms while a prompt is not.
- Make the technical share a number without a verdict: judging it needs the module's purpose, and that judgement happens in the evaluation task.

**Non-Goals:**

- No latent semantic indexing, no tf-idf vectors, no clustering. Overlap is set based Jaccard, and the reference entry must not call it conceptual cohesion in the LSI sense.
- No string literals, configuration keys, message texts or properties: identifiers and documentation only.
- No local variable and no parameter names in this change. Both are reachable from the source parser, but parameters need a report format version bump and a regeneration, which is a change of its own.
- No cross-module joins, no context map, no intrusion analysis: that is change B.
- No claim that a recommendation is safe to apply. The analysis reports language, not behaviour.

## Decisions

### D1: Compute from the stored module reports, in a tool, in one pass over the module

Chosen: a new report provider computes the vocabulary of one module from `getModuleReport(moduleName)`, writes its CSVs, and returns the compact rollup; a batch tool loops the modules exactly like the existing `getAll*Reports()` family.

Alternatives: re-parsing source and class files (duplicates what report collection just did, and makes the vocabulary depend on the parse order of two parsers); letting the model read the report JSON (5.6 MB per module, impossible); a separate engine pass outside the tool model (breaks the one-tool-call pattern every other metric uses).

### D2: Token rules, one lexicon tier per token

Splitting: camel case boundaries, `_`, digits, lowercase. Dropped: single character tokens, Java keywords.

Four tiers, and `unclassified` is the important one - it is what "candidate domain vocabulary" means, because the tool cannot know a domain it has not been told about:

| tier | meaning | seeded examples |
|---|---|---|
| `technical` | plumbing and framework vocabulary | impl, manager, factory, builder, handler, util, adapter, abstract, default, dto, bean, annotation |
| `generic` | words that carry no domain, only shape | result, value, data, item, entry, node, list, map, key, size, name, id, type |
| `noise` | language and accessor boilerplate | get, set, is, has, to, from, of, with, args, tmp, i, j |
| `unclassified` | not listed, therefore a candidate domain term | realm, artifact, resolution, plugin, repository |

`context`, `config` and `data` are the ambiguous ones; they are seeded as `generic`, and the evaluation may still call one a domain term when the module's purpose says so. That is exactly the split between counting and judging.

Channels: `Type`, `Method`, `MethodVerb` (first token of a method name, counted additionally), `Field`, `Package`, `Doc`. A token may occur in several channels; the CSV reports the count per channel.

Only `production` build units are counted. Test and generated build units are excluded, matching the `Category` split the existing reports already use.

### D3: The lexicon is a versioned CSV in the analysis directory

Chosen: `analysis/software-architecture/facts/domain_lexicon.csv` with columns `Term,Tier,Note` and a `lexiconVersion` line, resolved through a config property seeded to that path. The evidence names the version.

Alternatives: a Java constant list (not editable without a rebuild, and the tier of a term is exactly the thing an analyst wants to fix); a prompt partial (the tool cannot read it, and it is not the model that classifies).

The lexicon is data, not static prompt knowledge, so it lives as a CSV next to the analysis and is never included as a prompt partial.

### D4: Metric definitions

Occurrences count token occurrences inside identifiers, so `ProjectProject` weighs twice. Only the tiers `technical`, `generic` and `unclassified` take part in the shares; `noise` rows exist in the CSV and nowhere else.

| metric | definition |
|---|---|
| `distinctDomainTerms` | distinct tokens of tier `unclassified` |
| `technicalShare` | occurrences of `technical` / occurrences of `technical`+`generic`+`unclassified` |
| `genericShare` | the same for `generic` |
| `topTermShare` | occurrences of the five most frequent `unclassified` terms / occurrences of `unclassified` |
| `namingCohesion` | mean Jaccard of the token sets of class pairs joined by a build unit reference, minus the mean Jaccard of the pairs without one |
| `purposeCoverage` | distinct tokens of the module's purpose and architecture text that occur in the module's code vocabulary / distinct tokens of that text |
| `codeCoverage` | the twenty most frequent `unclassified` terms that occur in that text / twenty |
| `documentationOverlap` | Jaccard of the identifier token set and the documentation token set |

A class token set is the tokens of its simple name plus the tokens of its method names, its field names and the simple names of its field types. Reference pairs and non-reference pairs are taken from the same population: class pairs of two *different* build units of the module, so that a large build unit cannot dominate the baseline. If either mean has no pair, cohesion is reported as unavailable rather than as zero.

`purposeCoverage` and `codeCoverage` are reported as unavailable, not as zero, when no purpose or architecture description exists, because a missing description is not missing coverage.

### D12: A descriptor, a synthetic name and markup are not names

The first run over a real corpus (Apache Maven, JabRef, 90 and 14 modules) put `<init>`, `clinit`, `ljava`, `lorg`, `lang` and `code` at the top of the candidate terms of nearly every module. All three are extraction artefacts, not language, and each is answered in the extraction step:

* A class file writes the synthetic names `<init>` and `<clinit>` for a constructor and a static initializer. They name no concept of the module and carry the highest occurrence count of every method name, so they are skipped.
* A field type in a class file is a descriptor, `Ljava/lang/String;`, not a name. Counting it added `ljava`, `lang`, `org` and `apache` to the vocabulary of every module that holds a field of a library type. The descriptor is reduced to the name it carries, the same way a qualified source name is.
* Documentation comments carry javadoc and HTML markup; `{@code ...}`, `{@link ...}`, `@since` and `<li>` contributed `code`, `link`, `since` and `li` to the documentation channel. The markup is removed before the text is tokenized, while what it contained stays: the type inside a `{@link ...}` is vocabulary like any other name, with its package qualifier removed.

The effect is not cosmetic. Before the fix, the leading terms of `maven-core` were dominated by `init` and type-package fragments; after it they are `project`, `maven`, `artifact`, `execution`, `lifecycle`, `build`, `mojo`, `session`, `dependency`, `scope`, `phase`, `pom`. The whole analysis is worth nothing if it counts bytecode of the compiler instead of the language of the module.

### D5: Three report types, no new filename convention

Chosen, following `report-directory-structure` as written:

```
DomainVocabulary/<module>.csv
    Term,Tier,Occurrences,TypeOccurrences,MethodOccurrences,MethodVerbOccurrences,
    FieldOccurrences,PackageOccurrences,DocOccurrences
NamingCohesion/<module>.csv      Class,OtherClass,Linked,Jaccard,SharedTerms
DomainLanguageMetrics/<module>.csv
    Metric,Value                 (lexiconVersion, candidateTerms, minimumCandidateTerms,
                                  sufficientEvidence, the tier totals and shares, namingPairs,
                                  namingCohesion, namingCohesionLinked, namingCohesionUnlinked,
                                  purposeCoverage, codeCoverage, documentationOverlap,
                                  absentPurposeTerms, absentCodeTerms)
```

Alternatives: one file per module with a `Kind` column (three different schemas in one file, unreadable); filename suffixes such as `<module>.terms.csv` (deviates from the spec, which names the module as the filename body, and would need that spec changed for no gain).

Sorted by term, no timestamps, no run identifiers: two runs over the same report produce byte-identical files, which is what makes a diff between two runs meaningful.

### D6: The model gets a bounded rollup, the disk gets everything

Per module the tool returns: the metrics, the tier totals, the top `domainLanguage.topTermsPerModule` (seeded 20) `unclassified` terms with their occurrences and channels, and the leading absent purpose terms. Modules below `domainLanguage.minimumCandidateTerms` (seeded 50) return the insufficient-evidence state with their count **and their leading terms**, but no share, no coverage value and no naming cohesion: a reader has to see the module was not judged because it is small, and not because the analysis failed. Modules without a report or with a report written as an older format version appear in `skipped`. A module that is not Java is named there too, instead of being silently omitted.

The seed of 50 came from the corpus: of 87 Maven modules, 42 hold fewer than 25 candidate terms and 59 hold fewer than 100. No bound judges most of them, because those modules are small, which is a fact about Maven's module granularity rather than about the gate. Judging them at a coarser granularity is what the vocabulary overlap of change B is for.

Bounds: 40 modules x 30 lines is about the size of one existing batch metric result, so the prompt budget the batch pattern was built for holds. The full term list stays on disk, which is the point of the CSV requirement: the model sees a summary, the human sees the corpus.

### D7: Two tasks, one of them a glossary

```
module_analysis_reports_all + module_description + module_architecture
        |
        v
DomainVocabularyEvaluationAll      java_get_all_domain_vocabulary_reports
        |                          results/ModuleBatchEvaluation.json
        v                          tag: domain_vocabulary_evaluation
DomainGlossary                     java_get_all_domain_vocabulary_reports
        |                          results/DomainGlossary.json -> analysis.domainGlossary
        v                          tag: domain_glossary
DomainLanguageEvaluation           java_get_all_domain_vocabulary_reports
                                   results/ModuleBatchEvaluation.json
```

Both tasks call the same tool, so the reports are computed twice per run. That is accepted: the computation is a few seconds against a cached report, the CSV write is idempotent and byte-identical, and the alternative - a task with no model call to store the rollup - is not a shape the engine has.

The batch evaluation reuses `results/ModuleBatchEvaluation.json` unchanged, so no new response schema is needed for it. Only the glossary needs a schema of its own.

Ordering is a real dependency, not a preference: the evaluation prompt carries the glossary, and its judgement of "domain or technical" is only consistent across modules because the glossary was fixed before the first module was judged.

### D8: Documentation and reference

- `Documentation.adoc.hbs` gains a chapter for the domain language of the modules: the glossary, then the per-module findings in the existing evaluation table shape, with the pointer line to the reference entry.
- `AnalysisReference.adoc.hbs` gains the entries: what each share measures, that the technical share has no threshold of its own, the steering table with lexicon path, minimum token count and top-term count, and the caveats - identifiers only, no literals or configuration keys, no LSI, English-biased, generated and test code excluded.
- `analysis/software-architecture/README.md` gains the task rows and the new files in its directory listing, per `analysis-documentation`.

### D9: Tests

Unit tests on a hand-built module model pin the tokenizer, the lexicon tiers, the share arithmetic, the noise rows surviving into the CSV, the naming cohesion arithmetic on constructed pairs, the unavailable-coverage state and the insufficient-evidence gate. One test asserts that two runs over the same fixture produce byte-identical CSVs, which is the only way the no-timestamp rule stays true.

## Risks / Trade-offs

- [The lexicon drifts from the codebase and quietly reclassifies domain terms as technical] -> the tier of every term is a CSV column, so a wrong entry is visible and fixable without a code change; the evidence names the lexicon version, so two runs are comparable; a unit test pins the tiering on a fixed lexicon.
- [A reader treats `technicalShare` as a defect] -> the reference entry states that it carries no threshold of its own, and the evaluation prompt requires the judgement to be made against the module's purpose.
- [Small modules produce dramatic numbers from twenty identifiers] -> minimum token gate, insufficient-evidence state, and no metric values at all for such a module.
- [Identifier-only analysis is read as a statement about the domain] -> the caveat is part of the reference entry, and the non-goals name the unread material.
- [Naming cohesion is mistaken for the LSI-based conceptual cohesion of the literature] -> the reference entry names it as a set-overlap approximation and says what it leaves out.
- [Two tool calls per run] -> accepted, computation is seconds and the CSV write is idempotent.
- [Non-English identifiers or a lexicon tuned to one stack] -> the lexicon is editable per analysis; the caveat states that an unlisted word is a candidate, not a domain term.
- [A module whose sources were unavailable has no documentation channel] -> `documentationOverlap` is reported unavailable, never as zero.

## Migration Plan

Additive only: a new task is active by default, and no existing task, prompt or report changes behaviour. Rollback is setting the two new tasks to `active: false`, which leaves the CSV evidence and the stored properties behind but removes the run cost. No report format version is bumped, so no workspace needs a regeneration for this change.

## Open Questions

- Whether parameter and local variable names are worth the report format version bump. Deferred; the channel model already has room for them.
- Whether the seeded tier of `context`, `config` and `data` survives contact with real workspaces. Deferred until the Maven run shows how often they dominate a module.
