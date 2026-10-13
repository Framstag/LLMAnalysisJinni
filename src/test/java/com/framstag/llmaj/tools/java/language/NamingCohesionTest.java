package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.tools.java.BuildUnit;
import com.framstag.llmaj.tools.java.ClassReference;
import com.framstag.llmaj.tools.java.Clazz;
import com.framstag.llmaj.tools.java.Module;
import com.framstag.llmaj.tools.java.Package;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingCohesionTest {

    private static Clazz type(String qualifiedName) {
        return new Clazz(qualifiedName, null);
    }

    private static BuildUnit productionBuildUnit(String qualifiedName, List<String> referencedTargets) {
        List<ClassReference> references = referencedTargets.stream()
                .map(target -> new ClassReference(target, 1, 1, false))
                .toList();

        return new BuildUnit(qualifiedName, true, false, List.of(), references,
                List.of(type(qualifiedName)));
    }

    private static Module moduleOf(BuildUnit... buildUnits) {
        Package pck = new Package("com.example.core");
        Module module = new Module("core");

        for (BuildUnit buildUnit : buildUnits) {
            pck.addBuildUnit(buildUnit);
        }

        module.addPackage(pck);

        return module;
    }

    @Test
    void namesThatFollowTheReferencesReportAPositiveCohesion() {
        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.RealmCache", List.of("com.example.core.RealmStore")),
                productionBuildUnit("com.example.core.RealmStore", List.of()),
                productionBuildUnit("com.example.core.PluginLoader", List.of())));

        assertEquals(1.0 / 3.0, cohesion.linkedOverlap(), 1e-9);
        assertEquals(0.0, cohesion.unlinkedOverlap(), 1e-9);
        assertEquals(1.0 / 3.0, cohesion.value(), 1e-9);
    }

    @Test
    void aModuleWithoutAReferenceHasNoValue() {
        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.RealmCache", List.of()),
                productionBuildUnit("com.example.core.RealmStore", List.of()),
                productionBuildUnit("com.example.core.PluginLoader", List.of())));

        assertNull(cohesion.linkedOverlap());
        assertNotNull(cohesion.unlinkedOverlap());
        assertNull(cohesion.value());
    }

    @Test
    void aModuleWhoseTypesAllReferenceEachOtherHasNoValue() {
        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.RealmCache", List.of("com.example.core.RealmStore")),
                productionBuildUnit("com.example.core.RealmStore", List.of("com.example.core.RealmCache"))));

        assertNotNull(cohesion.linkedOverlap());
        assertNull(cohesion.unlinkedOverlap());
        assertNull(cohesion.value());
    }

    @Test
    void everyPairOfBuildUnitsIsListedWithItsReferenceFlagAndSharedTerms() {
        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.RealmCache", List.of("com.example.core.RealmStore")),
                productionBuildUnit("com.example.core.RealmStore", List.of()),
                productionBuildUnit("com.example.core.PluginLoader", List.of())));

        assertEquals(3, cohesion.pairs().size());

        NamingCohesion.ClassPairOverlap linkedPair = cohesion.pairs().get(0);

        assertTrue(linkedPair.linked());
        assertEquals("com.example.core.RealmCache", linkedPair.firstBuildUnit());
        assertEquals("com.example.core.RealmStore", linkedPair.secondBuildUnit());
        assertEquals(List.of("realm"), linkedPair.sharedTerms());
        assertFalse(cohesion.pairs().get(1).linked());
        assertEquals(List.of(), cohesion.pairs().get(1).sharedTerms());
    }

    @Test
    void aTypeWithoutANameOfItsOwnCarriesNoPair() {
        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.A", List.of()),
                productionBuildUnit("com.example.core.B", List.of())));

        assertEquals(List.of(), cohesion.pairs());
        assertNull(cohesion.value());
    }

    @Test
    void aReferenceToANestedTypeCountsForTheBuildUnitThatEnclosesIt() {
        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.RealmCache", List.of("com.example.core.RealmStore$Entry")),
                productionBuildUnit("com.example.core.RealmStore", List.of()),
                productionBuildUnit("com.example.core.PluginLoader", List.of())));

        assertTrue(cohesion.pairs().get(0).linked());
        assertNotNull(cohesion.value());
    }

    @Test
    void testCodeNeverEntersThePairs() {
        BuildUnit testUnit = new BuildUnit("com.example.core.RealmCacheTest", false, false, List.of(), List.of(),
                List.of(type("com.example.core.RealmCacheTest")));

        NamingCohesion cohesion = NamingCohesion.of(moduleOf(
                productionBuildUnit("com.example.core.RealmCache", List.of("com.example.core.RealmCacheTest")),
                productionBuildUnit("com.example.core.PluginLoader", List.of()),
                testUnit));

        assertEquals(1, cohesion.pairs().size());
        assertEquals("com.example.core.RealmCache", cohesion.pairs().get(0).firstBuildUnit());
        assertEquals("com.example.core.PluginLoader", cohesion.pairs().get(0).secondBuildUnit());
        assertFalse(cohesion.pairs().get(0).linked());
    }

    @Test
    void aModuleWithoutCountedCodeHasNoValue() {
        NamingCohesion cohesion = NamingCohesion.of(new Module("core"));

        assertEquals(List.of(), cohesion.pairs());
        assertNull(cohesion.value());
    }
}
