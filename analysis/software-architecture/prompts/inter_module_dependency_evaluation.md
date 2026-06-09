## Current Goal

* The project may have one or more build modules.
* Evaluate the inter-module dependencies across all modules.
* This analysis runs across all modules at once, not per module.

## Facts

{{#with modules.modules}}
The project has {{length}} build modules:

| Module | Path | Root |
|--------|------|------|
{{#each this}}
| {{name}} | `{{path}}` | {{root}} |
{{/each}}

{{/with}}

## Solution Strategy

* Call the **'java_get_inter_module_dependency_report'** tool to retrieve inter-module dependency data.
* The tool analyses all modules and their imports to determine which modules depend on each other.
* The tool returns three distributions:

  1. **Inter-module Ce per module** — efferent coupling (fan-out): how many other modules each module depends on.
     * Each entry: `moduleName → count`
  2. **Inter-module Ca per module** — afferent coupling (fan-in): how many other modules depend on each module.
     * Each entry: `moduleName → count`
  3. **Instability (Ce/(Ce+Ca)*100) per module** — stability metric from 0 to 100.
     * Each entry: `moduleName=value → value`
     * 0 = perfectly stable (nothing depends on it, but many depend on it)
     * 100 = perfectly unstable (depends on many, nothing depends on it)

* The tool also writes a CSV file `InterModuleDependencyMatrix.csv` with the full dependency matrix showing import counts per module pair.

### Evaluation Guidelines

Evaluate the results in relation to common architecture guidelines:

* **Instability interpretation:**
  * Instability > 70: Highly unstable. Should be leaf modules at the top of the dependency hierarchy (e.g., application entry points, UI layer). If a stable module (like core, domain) has high instability, this may indicate an architecture erosion.
  * Instability < 30: Highly stable. Should be foundation modules at the bottom (e.g., core library, utilities, domain model). These are depended upon by many others.
  * Instability 30-70: Balanced zone. Typical for intermediate layers (e.g., services, repositories).

* **Dependency direction violations:**
  * Foundation modules (low Instability) should not depend on application modules (high Instability).
  * This is the "dependency inversion principle" at module scale.
  * E.g., if `core` depends on `web-app`, that is likely an architecture violation.

* **Hub modules (high Ca):**
  * Modules with high Ca (many dependents) act as hubs.
  * If such a module has high Ce as well (high Instability), changes to it will ripple through many downstream modules — this is a red flag.
  * Consider extracting stable interfaces or splitting such modules.

* **Orphan modules:**
  * Modules with both Ce = 0 and Ca = 0 are disconnected from the rest of the system. May be dead code, libraries, or intentionally isolated.

* **Dependency weights:**
  * The CSV matrix shows import counts. High import counts between modules indicate tight coupling at the implementation level.
  * Look for modules that import extensively from a single other module — may indicate missing abstraction boundaries.