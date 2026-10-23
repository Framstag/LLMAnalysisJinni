package com.framstag.llmaj.handlebars;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups a list of entries by the value of one of their properties, so that a template can render one section
 * per group without the order of the list deciding which group opens a section.
 *
 * <p>Usage: {@code {{#each (groupBy property="anchorType" source=anchors)}}}. The block then holds the entries
 * of one group in {@code this} and the name of the group in {@code @key}. The groups keep the order in which
 * they appear in the list, so that the rendered order is the order of the data.
 */
public class GroupByHelper implements Helper<Object> {

    @Override
    public Object apply(Object context, Options options) throws IOException {
        Map<String, List<Object>> groups = new LinkedHashMap<>();

        String property = options.hash("property", null);
        Object source = options.hash("source", null);

        if (property == null || !(source instanceof Iterable<?> entries)) {
            return groups;
        }

        for (Object entry : entries) {
            groups.computeIfAbsent(valueOf(entry, property), key -> new ArrayList<>()).add(entry);
        }

        return groups;
    }

    /**
     * @return the value of the property of the entry, or an empty name when the entry does not hold it
     */
    private static String valueOf(Object entry, String property) {
        if (entry instanceof Map<?, ?> map) {
            Object value = map.get(property);

            return value == null ? "" : String.valueOf(value);
        }

        return "";
    }
}
