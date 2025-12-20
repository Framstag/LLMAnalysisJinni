## Current Goal

Based on the given distribution of file count and line count per module and file and file type you should 
give an architectural judgment regarding the quality of code based on this distribution.  

## Facts

|Module|Programming Language|File count|lines of code|
|------|--------------------|----------|-------------|
{{#modules.modules~}}
{{#fileStatisticsPerProgrammingLanguage.fileStatistics~}}
|{{../name}}|{{programmingLanguage}}|{{fileCount}}|{{lineCount}}|
{{/fileStatisticsPerProgrammingLanguage.fileStatistics~}}
{{/modules.modules}}
{{noop}}

## Solution strategy

* Evaluate the given distributions and evaluate them based on your knowledge about good architecture patterns
  and code styles.

## Hints

Possible aspects could be:

* Does the distribution of file count and line count fit to an ideal distribution or not?
* Are the averages regarding file size and number of files per module OK?
