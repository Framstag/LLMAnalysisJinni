package com.framstag.llmaj.handlebars;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.ConditionalHelpers;

public class HandlebarsFactory {

    public static Handlebars create() {

        return new Handlebars()
                .registerHelpers(ConditionalHelpers.class)
                .registerHelper("groupBy", new GroupByHelper())
                .with(EscapingStrategy.NOOP);
    }
}
