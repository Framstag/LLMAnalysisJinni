package com.framstag.llmaj.tools.history;

/**
 * An author that changed a module, with the number of commits of that author in the module.
 *
 * <p>The count is the number of commits the author made for the changed files of the module, so that a
 * module whose authorship is concentrated can be told apart from a module that many people work on.
 */
public record ModuleAuthor(String name, int commitCount) {
}
