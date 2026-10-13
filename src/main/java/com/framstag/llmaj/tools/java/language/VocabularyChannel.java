package com.framstag.llmaj.tools.java.language;

/**
 * Where a token was found. The channels are kept apart because they say different things about a name:
 * a type name is a noun of the model, a method name is a verb and therefore a behaviour, a field name and
 * its type are the state, a package segment is a structural hint and documentation is stated intent rather
 * than encoded meaning.
 */
public enum VocabularyChannel {
    TYPE("type"),
    METHOD("method"),
    METHOD_VERB("methodVerb"),
    FIELD("field"),
    PACKAGE("package"),
    DOC("doc");

    private final String value;

    VocabularyChannel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
