package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.tools.java.BuildUnit;
import com.framstag.llmaj.tools.java.Clazz;
import com.framstag.llmaj.tools.java.Field;
import com.framstag.llmaj.tools.java.Method;
import com.framstag.llmaj.tools.java.Module;
import com.framstag.llmaj.tools.java.Package;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The counted vocabulary of one module report.
 *
 * <p>Only hand-written production code is counted: a build unit that is test code, generated code or both
 * is skipped, so that a mock named after a domain class and a generated metamodel do not enter the
 * vocabulary of the module. The package segments of a package enter the vocabulary once, not once per
 * class, and only when the package holds counted code - a test-only package names no production concept.
 *
 * <p>The class knows nothing about tiers: it counts what a name is made of, and the lexicon decides
 * afterwards what the tokens mean.
 */
public final class ModuleVocabulary {

    private final Map<String, TermOccurrences> occurrencesByTerm = new LinkedHashMap<>();

    private ModuleVocabulary() {
    }

    public static ModuleVocabulary of(Module module) {
        ModuleVocabulary vocabulary = new ModuleVocabulary();

        if (module == null) {
            return vocabulary;
        }

        for (Package pck : module.getPackages()) {
            boolean countedBuildUnitSeen = false;

            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!isCounted(buildUnit)) {
                    continue;
                }

                countedBuildUnitSeen = true;
                vocabulary.addBuildUnit(buildUnit);
            }

            if (countedBuildUnitSeen) {
                vocabulary.addText(VocabularyChannel.PACKAGE, pck.getName());
            }
        }

        return vocabulary;
    }

    /**
     * @return true when the build unit is hand-written production code
     */
    public static boolean isCounted(BuildUnit buildUnit) {
        return buildUnit.isProduction() && !buildUnit.isGenerated();
    }

    private void addBuildUnit(BuildUnit buildUnit) {
        for (Clazz clazz : buildUnit.getClazzes()) {
            addText(VocabularyChannel.TYPE, simpleNameOf(clazz.getQualifiedName()));
            addText(VocabularyChannel.DOC, clazz.getDocumentation());

            for (Method method : clazz.getMethods()) {
                addMethod(method);
            }

            for (Field field : clazz.getFields()) {
                addText(VocabularyChannel.FIELD, field.getName());
                addText(VocabularyChannel.FIELD, simpleTypeOf(field.getType()));
            }
        }
    }

    private void addMethod(Method method) {
        if (!ProseText.isDeclaredName(method.getName())) {
            return;
        }

        List<String> tokens = IdentifierTokenizer.tokenize(method.getName());

        for (int i = 0; i < tokens.size(); i++) {
            add(VocabularyChannel.METHOD, tokens.get(i));

            if (i == 0) {
                add(VocabularyChannel.METHOD_VERB, tokens.get(i));
            }
        }

        addText(VocabularyChannel.DOC, method.getDocumentation());
    }

    private void addText(VocabularyChannel channel, String text) {
        List<String> tokens = (channel == VocabularyChannel.DOC)
                ? ProseText.tokens(text)
                : IdentifierTokenizer.tokenize(text);

        for (String token : tokens) {
            add(channel, token);
        }
    }

    private void add(VocabularyChannel channel, String token) {
        occurrencesByTerm.computeIfAbsent(token, t -> new TermOccurrences()).add(channel);
    }

    /**
     * @return the name a declared type is known by, without the packages and without the name of the type
     * it is nested in: the nested type has a name of its own and the outer type is counted where it is
     * declared
     */
    private static String simpleNameOf(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }

        int lastDot = qualifiedName.lastIndexOf('.');
        int lastDollar = qualifiedName.lastIndexOf('$');
        int start = Math.max(lastDot, lastDollar);

        return start < 0 ? qualifiedName : qualifiedName.substring(start + 1);
    }

    /**
     * @return the declared type without its packages, so that a field of type {@code java.util.List} adds
     * {@code list} and not the words of the package it lives in
     */
    private static String simpleTypeOf(String type) {
        return ProseText.simpleTypeName(type);
    }

    /**
     * @return one entry per distinct token, with its occurrences per channel
     */
    public Map<String, TermOccurrences> getOccurrencesByTerm() {
        return Collections.unmodifiableMap(occurrencesByTerm);
    }

    public boolean isEmpty() {
        return occurrencesByTerm.isEmpty();
    }

    /**
     * @return the number of distinct tokens
     */
    public int getTermCount() {
        return occurrencesByTerm.size();
    }
}
