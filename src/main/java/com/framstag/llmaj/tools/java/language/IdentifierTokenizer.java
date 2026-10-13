package com.framstag.llmaj.tools.java.language;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Splits identifiers and prose into the lowercase tokens a vocabulary count is built from.
 *
 * <p>The tokenizer is deliberately mechanical: it splits at camel case boundaries, at underscores and at
 * digits, lowercases what is left, and drops what cannot carry meaning - single character tokens, digit
 * runs and the keywords of the language. It knows nothing about domains: which of the surviving tokens
 * names a domain and which names plumbing is decided by the lexicon, not here.
 *
 * <p>Token order and repetition are preserved, because the vocabulary analysis counts occurrences. A name
 * that repeats a word, such as {@code ProjectProject}, contributes that word twice.
 */
public final class IdentifierTokenizer {

    /**
     * The reserved words and literals of the language, plus {@code var}, which is a reserved type name. The
     * contextual modifiers of later language versions ({@code record}, {@code sealed}, {@code permits},
     * {@code yield}) are not listed: each of them is an ordinary word of a domain name, as in
     * {@code CacheRecord}, and dropping it would lose the meaning of the name.
     */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "var", "void",
            "volatile", "while", "true", "false", "null");

    private static final int MINIMUM_TOKEN_LENGTH = 2;

    private IdentifierTokenizer() {
    }

    /**
     * @return the tokens of the text in the order they occur, with repetitions, each of them lowercase and
     * neither a single character nor a digit run nor a keyword
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new LinkedList<>();

        if (text == null || text.isEmpty()) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        char previous = 0;

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            char next = (i + 1 < text.length()) ? text.charAt(i + 1) : 0;

            if (Character.isDigit(character)) {
                flush(current, tokens);
                previous = character;
                continue;
            }

            if (!Character.isLetter(character)) {
                flush(current, tokens);
                previous = character;
                continue;
            }

            if (Character.isUpperCase(character) && !current.isEmpty() && startsNewWord(previous, next)) {
                flush(current, tokens);
            }

            current.append(Character.toLowerCase(character));
            previous = character;
        }

        flush(current, tokens);

        return tokens;
    }

    /**
     * A camel case boundary sits before an upper case letter that follows a lower case letter or a digit,
     * and it sits inside a run of upper case letters before the last one, so that an acronym stays one word.
     */
    private static boolean startsNewWord(char previous, char next) {
        if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
            return true;
        }

        return Character.isUpperCase(previous) && next != 0 && Character.isLowerCase(next);
    }

    private static void flush(StringBuilder current, List<String> tokens) {
        if (current.isEmpty()) {
            return;
        }

        String token = current.toString();
        current.setLength(0);

        if (token.length() < MINIMUM_TOKEN_LENGTH || KEYWORDS.contains(token)) {
            return;
        }

        tokens.add(token);
    }
}
