package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.tools.java.BuildUnit;
import com.framstag.llmaj.tools.java.Clazz;
import com.framstag.llmaj.tools.java.Field;
import com.framstag.llmaj.tools.java.Method;
import com.framstag.llmaj.tools.java.MethodVisibility;
import com.framstag.llmaj.tools.java.Module;
import com.framstag.llmaj.tools.java.Package;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleVocabularyTest {

    private static Clazz type(String qualifiedName, String documentation) {
        return new Clazz(qualifiedName, documentation);
    }

    private static BuildUnit buildUnit(String name, boolean production, boolean generated, Clazz... clazzes) {
        return new BuildUnit(name, production, generated, List.of(), List.of(clazzes));
    }

    private static Module moduleOf(String packageName, BuildUnit... buildUnits) {
        Package pck = new Package(packageName);
        Module module = new Module("core");

        for (BuildUnit buildUnit : buildUnits) {
            pck.addBuildUnit(buildUnit);
        }

        module.addPackage(pck);

        return module;
    }

    @Test
    void everyChannelCountsTheTokenItCarries() {
        Clazz projectBuilder = type("com.example.core.ProjectBuilder", "Assembles the project realm");
        projectBuilder.addMethod(new Method("buildProject", "()V"));
        projectBuilder.addField(new Field("projectRealm", "com.example.core.Realm",
                MethodVisibility.PRIVATE, false, true));

        Module module = moduleOf("com.example.core", buildUnit("com.example.core.ProjectBuilder",
                true, false, projectBuilder));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertEquals(1, terms.get("project").getOccurrences(VocabularyChannel.TYPE));
        assertEquals(1, terms.get("builder").getOccurrences(VocabularyChannel.TYPE));
        assertEquals(1, terms.get("project").getOccurrences(VocabularyChannel.METHOD));
        assertEquals(1, terms.get("build").getOccurrences(VocabularyChannel.METHOD_VERB));
        assertEquals(0, terms.get("project").getOccurrences(VocabularyChannel.METHOD_VERB));
        assertEquals(1, terms.get("project").getOccurrences(VocabularyChannel.FIELD));
        // The field name carries it and the declared type repeats it.
        assertEquals(2, terms.get("realm").getOccurrences(VocabularyChannel.FIELD));
        assertEquals(1, terms.get("realm").getOccurrences(VocabularyChannel.DOC));
        assertEquals(1, terms.get("core").getOccurrences(VocabularyChannel.PACKAGE));
    }

    @Test
    void theVerbChannelHoldsOnlyTheHeadOfAMethodName() {
        Clazz type = type("com.example.core.RealmCache", null);
        type.addMethod(new Method("expireRealmEntry", "()V"));

        Module module = moduleOf("com.example.core", buildUnit("com.example.core.RealmCache",
                true, false, type));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertEquals(1, terms.get("expire").getOccurrences(VocabularyChannel.METHOD_VERB));
        assertEquals(0, terms.get("realm").getOccurrences(VocabularyChannel.METHOD_VERB));
        assertEquals(1, terms.get("realm").getOccurrences(VocabularyChannel.METHOD));
    }

    @Test
    void aPackageContributesItsSegmentsOnce() {
        Clazz first = type("com.example.core.First", null);
        Clazz second = type("com.example.core.Second", null);

        Module module = moduleOf("com.example.core",
                buildUnit("com.example.core.First", true, false, first),
                buildUnit("com.example.core.Second", true, false, second));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertEquals(1, terms.get("core").getOccurrences(VocabularyChannel.PACKAGE));
        assertEquals(1, terms.get("first").getOccurrences(VocabularyChannel.TYPE));
        assertEquals(1, terms.get("second").getOccurrences(VocabularyChannel.TYPE));
    }

    @Test
    void generatedAndTestAndGeneratedTestCodeIsNotCounted() {
        Clazz production = type("com.example.core.Production", null);
        Clazz test = type("com.example.core.MockRealm", null);
        Clazz generated = type("com.example.core.ProjectMetamodel", null);
        Clazz testGenerated = type("com.example.core.RealmTestFixtures", null);

        Module module = moduleOf("com.example.core",
                buildUnit("com.example.core.Production", true, false, production),
                buildUnit("com.example.core.MockRealm", false, false, test),
                buildUnit("com.example.core.ProjectMetamodel", true, true, generated),
                buildUnit("com.example.core.RealmTestFixtures", false, true, testGenerated));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertTrue(terms.containsKey("production"));
        assertFalse(terms.containsKey("mock"));
        assertFalse(terms.containsKey("metamodel"));
        assertFalse(terms.containsKey("fixtures"));
    }

    @Test
    void aTestOnlyPackageNamesNoProductionConcept() {
        Clazz test = type("com.example.realmtest.MockRealm", null);

        Module module = moduleOf("com.example.realmtest",
                buildUnit("com.example.realmtest.MockRealm", false, false, test));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertFalse(terms.containsKey("realmtest"));
        assertTrue(terms.isEmpty());
    }

    @Test
    void aTypeNameIsCountedWithoutItsPackagesAndWithoutTheTypeItIsNestedIn() {
        Clazz outer = type("com.example.core.ProjectRealm", null);
        Clazz nested = type("com.example.core.ProjectRealm$CacheRecord", null);

        Module module = moduleOf("com.example.core",
                buildUnit("com.example.core.ProjectRealm", true, false, outer, nested));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertEquals(1, terms.get("project").getOccurrences(VocabularyChannel.TYPE));
        assertEquals(1, terms.get("realm").getOccurrences(VocabularyChannel.TYPE));
        assertEquals(1, terms.get("cache").getOccurrences(VocabularyChannel.TYPE));
        assertEquals(1, terms.get("record").getOccurrences(VocabularyChannel.TYPE));
    }

    @Test
    void aFieldTypeIsCountedWithoutItsPackages() {
        Clazz type = type("com.example.core.Realm", null);
        type.addField(new Field("entries", "java.util.LinkedHashMap",
                MethodVisibility.PRIVATE, false, false));

        Module module = moduleOf("com.example.core", buildUnit("com.example.core.Realm",
                true, false, type));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertEquals(1, terms.get("linked").getOccurrences(VocabularyChannel.FIELD));
        assertEquals(1, terms.get("hash").getOccurrences(VocabularyChannel.FIELD));
        assertEquals(1, terms.get("map").getOccurrences(VocabularyChannel.FIELD));
        assertFalse(terms.containsKey("java"));
        assertFalse(terms.containsKey("util"));
    }

    @Test
    void occurrencesInsideOneNameCountTwice() {
        Clazz type = type("com.example.core.ProjectProject", null);

        Module module = moduleOf("com.example.core", buildUnit("com.example.core.ProjectProject",
                true, false, type));

        Map<String, TermOccurrences> terms = ModuleVocabulary.of(module).getOccurrencesByTerm();

        assertEquals(2, terms.get("project").getOccurrences(VocabularyChannel.TYPE));
    }

    @Test
    void anEmptyModuleYieldsNoTerm() {
        assertTrue(ModuleVocabulary.of(new Module("core")).isEmpty());
        assertEquals(0, ModuleVocabulary.of(new Module("core")).getTermCount());
        assertNull(ModuleVocabulary.of(new Module("core")).getOccurrencesByTerm().get("realm"));
    }
}
