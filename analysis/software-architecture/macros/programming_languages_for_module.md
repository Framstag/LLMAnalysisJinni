{{~#if programmingLanguages.programmingLanguages.length~}}
The following programming languages have been identified for this module:

{{#programmingLanguages.programmingLanguages~}}
* Programming language "{{name}}"
{{/programmingLanguages.programmingLanguages~}}
{{~else~}}
No known programming language used in this module!
{{~/if~}}