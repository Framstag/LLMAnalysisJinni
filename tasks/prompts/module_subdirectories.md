## Current Goal

* To be able to categorize code analyze results on file level we want to classify files by their containing subdirectory.

## Facts

The following build systems have been identified:

{{#build.buildsystems~}}
* Build System: '{{name}}', variant: '{{variant}}'
{{/build.buildsystems}}

The following modules have been identified:

{{#modules.modules~}}
* Module "{{name}}" in directory "{{path}}"
{{/modules.modules}}

Regarding the now to be analyzed module:

{{#with (lookup modules.modules loopIndex)}}
The current module to analyze is named: "{{name}}"
The path of this build module is: "{{path}}"
The current module is a root module: {{root}}

The following programming languages have been identified for this module:

{{#programmingLanguages.programmingLanguages~}}
* Programming language "{{name}}"
  {{/programmingLanguages.programmingLanguages}}
  {{/with}}

{{> facts/programming_language_wildcards.md}}

You have to identify none to multiple relevant subdirectories for the following categories:

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

* For each programming language used, scan the files in the module reading source and build product files.
* Use only calls of the 'GetMatchingFilesInDirRecursively' tool for this task with appropriate wildcards for each src or build artifact.
* Cluster the results to one or more subdirectories.
* Do not expect a specific directory structure in the calls to 'GetMatchingFilesInDirRecursively', though.
  So always scan for files with the module root as first parameter of 'GetMatchingFilesInDirRecursively'.
  A possible call thus could be 'GetMatchingFilesInDirRecursively("",["*.java])'.
* Assign one of the given category ids to each subdirectory found. 

## Hints

* A relevant subdirectory is this context is a directory that bundles a number of files in the same category.
  It bundles for example all production code possibly modularized into multiple packages.
* Do not use path expression in the wilds card parameter of 'GetMatchingFilesInDirRecursively'.
* IN ANY CASE DO NOT USE the 'GetAllFilesInDirRecursively' tool, since it will not perform for modules with many files.
