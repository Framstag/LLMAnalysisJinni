## Current Goal

Based on the given distribution of file count and line count per module and file and file type you should 
give an architectural judgment regarding the quality of code based on this distribution.  

## Facts


The following metrics have been measured regarding the detected modules. Each module has its own subchapter.

[# th:each="module : ${state.modules.modules}" th:remove="tag"]

### Module "[(${module.name})]"

[# th:each="statistics : ${module.fileStatisticsPerProgrammingLanguage.fileStatistics}" th:remove="tag"]
Programming language: "[(${statistics.programmingLanguage})]"

* File wildcard: "[(${statistics.wildcard})]"
* File count in module: [(${statistics.fileCount})]
* Line count of all files in module: [(${statistics.lineCount})]
[/]
[/]

## Solution strategy

* Evaluate the given distributions and evaluate them based on your knowledge about good architecture patterns
  and code styles.

## Hints

Possible aspects could be:

* Does the distribution of file count and line count fit to an ideal distribution or not?
* Are the averages regarding file size and number of files per module OK?
