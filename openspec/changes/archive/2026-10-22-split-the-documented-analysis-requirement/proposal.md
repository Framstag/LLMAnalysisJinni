# Proposal

## Why

The requirement of the analysis reference that holds the documented set of analyses has grown with every analysis the
report renders, and it now exceeds the length a requirement may have. The strict validation of the specifications
fails on it, and the CI gate runs the strict validation, so the project cannot be verified with the requirement in
its present shape. The enumeration is not a behaviour of its own: it is a list, and the behaviour of the requirement
is that every analysis of the list has exactly one entry.

## What Changes

- Move the enumeration of the documented analyses out of the text of the requirement and into a scenario of it, so
  that the requirement states one behaviour and the list of the analyses is where a list belongs.
- Keep the requirement, its name and its scenarios whole, and keep the enumeration complete.

## Capabilities

### Modified Capabilities

- `analysis-reference`: the requirement that holds the documented set of analyses states the behaviour in short
  form, and the enumeration of the analyses moves into a scenario of that requirement.

## Impact

- `openspec/specs/analysis-reference/spec.md`: the requirement is shortened, the enumeration moves into a scenario,
  and the other requirements of the capability are untouched.
- No code changes. The requirement describes the reference document, and the reference document and its test are
  unchanged.
