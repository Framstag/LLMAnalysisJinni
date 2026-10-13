# Tasks

## 1. Template

- [x] 1.1 Emit the list of diagrams that were not drawn with the blank line the paragraph needs, one bullet per
      line and without a blank line between the items, and drop the whitespace trimming that removed the blank
      line (`Documentation.adoc.hbs`, the `notDrawn` block)
- [x] 1.2 Confirm the shape occurs nowhere else in the documentation templates: of the 47 trimmed block tags,
      the `notDrawn` block was the only one whose body opens with a literal bullet

## 2. Tests

- [x] 2.1 `theNotDrawnListIsSeparatedFromItsParagraph`: render a fixture whose module used up its diagram budget
      and require the blank line after `Not drawn:`, one bullet per omission, and the general list invariant
- [x] 2.2 `noBulletIsGluedToAParagraph`: the same invariant over the fixture report, so any list of the template
      that loses its separation fails the build
- [x] 2.3 Prove the test has teeth: with the trimming restored, `theNotDrawnListIsSeparatedFromItsParagraph`
      fails with "the list needs a blank line after the paragraph, otherwise its markers are printed as text";
      with the fix it passes
- [x] 2.4 `mvn verify`: 393 unit tests and 6 smoke tests, BUILD SUCCESS

## 3. Verification on the real document

- [x] 3.1 Regenerate the Maven document and confirm the byte shape: `Not drawn:` followed by an empty line and
      then the bullets
- [x] 3.2 Scan the regenerated document for bullets attached to a paragraph: 0 in the `Not drawn:` sections,
      where the check found 33 before
