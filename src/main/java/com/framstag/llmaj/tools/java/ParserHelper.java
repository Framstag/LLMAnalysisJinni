package com.framstag.llmaj.tools.java;

import java.nio.file.Path;
import java.util.List;

public class ParserHelper {
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
