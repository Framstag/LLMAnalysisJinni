package com.framstag.llmaj.tools.common;

public class DistributionEntry {
    private final String value;
    private final Integer count;

    public DistributionEntry(String value, Integer count) {
        this.value = value;
        this.count = count;
    }

    public String getValue() {
        return value;
    }

    public Integer getCount() {
        return count;
    }
}
