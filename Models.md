# Usage of models

## Overview

In the following a table of models tested using Ollama together with a
first judgement regarding their usability in this context (Column "T" signals "has tool support"):

| Model                                           | T | Judgment                             |
|-------------------------------------------------|---|--------------------------------------|
| llama3.2:3b                                     | x | Works, but quality is poor           |
| llama3.1:8b                                     | x | Hallucinates quickly                 |
| mistral:latest                                  | - |                                      |
| mistral-nemo:12b                                | x | Slow, does prefix JSON response      |
| gemma3n:latest                                  | - |                                      |
| gemma3:4b                                       | - |                                      |
| codellama:latest                                | - |                                      |
| qwen3:8b                                        | x | Good reasoning, slow , too creative  |
| qwen3:4b                                        | x | Bigger context, but too creative     |
| devstral:latest                                 | x | Good enough                          |
| codeqwen:latest                                 | - |                                      |
| qwen2.5-coder:3b                                | - |                                      |
| qwen2.5:7b                                      | x | OK, but small context                |
| qwen3:1.7b                                      | x | Too much trial, not enough reasoning |
| nemotron-mini:4b                                | - |                                      |
| phi4-mini:latest                                | - |                                      |
| granite3.3:8b                                   | - |                                      |
| granite3-dense:8b                               | - |                                      |
| deepseek-r1:latest                              | - |                                      |
| fl0id/teuken-7b-instruct-commercial-v0.4:latest | - |                                      |
| cogito:8b                                       | ? | untested                             |
| hermes3:8b                                      | ? | untested                             |
| gpt-oss:20b                                     | x |                                      |

## Current suggestion

We initially mostly used qwen2.5:7b but are not always happy with the result, possible due to the small context.

We are also experimenting with qwen3:4b, which has a bigger context, but does strange analysis of the sources on its own.

We have fine result with gpt-oss:20b, but execution is slower and the model bigger.
However, we do not se hallucination or creative solution strategies like unforced tool execution or not calling tool where appropriate like other bigger models o.