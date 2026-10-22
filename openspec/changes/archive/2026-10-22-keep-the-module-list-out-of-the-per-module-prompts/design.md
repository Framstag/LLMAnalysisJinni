# Design

## What the list costs

The list of the modules renders one line per module:

```
* Module "maven-builder-support" in directory "compat/maven-builder-support"
```

83 modules, 7,265 characters, in the prompt of every step of every task that analyses one module
at a time.

The prompt is sent again on every model round of the step, and the list is in it every time. The
measurement over the whole analysis of the Maven project:

| Part | Characters | Share |
|---|---|---|
| Everything sent to the model | 68,641,555 | 100% |
| The fixed prompt, times the rounds | 21,828,696 | 32% |
| **The list of the modules, times the rounds** | **7,933,380** | **12%** |

That is about 1,980,000 tokens.

## Which prompts carry the list

| Prompt | Kind | Keeps the list |
|---|---|---|
| `module_analysis_buildfiles.md` | one module | no |
| `module_purpose.md` | one module | no |
| `module_architecture.md` | one module | no |
| `class_dependency_graph_evaluation_all.md` | one module | no |
| `context_map_evaluation.md` | whole project | yes |
| `domain_glossary.md` | whole project | yes |

## The two macros

`list_of_modules` becomes the count:

```
The following {{modules.modules.length}} modules have been identified:
```

`list_of_all_modules` holds the list:

```
The following {{modules.modules.length}} modules have been identified:

{{#modules.modules~}}
* Module "{{name}}" in directory "{{path}}"
{{/modules.modules~}}
```

The per-module prompts keep `list_of_modules`, the prompts about the whole project switch to
`list_of_all_modules`.

## Decisions

- The list is not shortened, it is moved. A shorter list would still name every module, and the
  cost of the list is that it names every module.
- The number of the modules stays in the per-module prompts. A task that analyses one of many
  modules has to know that the project has more than one, otherwise every module claims to be the
  project.
- The module that the step is about is named by the macro `current_loop_module`, which the
  per-module prompts already use. Nothing changes about it.
- The build file of a module names the modules below it, so the analysis of a build file learns
  the modules below it from the build file it reads. The module list is not needed for that.
- The prompts about the whole project keep the list. The context map and the domain glossary
  reason about the modules together, and the list is their input.
