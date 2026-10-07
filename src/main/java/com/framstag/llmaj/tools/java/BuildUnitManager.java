package com.framstag.llmaj.tools.java;

import java.util.*;

public class BuildUnitManager {
    private final String name;
    private boolean isGenerated;
    private boolean isProduction;
    private final Set<String> imports;
    private final Map<String, ClassManager> classesByName;
    private final Map<String, ReferenceAccumulator> referencesByTarget;

    public BuildUnitManager(String name) {
        this.name = name;
        this.isGenerated = false;
        this.isProduction = true;
        this.imports = new HashSet<>();
        this.classesByName = new HashMap<>();
        this.referencesByTarget = new HashMap<>();
    }

    /**
     * Collects the strength of one reference while the class file is being read. Several class files, an
     * outer class and its nested classes, contribute to the same build unit, so the members are kept as a
     * set: calling the same member from the outer and from the nested class is one member of the interface,
     * not two.
     */
    private static final class ReferenceAccumulator {
        private final Set<String> members = new TreeSet<>();
        private int traffic;
        private boolean structural;
    }

    /**
     * Records one reference site. A {@code memberKey} of {@code null} records that the target is referenced
     * as a type without a measured member, which is what a superclass, an interface or a declared field type
     * contributes.
     */
    public void addReference(String target, String memberKey, boolean structural) {
        ReferenceAccumulator accumulator = referencesByTarget.computeIfAbsent(target,
                ignored -> new ReferenceAccumulator());

        if (memberKey != null) {
            accumulator.members.add(memberKey);
            accumulator.traffic++;
        }

        if (structural) {
            accumulator.structural = true;
        }
    }

    public List<ClassReference> getReferences() {
        return referencesByTarget.entrySet().stream()
                .map(entry -> new ClassReference(entry.getKey(),
                        entry.getValue().members.size(),
                        entry.getValue().traffic,
                        entry.getValue().structural))
                .sorted()
                .toList();
    }

    public String getName() {
        return name;
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    public void setGenerated(boolean generated) {
        isGenerated = generated;
    }

    public boolean isProduction() {
        return isProduction;
    }

    public void setProduction(boolean production) {
        isProduction = production;
    }

    public List<String> getImports() {
        return new ArrayList<>(imports);
    }

    public void addImports(Collection<String> imports) {
        this.imports.addAll(imports);
    }

    public ClassManager getOrAddClassByName(String name) {
        return classesByName.computeIfAbsent(name, ClassManager::new);
    }

    public List<ClassManager> getClasses() {
        return classesByName.values().stream().toList();
    }

    /**
     * @return true when a class file of this build unit reported at least one reference site or a structural
     * relation, so a report written before the weighted record can be told apart from a module without any
     * references
     */
    public boolean hasReferences() {
        return !referencesByTarget.isEmpty();
    }

}
