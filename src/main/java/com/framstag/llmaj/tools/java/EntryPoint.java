package com.framstag.llmaj.tools.java;

import java.util.List;

/**
 * The counted evidence of one class of the project.
 *
 * <p>An entry point candidate is a class a caller can reach a module through: a class that declares a main
 * method, a class that carries an annotation and a class that implements an interface. The record holds what
 * the raw Java module report knows about the class and nothing more: the module the class belongs to, the
 * category of the code it is written in, whether it declares a main method, the names of its annotations and
 * the interfaces it implements.
 *
 * <p>The names are raw. The tool does not know that an annotation marks an entry point, that an interface is
 * a service provider interface or that a main method is reachable from the outside: that judgement belongs to
 * the analysis that reads this evidence.
 *
 * @param className     the qualified name of the class
 * @param module        the module of the project the class belongs to
 * @param codeCategory  the category of the code the class is written in: production, test or generated
 * @param hasMainMethod whether the class declares a static method named main that returns nothing
 * @param annotations   the names of the annotations the class carries
 * @param interfaces    the names of the interfaces the class implements
 */
public record EntryPoint(String className,
                         String module,
                         String codeCategory,
                         boolean hasMainMethod,
                         List<String> annotations,
                         List<String> interfaces) {
}
