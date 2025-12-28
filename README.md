# README

## About

This is a small experiment to find out how we can use LLMs for
big code analysis tasks, where just dropping all code into the LLM and calling a (huge) number of manual prompts is not feasible because of context size limits and context quality issues with much data.

General assumption is, that switching from a big prompt an all data in to an iterative approach with small consecutive prompts should be more successfully.

The application implements the following strategies to handle above issues:

* Automate prompt calling using a scriptable execution engine
* Enforce JSON response in all cases, using custom managed result object JSON schemas as input to the LLM.
* Explicitly build an "over all" state/context in form of a JSON structure (in memory and on disk).
* Use a template engine to enrich prompts with dynamic content from this state, thus building minimal prompts as small and selective as possible.
* Use template inclusion to centralize common facts for multiple prompts.
* Allow looping over substructures of the context state, thus allow iterations over findings (like the list of identified source modules).
* Implement dependency handling between prompt calls to allow the engine to always call prompts at times, where depending on information was successfully gathered.
* Store execution state to be able to recover from execution failures.
* Use a template engine to postprocess the gathered state and generate a summarizing documentation again.  
* Make use of embedded MCP tools, to modularize analysis capabilities and make the engine extendable. 
 
The current code implements prompts, result objects and documentation templates for executing a simple software architecture analysis.

However, the engine could be used for other purposes, too

## License

The software and the prompts are licensed under GPL.

## Setup

We use [Mise](https://mise.jdx.dev/) for a definition of used developer tools versions. See the `mise.toml` for details.

## Build

For simpler test setup we use the repository itself for testing.

This requires though, that for the tests to work you need a successful build, because it creates some artefacts (the SBOM under "target") necessary for testing:

So first build the software without tests:

```
mvn verify -DskipTests
```

and then eagain with tests:

```
mvn verify
```

## Usage

A typical call to analyze a given project would be:

```
analyse --modelUrl http://<ollama-url> --model "gpt-oss:20b" <project to analyze> <task directory> <workspace directory>
```

For the spring-petclinic (places on the same director level as this tool) this could be:

```
analyse --modelUrl http://<ollama-url>> --model "gpt-oss:20b" -j=false --log-request=false --log-response=false -- ../spring-petclinic tasks workspaces/spring-petclinic
```

In this case all active tasks in the configuration file are
call one after another and an `analysis.json` (containing the analysis result) and a `state.json` (containing tasks execution information) is created in the 
workspace directory.

The given LLM is used by calling the given ollama instance.

For generating a documentation of the analysis a possible command line could be:

```
document tasks workspaces/spring-petclinic
```

## Implementation

The implementation makes use of the following frameworks and libraries:

* Langchain4j for accessing models in chat mode
* Handlebars for templating of prompts and result document
* Jackson for (de)serializing JSON and YAML
* Picoli for CLI parsing
* JavParser for parsing Java Files
* FastCSV for writing CSV files