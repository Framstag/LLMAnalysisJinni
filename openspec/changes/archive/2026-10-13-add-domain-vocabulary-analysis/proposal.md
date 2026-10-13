# Proposal

## Why

The engine measures structure — complexity, coupling, cohesion, tangles — but never the language of the code, although every Java module report it produces already holds every class, method and field name. Domain-driven design rests on exactly that unmeasured material: one module should speak one domain, a name should mean one thing, and a term that belongs to another module's domain, appearing in this one, marks a context boundary that leaks. A reader of the report can today see that two modules are structurally coupled, but not that they share code while describing different domains, or that a single concept carries three different names inside one module.

## What Changes

- Add Java tool support that extracts identifiers from the existing raw module reports, splits them into tokens, classifies each token as technical, generic or noise against a versioned lexicon, and counts occurrences per module and channel (type, method, field, documentation).
- Add one batch report tool returning the compact rollup of those counts for all Java modules, so a single model call can judge every module.
- Dump the post-processed evidence as CSV files beside the other reports, one row per fact, so a human can verify every number the model reasoned over. Terms the metrics drop as noise stay in the CSVs with their tier label.
- Add a glossary task that builds one project-level ubiquitous-language glossary from the module purposes and the extracted vocabulary, and a batch evaluation task that judges each module's language against it: domain versus technical, consistency, naming cohesion, purpose coverage.
- Render the result as a documentation chapter and add the reference entry that explains the metrics, their thresholds and their caveats.
- No new Maven dependency. Token counting, tf-idf-free overlap and Jaccard are computed in plain Java; no latent-semantic indexing.

## Capabilities

### New Capabilities

- `domain-vocabulary-extraction`: extraction, tokenization, lexicon classification and counting of identifiers from the Java module reports, the per-module CSV evidence contract and the batch report tool over all modules.
- `domain-language-evaluation`: the model-facing judgement of the extracted vocabulary per module, the project glossary it is judged against, and the batch result shape.

### Modified Capabilities

- `batch-java-metric-evaluation`: the enumeration of metrics that get a batch report tool and a batch evaluation task gains the domain vocabulary evaluation.
- `analysis-reference`: the documented set of analyses gains the domain language analyses, so the reference holds their entries.
- `documentation-coverage`: the report renders the domain language evaluation section, and that section carries the pointer to its reference entry.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/`: identifier tokenization, lexicon classification, counting and CSV writing beside `JavaTool`, plus two new tool methods.
- New CSV report types `DomainVocabulary/`, `NamingCohesion/` and `DomainLanguageMetrics/` under the workspace directory, following the existing `<ReportType>/<sanitizedModuleName>.csv` convention, so no change to `report-directory-structure` is needed.
- `analysis/software-architecture/`: two new task definitions, two prompts, two response schemas, the domain-language lexicon, the documentation chapter and the reference entries, and the README task table.
- Unit tests for tokenization, lexicon classification and the counting and CSV contract; no change to the existing Java parser behaviour.
- Java-only: a module without a Java report is reported as skipped, never silently omitted.
