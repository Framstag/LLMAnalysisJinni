package com.framstag.llmaj.tools.java;

import java.util.List;

/**
 * One anchor candidate of the modernisation anchor analysis: the element a modernisation can attach to, the
 * module it belongs to, the counted evidence it rests on and the name of the analysis that counted that
 * evidence.
 *
 * <p>The evidence is a short list of strings that carry the counted numbers, so that an anchor the analysis
 * forms from this candidate can name the number behind it. The source names the tool that counted the
 * evidence, so that a reader can find the report it was written to.
 */
public record AnchorCandidate(String element,
                              String module,
                              List<String> evidence,
                              String source) {
}
