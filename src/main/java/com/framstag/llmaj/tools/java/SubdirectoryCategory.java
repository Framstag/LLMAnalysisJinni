package com.framstag.llmaj.tools.java;

public enum SubdirectoryCategory {
    SRC("Src"),
    GEN_SRC("GenSrc"),
    TEST_SRC("TestSrc"),
    TEST_GEN_SRC("TestGenSrc"),
    OBJ("Obj"),
    TEST_OBJ("TestObj");

    private final String value;

    SubdirectoryCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isSrc() {
        return this == SRC || this == GEN_SRC || this == TEST_SRC || this == TEST_GEN_SRC;
    }

    public boolean isObject() {
        return this == OBJ || this == TEST_OBJ;
    }

    public static SubdirectoryCategory fromString(String text) {
        for (SubdirectoryCategory category : SubdirectoryCategory.values()) {
            if (category.value.equalsIgnoreCase(text)) {
                return category;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}