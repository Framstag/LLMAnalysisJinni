## Context

Config write path: `WorkspaceInitCmd` sets `apiKey` from `--apiKey` option (null when absent) -> `ConfigStorer.save` (Jackson, `ObjectMapperFactory` mapper, default inclusion = ALWAYS) -> `"apiKey": null` in file. Config read path: `ConfigLoader` validates against `config-file.json` (`apiKey` type string, not in required). Null value = validation error on every `analyse`. See `proposal.md` — Why.

## Goals / Non-Goals

**Goals:** Fresh workspaces without API key (Ollama local/private) are schema-valid after init.

**Non-Goals:** No migration of already-broken `config.json` files (user data). No global mapper change. No schema relaxation to nullable (keeps null warts out of files).

## Decisions

**D1: NON_NULL inclusion locally in `ConfigStorer`, not in `ObjectMapperFactory`.**
Rationale: `ObjectMapperFactory` also serializes `analysis.json` (accumulated results, nullable fields may be meaningful) — a global NON_NULL could silently drop fields there. Locally configured mapper in the storer confines the change to the config file.
Alternative considered: schema `{"type": ["string","null"]}` — accepts the broken files but leaves `null` junk and masks the write-path bug.

**D2: Keep `apiKey` optional/omitted when null.**
It is already not in the schema's `required` list, so omission is valid. An empty string would also pass but writes a misleading placeholder.

## Risks / Trade-offs

- [Config fields that are legitimately null get omitted on next save] -> Config has no other optional/required-free nullable fields today; `apiKey` is the only one. If a future optional field appears, omission remains schema-valid as long as it is not `required`.
- [Existing broken workspaces still fail] -> Out of scope: user re-inits or edits config. Noted in proposal.

## Migration Plan

1. Edit `ConfigStorer` (per-field mapper `JsonMapper.builder().serializationInclusion(NON_NULL)`).
2. Add round-trip unit test (save config w/o api key -> reload via `ConfigLoader` succeeds; file contains no `apiKey`).
3. `mvn verify`; manual check: `workspace init` without `-k` on scratch dir -> file has no `apiKey` -> `analyse -o` one task works.

## Open Questions

None.
