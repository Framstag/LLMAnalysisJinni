---
name: get-local-changes
description: Returns a list of files that have local uncommitted changes
allowed-tools: bash
---

Return the result of the following shell command in the project root directory:

`git ls-files --others --modified --exclude-standard`