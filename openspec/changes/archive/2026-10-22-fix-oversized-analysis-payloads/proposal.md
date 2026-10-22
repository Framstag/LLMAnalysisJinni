# Fix the oversized analysis payloads and the lost usage of a failed step

## Why

The full analysis of a large project failed on one task and lost the token usage of another.

The evaluation of the class reference structure asked the model for the reference graphs, the split
candidates and the dependency diagrams of every module of the project in a single request. The split
candidates of 87 modules were 2.2 MB, and the request that carried all three results was about
625,000 tokens. The model refused it with `context window exceeds limit`, and the task failed after
77 minutes of work. The result of a separation also named every edge it severed, so a separation of
a large module carried 966 edges in the result although the price of the separation is reported in
full anyway.

The task that failed had spent 620,000 input tokens on three model calls before the fourth one was
refused, and none of it was reported. A model call that fails for a reason another attempt cannot
repair ended the step and took the usage of the calls that succeeded before it with it.

## What Changes

- The evaluation of the class reference structure becomes a loop over the modules, so that every
  module is evaluated in a request of its own. The findings of a module are stored in that module,
  and the documentation reads them from there.
- The severed edges of a separation are bounded. The strongest edges are kept, and the number of the
  severed edges is reported beside them.
- A model call that fails carries the usage of the calls that succeeded before it out of the step,
  and the step records that usage.

## Impact

- The capability `split-candidate-analysis` gains a bound on the listed severed edges, and its
  evaluation task is evaluated per module instead of in one response for all modules.
- The capability `workspace-token-usage` keeps the usage of a step whose model call failed, and not
  only of a step whose attempts were all rejected.
- The analysis domain needs a task that is a loop over the modules, and the documentation template
  reads the findings of the class reference structure from the modules.
