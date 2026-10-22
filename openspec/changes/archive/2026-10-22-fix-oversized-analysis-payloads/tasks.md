# Tasks

## 1. Bound the severed edges of a separation

- [x] 1.1 Keep the strongest severed edges of a separation only, and report the number of the severed edges beside them
- [x] 1.2 Report the number of the listed edges and the limit beside the list
- [x] 1.3 Leave the price of the separation unchanged
- [x] 1.4 Cover the bound and the price with tests

## 2. Evaluate the class reference structure per module

- [x] 2.1 Make the task a loop over the modules
- [x] 2.2 Call the tools that work on a single module
- [x] 2.3 Rewrite the prompt for the module of the step
- [x] 2.4 Read the findings of the class reference structure from the modules in the documentation template

## 3. Keep the usage of a step whose model call failed

- [x] 3.1 Carry the usage of the calls that succeeded out of a failed model call
- [x] 3.2 Turn a retriable model call failure into a rejected attempt that carries its usage
- [x] 3.3 Carry the usage of the step out with a failure that ends the step
- [x] 3.4 Record the usage of a step that failed before marking it as failed
- [x] 3.5 Cover the carried usage with tests

## 4. Verify

- [x] 4.1 The build passes
- [x] 4.2 The evaluation of the class reference structure succeeds on a project with many modules
- [x] 4.3 The documentation renders the findings of every module
