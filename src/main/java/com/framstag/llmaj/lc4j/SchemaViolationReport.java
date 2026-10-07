package com.framstag.llmaj.lc4j;

import com.networknt.schema.Error;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the errors of the schema validator into reports a reader and a model can act on.
 * <p>
 * The validator's own message is written in the language of the machine the engine runs on and names
 * neither the position of the offending value nor the value itself, so a rejection could not be acted
 * on: the Maven run of 2026-10-06 reported 188 violations as "hat keinen Wert in der Aufzählung [...]"
 * without saying where the wrong value was or what it was. Every report is composed here from the parts
 * the validator exposes - instance location, rejected value, keyword and schema node - so the same
 * payload yields the same report on every machine, whatever the default locale is.
 */
public final class SchemaViolationReport {

    /**
     * How much of a rejected value a report shows, so a report of a rejected object does not carry the
     * object itself.
     */
    private static final int MAX_VALUE_LENGTH = 80;

    private SchemaViolationReport() {
    }

    /**
     * One report per validator error, in the order the validator produced them.
     */
    public static List<String> of(List<Error> errors) {
        List<String> reports = new ArrayList<>();

        for (Error error : errors) {
            reports.add(of(error));
        }

        return reports;
    }

    static String of(Error error) {
        String location = location(error);
        String keyword = error.getKeyword() == null ? "" : error.getKeyword();

        return switch (keyword) {
            case "enum" -> location + ": the value " + value(error)
                    + " is not one of the permitted values " + permittedValues(error);
            case "required" -> location + ": the required property '" + missingProperty(error)
                    + "' is missing";
            case "type" -> location + ": the value " + value(error) + " is not of type "
                    + expectedType(error);
            default -> location + ": the value " + value(error) + " does not satisfy '" + keyword
                    + "', the schema expects " + schemaNode(error);
        };
    }

    /**
     * Where inside the payload the rejected value sits, for example {@code /moduleEvaluations/0/urgency}.
     */
    private static String location(Error error) {
        var instanceLocation = error.getInstanceLocation();

        if (instanceLocation == null || instanceLocation.toString().isEmpty()) {
            return "the payload";
        }

        return instanceLocation.toString();
    }

    private static String value(Error error) {
        JsonNode instanceNode = error.getInstanceNode();

        if (instanceNode == null || instanceNode.isMissingNode()) {
            return "(no value)";
        }

        return shorten(instanceNode.toString());
    }

    private static String permittedValues(Error error) {
        JsonNode schemaNode = error.getSchemaNode();
        List<String> values = new ArrayList<>();

        if (schemaNode != null && schemaNode.isArray()) {
            for (JsonNode value : schemaNode) {
                values.add(value.asString());
            }
        }

        return "[" + String.join(", ", values) + "]";
    }

    private static String missingProperty(Error error) {
        if (error.getProperty() != null) {
            return error.getProperty();
        }

        Object[] arguments = error.getArguments();

        return arguments != null && arguments.length > 0 ? String.valueOf(arguments[0]) : "(unknown)";
    }

    private static String expectedType(Error error) {
        Object[] arguments = error.getArguments();

        if (arguments != null && arguments.length > 1) {
            return String.valueOf(arguments[arguments.length - 1]);
        }

        return schemaNode(error);
    }

    private static String schemaNode(Error error) {
        JsonNode schemaNode = error.getSchemaNode();

        return schemaNode == null ? "(unknown)" : shorten(schemaNode.toString());
    }

    private static String shorten(String text) {
        if (text.length() <= MAX_VALUE_LENGTH) {
            return text;
        }

        return text.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }
}
