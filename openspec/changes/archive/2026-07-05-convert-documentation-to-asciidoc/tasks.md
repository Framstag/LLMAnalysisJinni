## 1. Template Conversion

- [x] 1.1 Rename `Documentation.md.hbs` to `Documentation.adoc.hbs`
- [x] 1.2 Rewrite headings: `#` → `= `, `##` → `== `, `###` → `=== `, `####` → `==== `
- [x] 1.3 Rewrite pipe tables to Asciidoc `|===` delimited tables
- [x] 1.4 Rewrite indented code blocks to `----` fenced blocks
- [x] 1.5 Rewrite `>>` blockquotes to `____` blockquotes
- [x] 1.6 Replace `[TOC]` with `:toc: left` document header
- [x] 1.7 Add `= Result of Architecture Analysis` document title line

## 2. Java Changes

- [x] 2.1 Change default `--document-postfix` from `".md"` to `".adoc"` in `DocumentCmd.java`
- [x] 2.2 Change compiled template name from `"Documentation.md"` to `"Documentation.adoc"` in `DocumentCmd.java`

## 3. Test Update

- [x] 3.1 Update `DocumentationTemplateTest.java` to assert Asciidoc table syntax (`|===`)
- [x] 3.2 Update `DocumentationTemplateTest.java` to assert Asciidoc heading syntax (`=== `)
- [x] 3.3 Update `DocumentationTemplateTest.java` to compile `Documentation.adoc` template

## 4. Verify

- [x] 4.1 Run `mvn verify -DskipTests` to confirm compilation
- [x] 4.2 Run `mvn verify` to confirm tests pass
