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

    public static void modifyClassAttributesByCategory(ClassManager classManager, SubdirectoryCategory category) {
        switch (category) {
            case SRC, OBJ -> {
                classManager.setProduction(true);
                classManager.setGenerated(false);
            }

            case GEN_SRC -> {
                classManager.setProduction(true);
                classManager.setGenerated(true);
            }
            case TEST_SRC, TEST_OBJ -> {
                classManager.setProduction(false);
                classManager.setGenerated(false);
            }
            case TEST_GEN_SRC -> {
                classManager.setProduction(false);
                classManager.setGenerated(true);
            }
        }
    }
}
