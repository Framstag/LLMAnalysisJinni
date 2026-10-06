# Spec Delta

## Purpose

Defines which conditions of an analysis tool are reported as errors of the engine and which are reported
as conditions the model or the reader can act on, so that the engine log carries the diagnostics that
need a human.

## ADDED Requirements

### Requirement: A condition the model can correct is not reported as an error

A tool condition the model can act on - a path that does not exist, arguments that have to be corrected -
SHALL be returned to the model as the tool result, and SHALL NOT be logged at `ERROR`. Such a condition
SHALL NOT write a stack trace into the log, and a repetition of the same condition SHALL be logged at
most once per run above `DEBUG`.

#### Scenario: A tool call names a file that does not exist
- **WHEN** a tool call names a path that does not exist
- **THEN** the tool result SHALL name the path and the condition
- **AND** the model SHALL be able to answer the step
- **AND** the engine log SHALL NOT contain an `ERROR` record with a stack trace for that condition

#### Scenario: The same condition occurs many times
- **WHEN** the same condition occurs a hundred times in one run
- **THEN** at most one record per run SHALL be emitted for it above `DEBUG`
- **AND** the tool result of each call SHALL still name the condition

#### Scenario: A defect of the tool stays an error
- **WHEN** a tool fails because of a defect of its own rather than an input the model provided
- **THEN** the failure SHALL be reported as an error

### Requirement: Unparsable files are reported per module, not per file

A source or class file that the Java parser cannot parse SHALL NOT produce one `ERROR` record with a
stack trace per file. The files a module could not parse SHALL be counted and reported at most once per
module at a level below `ERROR`, the detail per file SHALL be available at `DEBUG`, and the count SHALL
be visible so that a reader can see that a module was analysed without some of its files. Which files a
module report contains SHALL NOT change because of how the failures are reported.

#### Scenario: A tree with many unparsable files
- **WHEN** a scanned module contains many files that cannot be parsed
- **THEN** the engine log SHALL contain at most one record for that module naming the number of files
- **AND** the engine log SHALL NOT contain one `ERROR` record with a stack trace per file
- **AND** the per-file detail SHALL be available at `DEBUG`

#### Scenario: The parser results are unchanged
- **WHEN** a file inside a module cannot be parsed
- **THEN** the file SHALL stay out of the module's report, as it does today
- **AND** the metrics of the module SHALL be computed from the files that could be parsed
- **AND** the contents of a module report whose files could all be parsed SHALL NOT change

#### Scenario: The count remains visible to a reader
- **WHEN** a module's report is produced with unparsable files
- **THEN** the number of unparsable files SHALL be visible in the module's report or in the run report
- **AND** it SHALL NOT be visible only at `DEBUG`
