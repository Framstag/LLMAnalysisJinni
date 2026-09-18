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

    /**
     * Parser for the payload of a model response. The mapper is stateless, so one instance serves
     * every caller.
     */
    private static final ResponsePayloadParser PAYLOAD_PARSER =
            new ResponsePayloadParser(ObjectMapperFactory.getJSONObjectMapperInstance());

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
                        if (property.has("enum")) {
                            sb.append(", allowed: [");
                            JsonNode enumValues = property.get("enum");
                            for (int i = 0; i < enumValues.size(); i++) {
                                if (i > 0) sb.append(", ");
                                sb.append("\"").append(enumValues.get(i).asText()).append("\"");
                            }
                            sb.append("]");
                    }
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

    /**
     * Escape the unescaped quotes inside the value of a line that carries a key/value pair.
     * <p>
     * The line may have anything in front of the key, so a single-line document works as well as a
     * pretty-printed one. A quote ends the value when the next non-whitespace character closes the
     * entry - a comma, a closing brace or a closing bracket - or when the line ends.
     *
     * @param line one line of a document
     * @return the line with the inner quotes of its value escaped
     */
    public static String fixJsonLine(String line) {
        // Prefix: everything up to and including the colon that follows the key
        Pattern pat = Pattern.compile("^([^\"]*\"[^\"]*?\"\\s*:\\s*)\"(.*)$");
        Matcher m = pat.matcher(line);
        if (!m.matches()) {
            return line; // Passt nicht → unverändert
        }

        String prefix = m.group(1); // z.B.   "key":  oder  {"key":
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
                // Heuristik: String-Ende wenn danach Komma, schließende Klammer oder Zeilenende.
                // Bei Leerraum entscheidet das erste Nicht-Leerraum-Zeichen danach: nur dann ist
                // das Zitat ein String-Ende, ein inneres Zitat wird von weiterem Text gefolgt.
                boolean isStringEnd = false;
                int next = i + 1;

                while (next < rest.length() && (rest.charAt(next) == ' ' || rest.charAt(next) == '\t')) {
                    next++;
                }

                if (next >= rest.length()) {
                    isStringEnd = true; // Zeilenende
                } else {
                    char afterQuote = rest.charAt(next);
                    isStringEnd = afterQuote == ',' || afterQuote == '}' || afterQuote == ']';
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

    /**
     * Escape the unescaped quotes inside the values of a document, line by line. Pure text repair,
     * used by {@link ResponsePayloadParser} as a fallback for a payload that does not parse.
     * <p>
     * A repair is only accepted by that caller when it leaves the brackets of the document alone.
     */
    static String fixJsonDocument(String text) {        return java.util.Arrays.stream(text.split("\n"))
                .map(JsonHelper::fixJsonLine)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /**
     * Locate the payload of a response and return it as text, without parsing it.
     * <p>
     * The string-level entry point for callers that do not need a parsed document; callers that do
     * use {@link ResponsePayloadParser#parse(String)}, which also reports why nothing could be parsed.
     *
     * @param jsonString original string
     *
     * @return the located payload, repaired when it does not parse as it is, or the original string
     * when no payload can be located
     */
    public static String extractJSON(String jsonString) {
        return PAYLOAD_PARSER.payloadText(jsonString);
    }
}
