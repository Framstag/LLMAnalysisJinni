package com.framstag.llmaj.tools.java;

import java.nio.file.Path;
import java.util.List;

public class ParserHelper {
    /**
     * The two compilation unit level types of the language. {@code javac} writes a class file for an annotated
     * {@code package-info.java} and for a {@code module-info.java}. Neither declares a class, a field or a
     * method of the module — the descriptor carries the annotations of its package or the description of its
     * module — so neither may become a build unit of the module report.
     */
    private static final List<String> COMPILATION_UNIT_LEVEL_TYPES = List.of("package-info", "module-info");

    /**
     * @return true when the qualified name is the one {@code javac} gives the class file of a
     * {@code package-info.java} or a {@code module-info.java}, which declares no class of the module
     */
    public static boolean isPackageOrModuleInfo(String qualifiedName) {
        if (qualifiedName == null) {
            return false;
        }

        int lastDot = qualifiedName.lastIndexOf('.');

        return COMPILATION_UNIT_LEVEL_TYPES.contains(
                lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1));
    }

    public static SubdirectoryCategory getCategoryOfFile(List<SpecialSubdirectory> specialSubdirectories,
                                                          Path file, SubdirectoryCategory defaultValue) {

        for (SpecialSubdirectory directory : specialSubdirectories) {
            if (file.startsWith(directory.getPath())) {
                return directory.getCategoryId();
            }
        }

        return defaultValue;
    }

    public static void modifyClassAttributesByCategory(BuildUnitManager buildUnitManager, SubdirectoryCategory category) {
        switch (category) {
            case SRC, OBJ -> {
                buildUnitManager.setProduction(true);
                buildUnitManager.setGenerated(false);
            }

            case GEN_SRC -> {
                buildUnitManager.setProduction(true);
                buildUnitManager.setGenerated(true);
            }
            case TEST_SRC, TEST_OBJ -> {
                buildUnitManager.setProduction(false);
                buildUnitManager.setGenerated(false);
            }
            case TEST_GEN_SRC -> {
                buildUnitManager.setProduction(false);
                buildUnitManager.setGenerated(true);
            }
        }
    }
}
