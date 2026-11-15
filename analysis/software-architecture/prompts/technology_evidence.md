## Current Goal

Name the technologies the project uses, based on the namespaces its own production code imports.

This analysis exists for a project that holds no dependency data. It claims nothing about an artifact, a version or a
license: it says which technologies the imported namespaces belong to.

## Facts

The evidence is the consolidated import distribution of the project: the external namespace prefixes its production
code imports, grouped by the first segment of the prefix, with the number of imports of each prefix.

The namespaces of the Java platform itself and the namespaces of the project are already removed from the evidence.
The namespaces below `javax` and `jakarta` are kept on purpose, because the marker facts of the modernisation analysis
key a legacy dependency by them.

## Solution strategy

* Call the tool `java_get_import_technology_evidence` once.
* Read the `groups` of the result. A group names a root segment, the number of imports that use it, and the prefixes
  it holds.
* Name for every group the technology or the family of libraries its namespaces belong to, and list the namespaces
  the name rests on.
* Report a group you cannot place in `unplaced`, and name no technology for it.

## Hints

* Name a technology and not an artifact. `org.springframework` names Spring, and it does not tell whether the project
  uses Spring Boot or Spring MVC. A library that a root segment hides, such as `org.apache`, belongs to a family of
  libraries and not to one of them.
* State no version. The evidence of this analysis carries none, and the answer has no field for one.
* Read `returnedGroups` against `totalGroups`, and the prefixes of a group against its `totalPrefixes`. When a list
  is bounded, say in the reasoning that it is a part of the groups of the project and not the whole of them.
* An empty `groups` list is a result: report it with the `reason` the tool stated, and state no cause that the tool
  did not report.
* The tool result also names the modules whose import distribution could not be read in `skipped`. Name them in the
  reasoning, because the technology of those modules is not part of the answer.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `TechnologyEvidence` response schema.
* Each `technologies[]` entry MUST be a JSON object with the fields `name`, `namespaces` and `reasoning`. Do NOT use
  plain strings.
* The `namespaces` field of a technology entry is an array of namespace prefixes exactly as the tool spelled them,
  nothing else: no sentence, no reason, no tool output. Anything else in this field is a defect of the answer and the
  answer is rejected, because the field is bounded.
* The `limit` field states that a namespace group is coarser than an artifact and that the evidence carries no
  version, so that a reader knows what the answer does not say.
* Each `unplaced[]` entry MUST be a JSON object with the fields `namespace` and `reasoning`.
* Name at most one technology per group of the evidence. A longer response is cut off before its JSON is complete, so
  it is rejected and retried.
* You must only return clean JSON content without any prefix or postfix.
