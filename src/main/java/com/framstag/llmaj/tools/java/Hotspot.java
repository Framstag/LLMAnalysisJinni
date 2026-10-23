package com.framstag.llmaj.tools.java;

import java.util.List;

/**
 * One class of the project with the two figures of the hotspot: how often its file changed and how complex its
 * methods are.
 *
 * <p>The change count, the added and the removed lines are the counted history of the file the class was
 * declared in. The complexity and the method count are the counted metrics of the raw module report, taken
 * from it as they stand.
 *
 * <p>An aggregator module and its inner modules report the same classes, so a class can appear in the report
 * of several modules. The row holds one entry per qualified class name and names the modules the class appears
 * in, so the rank counts the places the class holds and not the copies the reports hold. The complexity is the
 * highest complexity of the appearances, because the reports of the same class can carry slightly different
 * counted figures and the reader should see the strongest one.
 *
 * <p>The rank is the position of the class among the classes of this project that changed. A class whose file
 * never changed holds no row at all, because the join needs both figures.
 */
public record Hotspot(String className,
                      List<String> modules,
                      String sourceFile,
                      int changeCount,
                      int linesAdded,
                      int linesRemoved,
                      int complexity,
                      int methodCount,
                      int rank) {
}
