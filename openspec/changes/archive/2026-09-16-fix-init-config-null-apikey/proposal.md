## Why

`workspace init` without `--apiKey` writes `"apiKey": null` into `config.json`. The config schema (`src/main/resources/schema/config-file.json`) types `apiKey` as string, so the null value fails validation on the next `analyse` (`ERROR: 5,14: null gefunden, string erwartet`). The standard Ollama-local workflow (see AGENTS.md init example — no `-k` flag) therefore produces a workspace that can never start. Verified pre-existing: `json-schema-validator` 3.0.5 and 3.0.7 reject the identical config (probed), so this is not caused by the 2026-09 dependency update.

## What Changes

- `ConfigStorer.save(...)`: serialize with `NON_NULL` inclusion so optional fields with null values (`apiKey`) are omitted from `config.json` instead of written as `null`. `apiKey` is not in the schema's `required` list, so an omitted key stays valid.
- Add unit test: round-trip save/load of a `Config` without api key must succeed (schema-valid file).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Config file writing/validation internals change; no analysis capability behavior. Explicit opt-out via `skip_specs: true`.

## Impact

- `ConfigStorer.java` — isolated mapper with `NON_NULL` (does not touch `ObjectMapperFactory` global mapper, so `analysis.json`/`state.json` serialization is unaffected).
- Existing workspace `config.json` files that already contain `"apiKey": null` stay broken until edited or re-initialized (out of scope; files are user data, not migrated).
- Verification: `mvn verify` + manual `workspace init` without `-k` then `analyse -o` succeeds.
