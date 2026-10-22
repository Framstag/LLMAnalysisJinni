# Proposal

## Why

An analysis of a 90 module project with a cloud model failed on four of the forty one tasks, and the
engine reported the failures in a way that points at the wrong cause.

The build file analysis of a module has to find the build file of that module. It is told the name
and the path of the module and the build system of the project, and it is given the two tools that
read files. It is not told the name of the build file, even though the project already knows it: the
analysis carries a fact that maps every build system to the wildcard of its build file, and two of
the three module tasks that read build files already use that fact. So the model guesses, guesses
wrong once in a while, and recovers by probing. The transcript of a failing module shows it reading
the build file of the root module, fifty four kilobytes of it, instead of the two and a half kilobyte
build file of its own module, and then asking for one file after another, one tool call per file,
until it ran out of tool rounds. The module never got its answer.

The engine could have said that. It knows why the model asked for what it asked for, because the
model puts its reasoning into the response, and the engine currently throws that reasoning away. But
it does not look at the reason the model stopped, so a response that was cut in the middle of a
sentence is treated like a complete answer, and the resulting parse error is reported as a payload
that does not parse. The reader is told that the model produced malformed JSON, when the model
produced a truncated answer.

Three of the four failing tasks did not even reach the tool round bound. They produced a JSON
answer whose text stopped mid word, and the engine could not tell that from a wrong answer.

## What Changes

- Tell the build file analysis which file the build file of a module is. The analysis already knows
  the build system of the project and the wildcard of its build file, and the path of the module, so
  the prompt can name the file to read instead of leaving the model to guess it.
- Remove the hint that tells the model to check that a file exists before it reads it. The hint
  invites one tool call per candidate file, which is what the tool round bound of an attempt was
  reached for. The build file of a module is known, so the check is not needed.
- Give the build file analysis the tool that lists the files of a directory. The task can then find
  the build file of a module and any build file next to it in one call, the way the other two module
  tasks that read build files already do.
- Report a response that the model stopped in the middle of as what it is. The engine reads the
  reason the model stopped and reports a truncated response as its own kind of failure, with the
  reason in the message, instead of reporting the parse error of a half answer.
- Keep the messages that carry the task out of the window that bounds the conversation of an
  attempt. A step that needs many tool rounds pushed the task out of the context, and the model
  answered without knowing what it was asked to do, which is what made the empty answers of the
  failing modules happen.
- Keep the reasoning of the model available for the diagnosis of a failed step, so that a reader can
  see why the model asked for what it asked for. The chat log of a step keeps it, so a failed step
  can be read.
- The other failing tasks are not changed by this. They fail because the model sometimes answers in
  one piece and sometimes does not, which the reporting change makes visible and the attempt budget
  of the step already handles.

## Capabilities

### New Capabilities

- `build-file-identification`: the build file of a module is identified from the build system of the
  project and the path of the module, so that the build file analysis reads the build file of the
  module it analyses and does not probe for it.

### Modified Capabilities

- `llm-response-extraction`: a response that the model stopped in the middle of is recognised as a
  truncated response and reported as such, instead of being reported as a payload that does not
  parse.
- `llm-tool-execution`: the bound on the tool rounds of an attempt is reported with the number of
  tool calls the model asked for, so that a step that reached the bound is recognisable as a step
  that asked for too much, and not only as a step that failed. The messages that carry the task are
  kept out of the window that bounds the conversation, so that a long tool conversation cannot push
  the task out of the context.

## Impact

- `analysis/software-architecture/prompts/module_analysis_buildfiles.md`: the prompt names the build
  file of the module and no longer asks the model to check whether a file exists before it reads it.
- `analysis/software-architecture/tasks.yaml`: the build file analysis gets the tool that lists the
  files of a directory, in addition to the tools it already has.
- `src/main/java/com/framstag/llmaj/lc4j/`: the outcome of a step carries the reason the model
  stopped, the response handling reports a truncated response as its own kind of failure, the chat
  log of a step keeps the reasoning of the model and the whole conversation, and the conversation of
  an attempt keeps the task out of the window that bounds it.
- `src/main/java/com/framstag/llmaj/lc4j/StepFailureReason.java`: a new reason for a response that
  the model stopped in the middle of.
- The response schemas, the response formats, the MCP tools and the analysis results are not
  changed. The analysis produces the same findings; it reaches them with fewer model calls and
  without the failures this change removes.
