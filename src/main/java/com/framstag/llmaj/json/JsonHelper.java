package com.framstag.llmaj.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonHelper
{
    private static final Logger logger = LoggerFactory.getLogger(JsonHelper.class);

    public static JsonNode readResult(ObjectMapper mapper, Path path) {
        try {
            return mapper.readTree(path.toFile());
        } catch (IOException e) {
            logger.error("Exception while writing result to file", e);
        }

        return null;
    }

    public static void writeResult(JsonNode result, ObjectMapper mapper, Path path) {
        try {
            File file = path.toFile();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, result);
        } catch (IOException e) {
            logger.error("Exception while writing result to file", e);
        }
    }

    public static String getObjectDescription(JsonNode schema) {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");

        JsonNode properties = schema.get("properties");
        final AtomicInteger propertyCount = new AtomicInteger();
        properties.fieldNames().forEachRemaining(fieldName -> propertyCount.getAndIncrement());

        final AtomicInteger propertyPos = new AtomicInteger();
        properties.fieldNames().forEachRemaining(fieldName -> {
            JsonNode property = properties.get(fieldName);

            if ((property.has("type"))) {
                sb.append("\"").append(fieldName).append("\": (");

                if (property.has("description")) {
                    sb.append(property.get("description").asText()).append("; ");
                }
                if (property.has("type")) {
                    if (property.get("type").asText().equals("object") &&
                            property.has("properties")) {
                        sb.append("type: object ").append(getObjectDescription(property));
                    }  else if (property.get("type").asText().equals("array") &&
                            property.has("items") &&
                            property.get("items").has("type") &&
                            property.get("items").get("type").asText().equals("object") &&
                            property.get("items").has("title")) {
                        sb.append("type: array of ")
                                .append(property.get("items").get("title").asText())
                                .append(": ")
                                .append(getObjectDescription(property.get("items")));
                    } else {
                        sb.append("type: ").append(property.get("type").asText());
                    }
                }

                sb.append(")");

                if (propertyPos.get() < propertyCount.get() - 1) {
                    sb.append(",\n");
                }
            }

            propertyPos.getAndIncrement();
        });

        sb.append("\n}");

        return sb.toString();
    }

    public static String createTypeDescription(JsonNode schema) {
        StringBuilder sb = new StringBuilder();

        if (!(schema.has("type") &&
                schema.get("type").asText().equals("object") &&
                schema.has("properties"))) {
            logger.error("Root object type of result schema must be of type 'object' and must have a 'properties' attribute");
            return "";
        }

        sb.append(getObjectDescription(schema));

        return sb.toString();
    }

    public static String getSchemaName(JsonNode schema) {
        if (schema.has("title")) {
            return schema.get("title").asText();
        }

        return "Result";
    }
}
