# Tasks

## 1. The prompt reads one build file

- [x] 1.1 Drop the step that finds the build files of the directories below the module
- [x] 1.2 Say that the build file of the module is the one to read
- [x] 1.3 Keep the naming of the build file of the module as it is

## 2. The task drops the listing tool

- [x] 2.1 Drop `filesystem_get_matching_files_in_dir_recursively` from the whitelist of the task
- [x] 2.2 Leave the tool itself available for the other tasks

## 3. Verify

- [x] 3.1 The build passes
- [x] 3.2 The change validates against the specification
- [x] 3.3 The prompt of the task holds no step that reads the build files of the other modules
