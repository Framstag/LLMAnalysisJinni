# Design

## What the analysis reads today

The prompt of `ModuleBuildfileAnalysis` says:

> * The build file of the current module is the file of the build system of the project, at the path
>   of the current module.
> * Read that build file.
> * Use the 'filesystem_get_matching_files_in_dir_recursively' tool to find the build files of the
>   directories below the current module, so that the build files of the submodules are known as
>   well.
> * Analyze the quality of the build files you read.

The third line is the problem. The tool answers with the build files of the whole subtree, and the
model then reads them, one by one.

## What the reads cost

The tool calls of the build file task in the analysis of the Maven project:

| Call | Count |
|---|---|
| `fileio_read_file` | 515 |
| `filesystem_get_matching_files_in_dir_recursively` | 178 |
| `filesystem_file_exists` | 30 |

Of the 515 reads, 111 are of a build file at the top level of a module and 404 are of a build file
of a module below:

```
   44x  pom.xml
   30x  its/core-it-support/pom.xml
   28x  its/pom.xml
   28x  its/core-it-support/core-it-plugins/pom.xml
   16x  its/core-it-support/core-it-plugins/maven-it-plugin-core-stubs/pom.xml
```

Those reads are what makes the step expensive. The step of the root module sent 1,649,584
characters, the step of index 22 sent 2,970,917 characters over 19 model rounds, and the median step
of the task sent 239,401 characters. The task sent 33,677,177 characters in total, 47% of the whole
analysis, and 8,165,229 tokens.

## What changes

- The prompt drops the line that asks for the build files of the directories below the module, and
  says instead that the build file of the module is the one to read.
- The task drops `filesystem_get_matching_files_in_dir_recursively` from its whitelist.

The modules below are named by the module list, and each of them has its own step that reads its
own build file. The build file of a module is the one of the build system of the project at the
path of the module, and the analysis is given that name.

## Decisions

- The tool is dropped from the whitelist and not only discouraged in the prompt. A tool the model
  may use is a tool the model will use, and the whole point of the change is that the reads of the
  build files of the other modules stop.
- The tool itself stays. Other tasks use it, and it is the right tool for them.
- The prompt does not tell the model that the build files of the modules below are known. The
  module list is in the prompt, and saying it again costs tokens on every round of every step.
- The build file of the module is still named by the analysis. Nothing about the naming changes, so
  the model reads the file it was told to read.
