package com.framstag.llmaj.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonHelper
{
    private static final Logger logger = LoggerFactory.getLogger(JsonHelper.class);

    private static String getObjectDescription(JsonNode schema) {
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

    /**
     * Get a more or less standardized textual description of theJSON schema to be used as a result
     * format description for the LLM.
     * @param schema the JSON schema
     * @return a textual description of the schema
     */
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

    /**
     * Get the name (attribute "title") of the passed schema.
     * @param schema The schema as string
     * @return the name of the schema or "Result" if no name was found in the schema description.
     */
    public static String getSchemaName(JsonNode schema) {
        if (schema.has("title")) {
            return schema.get("title").asText();
        }

        return "Result";
    }

    public static String fixJsonLine(String line) {
        // Regex: Gruppiert Prefix bis ": " und Rest ab Value-"
        Pattern pat = Pattern.compile("^(\\s*\"[^\"]*?\"\\s*:\\s*)\"(.*)$");
        Matcher m = pat.matcher(line);
        if (!m.matches()) {
            return line; // Passt nicht → unverändert
        }

        String prefix = m.group(1); // z.B.   "key":
        String rest = m.group(2);   // z.B.  value with "bad" quotes",

        // Inneren String reparieren
        StringBuilder out = new StringBuilder();
        boolean inString = true;
        boolean escape = false;

        for (int i = 0; i < rest.length(); i++) {
            char ch = rest.charAt(i);

            if (!inString) {
                out.append(ch);
                continue;
            }

            if (escape) {
                out.append(ch);
                escape = false;
                continue;
            }

            if (ch == '\\') {
                out.append(ch);
                escape = true;
                continue;
            }

            if (ch == '"') {
                // Heuristik: String-Ende wenn danach Komma, }, Leerraum+Komma oder Zeilenende
                boolean isStringEnd = false;
                if (i + 1 < rest.length()) {
                    char next = rest.charAt(i + 1);
                    if (next == ',' || next == '}') {
                        isStringEnd = true;
                    } else if (next == ' ' || next == '\t') {
                        // Skip whitespace
                        for (int j = i + 2; j < rest.length(); j++) {
                            char afterSpace = rest.charAt(j);
                            if (afterSpace == ',' || afterSpace == '}') {
                                isStringEnd = true;
                                break;
                            }
                        }
                    }
                } else {
                    isStringEnd = true; // Zeilenende
                }

                if (isStringEnd) {
                    out.append('"'); // Normale schließende "
                    inString = false;
                } else {
                    out.append("\\\""); // Inneres "
                }
                continue;
            }

            out.append(ch);
        }

        String fixedRest = out.toString();
        return prefix + '"'+ fixedRest;
    }

    private static String fixJsonDocument(String text) {
        return java.util.Arrays.stream(text.split("\n"))
                .map(JsonHelper::fixJsonLine)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /**
     * Remove potential LLM wrapping of the actual JSON context
     *
     * @param jsonString original string
     *
     * @return potentially cleaned and modified string to use for further processing
     */
    public static String extractJSON(String jsonString) {
        String origJsonString = jsonString;

        if (jsonString.startsWith("```json")) {
            jsonString = jsonString.substring(7);
        }
        if (jsonString.endsWith("```")) {
            jsonString = jsonString.substring(0,jsonString.length()-3);
        }

        jsonString = fixJsonDocument(jsonString);

        if (!jsonString.equals(origJsonString)) {
            logger.warn("Corrected JSON String to: {}", jsonString);
        }

        return jsonString;
    }
}
