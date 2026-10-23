package com.framstag.llmaj.tools.java;

/**
 * One class of the project with the two figures of the hotspot: how often its file changed and how complex its
 * methods are.
 *
 * <p>The change count, the added and the removed lines are the counted history of the file the class was
 * declared in. The complexity and the method count are the counted metrics of the raw module report, taken
 * from it as they stand.
 *
 * <p>The rank is the position of the class among the classes of this project that changed. A class whose file
 * never changed holds no row at all, because the join needs both figures.
 */
public record Hotspot(String className,
                      String module,
                      String sourceFile,
                      int changeCount,
                      int linesAdded,
                      int linesRemoved,
                      int complexity,
                      int methodCount,
                      int rank) {
}
