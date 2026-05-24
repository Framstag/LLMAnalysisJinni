## Context

Nesting depth measures the maximum level of nested control structures inside a method body. It is complementary to cyclomatic complexity (CC): CC counts decision paths, nesting depth measures scoping depth. A flat switch with 12 cases (CC=12, depth=1) is readable; a method with if-in-for-in-while (CC=8, depth=5) is spaghetti.

Current method complexity in `java-method-complexity-analysis` covers CC, parameter count, and lines of code. Nesting depth adds a readability dimension that LLM prompts can cross-reference with CC to distinguish these cases.

## Goals / Non-Goals

**Goals:**
- Add `nestingDepth` int field to `Method.java` (default 0)
- Populate from JavaParser AST in `JavaFileParser.java`
- Add 1 `@Tool` in `JavaTool.java` returning nesting depth distribution per module
- Add 1 task definition in `tasks.yaml` with prompt
- Maintain backward compatibility

**Non-Goals:**
- Nesting depth from `.class` files — bytecode has no scope structure
- Average nesting depth — max depth is the standard readability metric
- Per-method-report — only distribution-level report needed for LLM evaluation

## Decisions

### Decision 1: Max nesting depth computed in parser, not @Tool

**Chosen**: Compute in `JavaFileParser.java` during parsing. Nesting depth is a per-method scalar (like linesOfCode) that doesn't change. Computing at parse time keeps @Tool methods simple and avoids redundant AST work.

Pseudo-code for parsing:

```java
method.setNestingDepth(computeMaxDepth(methodDeclaration.getBody()));
```

Recursive helper:

```java
private int computeMaxDepth(Node node) {
    int maxChildDepth = 0;
    for (Node child : node.getChildNodes()) {
        if (child instanceof IfStmt || child instanceof ForStmt
            || child instanceof WhileStmt || child instanceof DoStmt
            || child instanceof SwitchStmt || child instanceof TryStmt
            || child instanceof ForEachStmt) {
            maxChildDepth = Math.max(maxChildDepth,
                1 + computeMaxDepth(child));
        } else {
            maxChildDepth = Math.max(maxChildDepth,
                computeMaxDepth(child));
        }
    }
    return maxChildDepth;
}
```

### Decision 2: Control structures counted as nesting

**Chosen**: `if`, `for`, `while`, `do-while`, `switch`, `try`, `for-each`. These are the standard control flow constructs that increase scoping depth. Blocks `{}` alone don't count — they reflect formatting, not logic complexity.

### Decision 3: ClassFileParser leaves nestingDepth at 0

**Chosen**: Bytecode has no source-level scoping. Methods parsed from `.class` files keep the default 0. The @Tool distribution includes these, but they're indistinguishable from flat methods. This matches the existing pattern where `linesOfCode` is null for class-file-only methods.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Deep ternary chains (a ? b : c ? d : e) not counted | Ternary doesn't increase scoping depth like if/for. LLM can still catch via CC. |
| Lambda bodies not counted | Lambda nesting is shallow by nature. If needed later, add `LambdaExpr` check. |
| Overcounting: synchronized blocks, annotations | Only standard control structures counted — no false positives. |

## Open Questions

*(None — design is straightforward)*