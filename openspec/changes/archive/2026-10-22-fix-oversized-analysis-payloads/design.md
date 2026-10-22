# Design

## The request that does not fit

The evaluation of the class reference structure was a batch task: one task, one response, the
findings of every module. It called three tools, each of which returned the data of every module
of the project.

| Result | Size | Tokens |
|---|---|---|
| `java_generate_all_dependency_diagrams` | 134,622 chars | ~33,655 |
| `java_get_all_class_dependency_graphs` | 106,464 chars | ~26,616 |
| `java_get_all_split_candidates` | 2,262,231 chars | ~565,557 |
| **Request** | | **~625,000** |

The context window of the model is 65,536 tokens, so the request was an order of magnitude too
large. The model refused it with `invalid params, context window exceeds limit`.

The data of the whole project in one request cannot be made to fit. A bound on the payload of one
tool is not enough: the two smaller results alone are 60,000 tokens, and the split candidates of 87
modules do not fit into what is left even when they are cut to the bone.

## The task becomes a loop

The engine already has the mechanism for this: a task with `loopOn` runs one step per element of
the list, and every step is a fresh conversation. The data of a single module is small enough.

The task `ClassDependencyGraphEvaluationAll` now loops over `/modules/modules` and calls the three
tools that work on a single module: `java_generate_dependency_diagrams`,
`java_get_class_dependency_graph` and `java_get_split_candidates`.

The findings of a module are stored in that module, because the engine writes the result of a loop
step into the element it belongs to. The documentation template reads them from there.

The same work is done, spread over 83 steps instead of one. The task went from failing after 77
minutes to succeeding after 9 minutes.

## The severed edges are bounded

A separation of a large module severed 966 edges, and the result named all of them. The price of
the separation does not depend on the list: the bottleneck cost, the weakest and the strongest
severed weight and the total severed weight are reported as figures. The list of the edges is
diagnostic.

The list is therefore bounded to the ten strongest edges of a separation, and the number of the
severed edges is reported beside the named ones. The price of the separation is unchanged, and the
reader knows that the list is a part of the whole.

The bound alone brings the result of all 87 modules from 2.2 MB down to 353 kB. It does not make
the request fit, and it is not meant to: the loop is what makes the request fit. The bound keeps
the result of a single module readable.

## The usage of a failed model call

The model call was the fourth one of the attempt. The first three had succeeded, and their tokens
were spent, but the failure of the fourth one ended the step and the accumulated usage went with
it. The state of the failed task carried no usage at all, although 620,000 input tokens had been
paid for it.

The failure of a model call now carries the usage of the calls that succeeded before it:

- `ChatExecutor` wraps a failed model call in a `ModelCallFailure` that carries the usage so far.
- `TaskStepRetrier` reads the failure: a retriable one becomes a rejected attempt that carries its
  usage, a non-retriable one ends the step and carries the usage of the whole step with it.
- `AnalyseCmd` records that usage before it marks the step as failed.

The error the model reported stays the cause of the failure, so the reason it gave for stopping and
the question whether another attempt may get past it are unaffected.

## Decisions

- The task is a loop and not a batch with a tool that takes a module filter. A batch would keep
  the results of every batch in the conversation, and the conversation grows with every tool round,
  so the request of the last round would carry all of them again. A loop gives every module a
  fresh conversation, which is the only way the data of one module reaches the model alone.
- The single module tools are used and not the tools for all modules with a filter. The tools for
  all modules compute the data of every module on every call, so a filtered variant would repeat
  the whole computation for every batch.
- The bound of the severed edges is ten. A separation of ten edges is enough to name the ties that
  decide the price and the packages they run between, and the price is reported as a figure
  besides them.
