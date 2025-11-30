# README

## About

This is a small experiment to find out how we can use LLMs for
big code analysis tasks, where just dropping all code into the LLM is not feasible because of context size limits and context quality issues with much data.

Assumption is, that switching from a big prompt an all data in to an iterative approach with small consecutive prompts should be more successfully.

Also part of the solution is using JSON as result type together with explicit cross-prompt context control.

## License

The software and the prompts are licensed under GPL.

## Setup

We use [Mise](https://mise.jdx.dev/) for a definition of used developer tools versions. Seethe `mise.toml` for details.

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

A typical call would be:

```
analyse --modelUrl http://<ollama-url> --model "gpt-oss:20b" <project to analyze> <task directory> <workspace directory>
```

In this case all active tasks in the configuration file are
call one after another and an `analysis.json` (containing the analysis result) and a `state.json` (containing tasks execution information) is created in the 
workspace directory.

The given LLM is used by calling the given ollama instance.
