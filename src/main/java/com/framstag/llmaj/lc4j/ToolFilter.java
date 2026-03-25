package com.framstag.llmaj.lc4j;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.ArrayList;
import java.util.List;

public class ToolFilter {
    private final List<String> whitelist;
    private final List<String> blacklist;

    public ToolFilter(List<String> whitelist,
                      List<String> blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    List<ToolSpecification> filter(List<ToolSpecification> specifications)
    {
        if (whitelist.isEmpty() && blacklist.isEmpty()) {
            return specifications;
        }

        List<ToolSpecification> result = new ArrayList<>(specifications.size());

        for (ToolSpecification specification : specifications) {
            if (!whitelist.isEmpty()) {
                for (String pattern : whitelist) {
                    if (specification.name().matches(pattern)) {
                        result.add(specification);
                        break;
                    }
                }
            }

            if (!blacklist.isEmpty()) {
                boolean match = false;
                for (String pattern : blacklist) {
                    if (specification.name().matches(pattern)) {
                        match = true;
                        break;
                    }
                }

                if (!match) {
                    result.add(specification);
                }
            }
        }

        return result;
    }
}
