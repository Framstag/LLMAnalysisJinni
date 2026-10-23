package com.framstag.llmaj.tools.java;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The type solver of the report collection resolves symbols, and a symbol is a declaration, never a comment. The
 * solver used to parse every source tree of a module with the comment attribution of a full parse, which made the
 * collection of a module of several hundred source files run for more than twenty minutes without writing a report.
 *
 * <p>The parse of the module itself keeps its comment attribution, because the documentation of a class and of a
 * method - and every metric that counts it - comes from there. This test holds both halves of that decision, and the
 * language level of the two parsers, so that they cannot drift apart.
 */
class TypeSolverCommentAttributionTest {

    @Test
    void theSolverOfTheReportCollectionDoesNotAttributeComments() {
        assertFalse(JavaTool.createSolverConfiguration().isAttributeComments(),
                "the solver parses without comment attribution, because a symbol is a declaration, never a comment");
    }

    @Test
    void theSolverNamesTheSameLanguageLevelAsTheModuleParse() {
        assertEquals(ParserConfiguration.LanguageLevel.JAVA_21,
                JavaTool.createSolverConfiguration().getLanguageLevel(),
                "the solver and the module parse read the same language level");
    }

    @Test
    void theModuleParseStillAttributesComments() {
        assertTrue(StaticJavaParser.getParserConfiguration().isAttributeComments(),
                "the parse of the module keeps its comments, because the documentation ratio counts them");
    }
}
