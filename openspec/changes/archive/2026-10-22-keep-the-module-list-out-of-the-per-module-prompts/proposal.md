# Keep the list of all modules out of the prompts of the per-module tasks

## Why

The tasks that analyse one module at a time put the list of every module of the project into their
prompt. The list renders 83 lines, one per module, and it is 7,265 characters long.

The prompt is sent to the model again on every model round of the step, and the list is in it every
time. The list is 12% of everything the analysis sent to the model: 7,933,380 characters, about
1,980,000 tokens, of 68,641,555 characters in total.

The module that is analysed is named in the prompt already, and the number of the modules of the
project is enough for a task that analyses one of them. The tasks that are about the whole project,
the context map and the domain glossary, need the list and keep it.

## What Changes

- A prompt that is about one module states how many modules the project has and names that one
  module. It does not carry the list of all the modules.
- A prompt that is about the whole project keeps the list of all the modules.
- The list of all the modules moves into a macro of its own, so that the two kinds of prompt can
  be told apart.

## Impact

- A new capability `per-module-analysis-prompt` states what a prompt about one module carries.
- The macro `list_of_modules` states the number of the modules instead of listing them.
- A new macro `list_of_all_modules` carries the list, and the prompts of the context map and the
  domain glossary use it.
- The prompts of the build file analysis, the module purpose, the module architecture and the class
  reference structure no longer carry the list.
