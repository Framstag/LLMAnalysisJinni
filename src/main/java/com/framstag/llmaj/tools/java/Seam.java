package com.framstag.llmaj.tools.java;

import java.util.List;

/**
 * The counted evidence of one interface of the project.
 *
 * <p>A seam is an interface with the classes that implement it, the classes that use it and the modules that
 * import it. The counts are facts about the code, not a verdict: the tool does not say that an interface is
 * good or bad, it says how many implementors it has and how many modules import it.
 *
 * @param interfaceName    the qualified name of the interface
 * @param declaringModule  the module of the project that declares the interface
 * @param implementors     the classes of the project that implement the interface, with their modules
 * @param consumers        the classes of the declaring module that reference the interface and do not
 *                          implement it
 * @param importingModules the modules of the project that import the interface, the declaring module among
 *                          them when its own classes reference it
 * @param portCandidate    whether the interface is implemented more than once and imported by more than one
 *                          module
 * @param reason           the counts the port decision rests on, in words
 */
public record Seam(String interfaceName,
                   String declaringModule,
                   List<Implementor> implementors,
                   List<String> consumers,
                   List<String> importingModules,
                   boolean portCandidate,
                   String reason) {

    /**
     * One class that implements the interface, with the module it belongs to.
     */
    public record Implementor(String className, String module) {
    }

    public int implementorCount() {
        return implementors.size();
    }

    public int consumerCount() {
        return consumers.size();
    }

    public int importingModuleCount() {
        return importingModules.size();
    }
}
