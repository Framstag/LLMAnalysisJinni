## 1. Implement

- [x] 1.1 Add `<parameters>true</parameters>` to `maven-compiler-plugin` configuration in `pom.xml` and verify effective javac args include `-parameters` (check `target/classes` class has `MethodParameters` attribute)
- [x] 1.2 Add unit test: build `ToolService` from `FilesystemTool`, find spec `filesystem_get_all_files_in_dir`, assert parameter name is `path`

## 2. Verify

- [x] 2.1 Run `mvn verify` and verify all tests pass (incl. new parameter-name test)
- [x] 2.2 Live: run one `analyse -o` task on scratch workspace, verify no `Parameter 'arg0'` warnings in log

## 3. Wrap-up

- [x] 3.1 Commit change (`chore:` style), mark tasks complete, archive change
