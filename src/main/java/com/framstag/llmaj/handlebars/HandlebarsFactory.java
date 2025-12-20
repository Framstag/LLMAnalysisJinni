package com.framstag.llmaj.handlebars;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;

public class HandlebarsFactory {

    public static Handlebars create() {
        var handlebars = new Handlebars()
                .with(EscapingStrategy.HTML_ENTITY);

// AND helper: all arguments must be true
        handlebars.registerHelper("and", (context, options) -> {
            boolean result = isTruthy(context);

            for (Object param : options.params) {
                result = result && isTruthy(param);
                if (!result) break; // short-circuit
            }

            return result ? options.fn() : options.inverse();
        });

        // OR helper: at least one argument must be true
        handlebars.registerHelper("or", (context, options) -> {
            boolean result = isTruthy(context);

            for (Object param : options.params) {
                result = result || isTruthy(param);
                if (result) break; // short-circuit
            }

            return result ? options.fn() : options.inverse();
        });

        return handlebars;
    }

    private static boolean isTruthy(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean b -> b;
            case Number number -> number.doubleValue() != 0;
            case String s -> !s.isEmpty();
            default -> true;
        };
    }
}
