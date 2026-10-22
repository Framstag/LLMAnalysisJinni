# Bound the class reference graph of a single module

## Why

The evaluation of the class reference structure asks for the reference graph of a module with the
tool `java_get_class_dependency_graph`. That tool returns the whole graph: every top level class,
every reference edge and every structural edge.

For the root module of the Maven project that is 3,372 classes, 6,651 reference edges and 2,501
structural edges. The result is 1,667,169 characters, about 417,000 tokens, in a single tool
result. The step that holds it sends 1.87 million characters to the model, and the root module
alone is 30% of the whole task.

The prompt of the task asks for something much smaller: "how many classes and reference edges its
graph holds, how much was excluded, the distribution of edge weights and the heaviest couplings".
The tool for all modules answers exactly that, with the couplings capped. The tool for a single
module returns the whole graph on top of it.

## What Changes

- The tool that returns the class reference graph of a single module bounds the node list, the
  reference edge list and the structural edge list.
- The bounds are reported beside the lists, and the complete counts are always reported.
- The edges that are listed are the strongest ones, because they are the ones that describe the
  structure of the module.

## Impact

- The capability `class-dependency-graph` gains the bound on the listed nodes and edges of the
  graph of a single module.
- The tool `java_get_class_dependency_graph` returns a bounded result.
- The evaluation of the class reference structure no longer sends the whole graph of a module to
  the model.
