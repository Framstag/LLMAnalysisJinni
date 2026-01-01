package com.framstag.llmaj.tools.common;

import java.util.LinkedList;
import java.util.List;

public class Distribution {
    private final String name;

    private List<DistributionEntry> entries = new LinkedList<>();

    public Distribution(String name) {
        this.name = name;
    }

    public void addEntry(String value, Integer count) {
        entries.add(new DistributionEntry(value, count));
    }

    public String getName() {
        return name;
    }

    public List<DistributionEntry> getEntries() {
        return entries;
    }
}
