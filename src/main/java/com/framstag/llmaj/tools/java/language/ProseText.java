package com.framstag.llmaj.tools.java.language;

import java.util.List;

/**
 * The token bridge between prose and the vocabulary count.
 *
 * <p>Documentation comments and module descriptions are prose that carries markup: javadoc inline tags such as
 * {@code {@code ...}}, block tags such as {@code @param} and {@code @since}, and HTML elements such as
 * {@code <p>} or {@code <li>}. Those carry no domain and they would flood the documentation channel, so they
 * are removed before the text is tokenized. What they contained stays: the name inside a {@code {@code ...}}
 * tag is vocabulary like any other name.
 *
 * <p>An exception is the author of a file. A person's name carries no domain, and a corpus showed it plainly: one
 * author name appeared in 587 of the module pairs of a project and made them look like a shared language. The
 * whole {@code @author} line is therefore dropped, name included.
 *
 * <p>A type string from a class file is a descriptor rather than a name: {@code Ljava/lang/String;} names the
 * type {@code String} of the package {@code java.lang}, and an array is marked by a leading bracket. A reader
 * of the evidence would see {@code ljava} where the code says {@code String}, so the descriptor is reduced to
 * the name it carries.
 */
public final class ProseText {

    private static final String HTML_ELEMENT = "<[^>]*>";
    private static final String AUTHOR_TAG = "(?m)^[\\s*]*@author[^\\n]*";
    private static final String BLOCK_TAG = "(?m)^[\\s*]*@[a-zA-Z]+";
    private static final String INLINE_TAG_OPENING = "\\{@[a-zA-Z]+";
    private static final String TAG_BRACES = "[{}\\*]";
    private static final String QUALIFIED_NAME = "(?:\\b[a-zA-Z][a-zA-Z0-9]*\\.)+([A-Z][A-Za-z0-9]*)";

    private ProseText() {
    }

    /**
     * @return the tokens of a prose text, without the markup that carries no domain
     */
    public static List<String> tokens(String text) {
        if (text == null) {
            return List.of();
        }

        return IdentifierTokenizer.tokenize(text
                .replaceAll(HTML_ELEMENT, " ")
                .replaceAll(AUTHOR_TAG, " ")
                .replaceAll(BLOCK_TAG, " ")
                .replaceAll(INLINE_TAG_OPENING, " ")
                .replaceAll(TAG_BRACES, " ")
                .replaceAll(QUALIFIED_NAME, "$1"));
    }

    /**
     * @return the name a type string carries, whether it is written as a class file descriptor, as a qualified
     * source name, as an array of either, or with its type arguments
     */
    public static String simpleTypeName(String type) {
        if (type == null) {
            return null;
        }

        String name = type.trim();

        while (name.startsWith("[")) {
            name = name.substring(1);
        }

        // A class file names an object type as L<package>/<Type>; a primitive is its own descriptor letter.
        if (name.length() > 2 && name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }

        name = name.replace('/', '.');

        int genericStart = name.indexOf('<');

        if (genericStart >= 0) {
            name = name.substring(0, genericStart);
        }

        int lastSeparator = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));

        return lastSeparator < 0 ? name : name.substring(lastSeparator + 1);
    }

    /**
     * @return true when the name is a name the code declares. A class file also holds the synthetic names
     * {@code <init>} and {@code <clinit>}, which name a constructor and a static initializer rather than a
     * concept of the module
     */
    public static boolean isDeclaredName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String trimmed = name.trim();

        return !trimmed.startsWith("<") && !trimmed.endsWith(">");
    }
}
