# Tasks

## 1. The history reader

- [x] 1.1 Add the tool that runs the git executable of the machine over the project directory with the machine
      readable flags and parses the per file commits, the added and removed lines, the dates and the authors;
      verify with a unit test over a repository created in a temporary directory that the counts, the dates and the
      authors of a changed file are correct
- [x] 1.2 Count the pairs of files that changed in the same commit with the number of commits they shared, bounded by
      a stated limit on the number of files a commit may touch; verify with a unit test over a repository with two
      files that always change together and a commit that touches many files
- [x] 1.3 Bound the counted history by a number of commits, name the window in the result and state what the window
      leaves out; verify with a unit test that the result names the window and that a file changed before the window
      holds no row

## 2. The aggregation and the absence

- [x] 2.1 Aggregate the counted history per build module, holding the changed file count, the churn, the date of the
      last change and the number of authors; verify with a unit test that a module of the analysis state is aggregated
      from the files of its directories
- [x] 2.2 Report the authors of a module with the number of commits each of them made; verify with a unit test that
      the author with most commits is named with its count
- [x] 2.3 Report an absent history with the reason for each of the three cases, a project that is not a repository, a
      machine without the git executable and a truncated history; verify with a unit test per case that the result
      names the case and holds no counts

## 3. The reports

- [x] 3.1 Write the counted history as project level CSV reports, one file for the files, one for the modules and one
      for the pairs of files that change together; verify with a unit test that the three files exist with one row per
      fact and that the columns of the views are not merged
- [x] 3.2 Register the tool in the tool factory under a name of its own family; verify that the tool appears in the
      tool list of the engine and that its parameters are named

## 4. Integration checks

- [x] 4.1 Run `mvn verify` and confirm the build passes and that no existing tool changed its result; verify with the
      tool tests of the engine
- [x] 4.2 Run the specification validation of the project and confirm that the change validates; verify with
      `openspec validate add-version-history-analysis --strict`

## 5. Review follow-up

- [x] 5.1 Sum the lines of a path that appears twice in one commit instead of keeping only the first entry, and count
      the commit once; verify with `HistoryToolTest` that both entries of the path are summed
- [x] 5.2 Cover the project directory being a subdirectory of the repository, and the more specific module path
      winning for a nested module; verify with `HistoryToolTest` and `ModuleHistoryAggregationTest`

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
