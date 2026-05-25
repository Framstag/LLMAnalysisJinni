## Why

The analysis pipeline has 14 Java analysis tools but gaps remain in lightweight, high-signal metrics that can be extracted from data already captured during module analysis. Three analyses — annotation density, package-level tangles, and import diversity — require zero parser changes and expose real architectural smells: framework lock-in, broken modularity, and dependency hygiene. Adding them fills natural blind spots without touching the parser layer.

## What Changes

- **New tool + task**: `java_get_annotation_report` — reports annotation type distribution and @Override ratio per module
- **New tool + task**: `java_get_package_tangle_report` — detects cycles between packages (not just classes) using same import graph
- **New tool + task**: `java_get_import_diversity_report` — reports unique external packages, framework vs application import ratio

Each follows the existing pattern: `@Tool` method in `JavaTool.java`, evaluation task in `tasks.yaml`, prompt template, JSON schema, documentation section.

Explicitly excluded from this change (noted during exploration):
- **Class weight distribution**: overlaps heavily with existing method_count + field_visibility tools
- **Anonymous class density**: low signal-to-noise ratio, narrow applicability

## Capabilities

### New Capabilities

- `annotation-density`: Tool + task for annotation type distribution and @Override ratio per module
- `package-tangle-detection`: Tool + task for package-level cycle detection using import graph
- `import-diversity`: Tool + task for external package import diversity and framework dependency ratio

### Modified Capabilities

*(none — no existing specs change)*

## Impact

- **Java code**: 3 new `@Tool` methods in `JavaTool.java` (each ~40-80 lines, following existing patterns)
- **Analysis pipeline**: 3 new tasks in `tasks.yaml`, 3 prompts, 3 JSON schemas
- **Documentation**: 3 new sections in `Documentation.md.hbs`
- **Zero** changes to parser layer, data model, or build system
- **Zero** new dependencies