## Current Goal

* To be able to categorize code analyze results on the file level, we want to classify files by their containing subdirectory.

## Facts

{{> macros/list_of_build_systems.md}}
{{> macros/list_of_modules.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
{{> facts/programming_language_wildcards.md}}
{{/if}}

{{~#if programmingLanguages.programmingLanguages.length~}}You have to identify relevant subdirectories for the following categories:

| Id         | Description                                            |
|------------|--------------------------------------------------------|
| Src        | Manually written source code that is used in poduction |
| GenSrc     | Manually written source code that is used in poduction |
| TestSrc    | Source code that was generated to test the software    |
| TestGenSrc | Source code that was generated to test the software    |
| Obj        | Compiled object code that is used in poduction         |
| TestObj    | Compiled object code for tests                         |

For Maven and Java typical subdirectories (relative to the module) are:

| Id         | Category                      |
|------------|-------------------------------|
| Src        | src/main/java                 |
| GenSrc     | target/generated-sources      |
| TestSrc    | src/test/java                 |
| TestGenSrc | target/generated-test-sources |
| Obj        | target/classes                |
| TestObj    | target/test-classes           |

## Solution strategy

* For each programming language used in the current module, scan the files in the module reading source and build product files.
* If no known programming language is used in the current module, skip the analysis and return an empty list of directories.
* Use only calls of the 'FileCountPerFileTypeAndDirectory' tool for this task with appropriate wildcards for each src or build artifact.
* Cluster the results to one or more subdirectories.
* Do not expect a specific directory structure in the calls to 'FileCountPerFileTypeAndDirectory', though.
  So always scan for files with the module root as the first parameter of 'FileCountPerFileTypeAndDirectory'.
  A possible call thus could be 'FileCountPerFileTypeAndDirectory("",["*.java])'.
* Assign one of the given category ids to each subdirectory found.
* Return the path *relative* to the module root for each directory identified.

## Hints

* A relevant subdirectory in this context is a directory that bundles a number of files in the same category.
  It bundles, for example, all production code possibly modularized into multiple packages.
* It does not contain directories that replicate language-specific package structures.
* Do not use path expression in the wildcards parameter of 'FileCountPerFileTypeAndDirectory'.
* To keep the search fast, look only for wildcards for programming languages that are actually used in the to be analyzed module.
* Return the path relative to the module root.
{{else}}
## Solution Strategy

* Since no supported programming languages are found, just return an empty array without further actions.
{{/if}}
{{/with}}

