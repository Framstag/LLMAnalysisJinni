# Tasks

## 1. Name the build file of the module in the prompt

- [x] 1.1 Add the fact that maps a build system to the wildcard of its build file to the prompt of
      the build file analysis, and verify that the prompt of the task contains the build file of the
      build system of the project
- [x] 1.2 Tell the prompt to read the build file of the module it analyses, at the path of that
      module, and verify that the prompt no longer asks the model to locate the build file
- [x] 1.3 Remove the hint that tells the model to check that a file exists before it reads it, and
      verify that the prompt no longer contains that hint
- [x] 1.4 Update the task table of the analysis README so that the changed task is described by its
      new behaviour, and verify that the table names the task

## 2. Give the build file analysis the tool that lists files

- [x] 2.1 Add the tool that lists the files of a directory that match a wildcard to the tools of the
      build file analysis, and verify that the task keeps the tool that reads a file
- [x] 2.2 Verify that the task does not use a broad wildcard for its tools, and that every tool it
      uses is one the task needs

## 3. Report a response the model stopped in the middle of

- [x] 3.1 Add a reason for a response that the model did not finish to the reasons a step attempt
      can be rejected for, with a name that says the response was truncated
- [x] 3.2 Read the reason the model stopped in the response handling, and reject a response that
      the model did not finish with that reason and a message that names the reason the model gave
- [x] 3.3 Verify that a truncated response is not reported as a missing payload and not as a
      payload that does not parse, with a unit test on the response handling
- [x] 3.4 Verify that the repair hint the next attempt is given names the truncation, so that the
      next attempt is told to finish its answer

## 4. Keep the task out of the window that bounds the conversation

- [x] 4.1 Keep the messages that carry the task for the whole attempt and out of the window that
      bounds the conversation, and verify with a unit test that the task survives a conversation that
      fills the window
- [x] 4.2 Cut the window so that it does not start with a tool result whose message was cut away,
      and verify with a unit test that no tool result is left without the message that asked for it
- [x] 4.3 Write the whole conversation of an attempt to the chat log, so that a log starts with the
      task, and verify with a unit test that the log holds the whole conversation

## 5. Keep the reasoning of the model in the chat log of a step

- [x] 5.1 Return the reasoning of the model with the response, and write it next to the messages of
      the step in the chat log, and verify with a unit test that the log of a step contains the
      reasoning
- [x] 5.2 Verify that the reasoning is not part of the payload the step produced, with a unit test
      that the payload of a step is unchanged by the reasoning

## 6. Name the tools the model asked for in the bound reason

- [x] 6.1 Count the tool calls the model asked for per tool in the attempt that reaches the bound,
      and name the tools and their counts in the reason of that rejection
- [x] 6.2 Verify with a unit test that the reason of a step that reached the bound names the tool
      the model asked for
- [x] 6.3 Verify that the reason of a step that reached the bound stays bounded in length, so that
      it fits the one line the display reserves for it

## 7. Document and check the change

- [x] 7.1 Document the new failure reason and what it means in the README, and verify that the
      documented command runs as written
- [x] 7.2 Run `mvn verify` and verify that the build, the unit tests and the artefact smoke test
      pass
- [x] 7.3 Run the build file analysis of the 90 module workspace and verify that the step reaches
      its answer without asking for the existence of single files
- [x] 7.4 Run the three other failing tasks of the 90 module workspace and verify that they reach
      their answer as well
