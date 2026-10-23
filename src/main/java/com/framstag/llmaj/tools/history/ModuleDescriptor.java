package com.framstag.llmaj.tools.history;

/**
 * A build module of the project, as the analysis state knows it.
 *
 * <p>The name of the module is the name the analysis uses for it, the path is the directory of the module
 * relative to the project directory. The path of a module that holds the whole project is empty or a dot.
 */
public record ModuleDescriptor(String name, String path) {
}
