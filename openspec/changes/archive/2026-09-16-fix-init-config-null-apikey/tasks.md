## 1. Implement

- [x] 1.1 Edit `ConfigStorer.java` to serialize config with NON_NULL inclusion and verify `apiKey: null` is omitted from output
- [x] 1.2 Add `ConfigStorerTest` round-trip test: save `Config` without apiKey, reload via `ConfigLoader` — verify load succeeds and file contains no `apiKey` entry

## 2. Verify

- [x] 2.1 Run `mvn verify` and verify all tests pass (incl. new round-trip test)
- [x] 2.2 Manual: `workspace init` without `-k` into scratch dir, verify `config.json` has no `apiKey` field, then `analyse -o <first-task>` succeeds

## 3. Wrap-up

- [x] 3.1 Commit change (`bug:` style), mark tasks complete, archive change
