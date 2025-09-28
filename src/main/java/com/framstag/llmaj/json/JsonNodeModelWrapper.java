package com.framstag.llmaj.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class JsonNodeModelWrapper extends AbstractMap<String, Object> {

    private final JsonNode node;

    public JsonNodeModelWrapper(JsonNode node) {
        this.node = node;
    }

    @Override
    public Object get(Object key) {
        JsonNode child = node.get(String.valueOf(key));
        return convertNode(child);
    }

    @Override
    public boolean containsKey(Object key) {
        return node.has(String.valueOf(key));
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return containsKey(key) ? get(key) : defaultValue;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(node.fieldNames(), Spliterator.ORDERED),
                        false
                )
                .map(name -> new AbstractMap.SimpleEntry<>(name, get(name)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Object convertNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return convertValueNode(node);
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                    .map(this::convertNode)
                    .collect(Collectors.toList());
        }
        return new JsonNodeModelWrapper(node); // wrap nested object
    }

    private Object convertValueNode(JsonNode valueNode) {
        if (valueNode.isBoolean()) {
            return valueNode.booleanValue();
        }
        if (valueNode.isInt()) {
            return valueNode.intValue();
        }
        if (valueNode.isLong()) {
            return valueNode.longValue();
        }
        if (valueNode.isFloat() || valueNode.isDouble() || valueNode.isBigDecimal()) {
            return valueNode.doubleValue();
        }
        if (valueNode.isTextual()) {
            return valueNode.textValue();
        }

        return valueNode.asText();
    }
}
