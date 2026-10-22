# Design

## The result that does not fit

The tool `java_get_class_dependency_graph` returns the whole graph of a module. For the root module
of the Maven project that is:

| Part | Count | Size |
|---|---|---|
| Top level classes | 3,372 | ~250,000 chars |
| Reference edges | 6,651 | ~950,000 chars |
| Structural edges | 2,501 | ~450,000 chars |
| **Result** | | **1,667,169 chars** |

That is one tool result of about 417,000 tokens, and the step that holds it sends 1.87 million
characters to the model.

## What the task actually needs

The prompt of the evaluation of the class reference structure says:

> `java_get_class_dependency_graph` reports, for the current module, how many classes and reference
> edges its graph holds, how much was excluded, the distribution of edge weights and the heaviest
> couplings.

That is the compact descriptor, and the tool for all modules already returns it:
`compactClassDependencyGraphDescriptor` holds the node and edge counts, the exclusions, the
distribution of the edge widths and the strongest couplings, and it caps the couplings at
`CLASS_GRAPH_STRONGEST_COUPLINGS`.

The tool for a single module returns that and the whole graph on top of it. The graph of every
node and every edge is not what the task asks for, and the ladder of the split candidates carries
the structure that the analysis works on anyway.

## The bound

The lists of the single module graph are bounded:

| List | Bound | Keeps |
|---|---|---|
| Nodes | 200 | the first ones |
| Reference edges | 200 | the strongest by API width |
| Structural edges | 100 | the first ones |

The complete counts are reported beside every list, so a reader knows how much was left out. The
count of the nodes, the reference edges and the structural edges is what the task asks for, and it
stays complete.

The bound of 200 keeps the existing scenario of the capability intact: a module with 148 top level
classes still returns 148 node entries.

## Decisions

- The lists are bounded and not replaced by the compact descriptor alone. The capability
  `class-dependency-graph` asks for a tool that returns the graph of a module, and a caller that
  wants the names of the classes of a module can still have them.
- The reference edges that are listed are the strongest ones. The strength of an edge is its API
  width, which is the same measure the compact descriptor sorts by, so the two tools agree on what
  the strongest couplings of a module are.
- The complete counts are always reported. A bounded list without the count of the whole is a
  silent omission.
- The bound is a constant of the tool, and the test reads the constant rather than repeating the
  number, so a change of the bound does not break the test.
