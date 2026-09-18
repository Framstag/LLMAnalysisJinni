package com.framstag.llmaj.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Locates and parses the JSON payload of a model response.
 * <p>
 * A model does not always answer with a bare JSON document: it may explain itself first, wrap the
 * answer in a code fence, or do both. The payload is therefore located structurally - the response
 * is scanned for candidates, each candidate is sliced from its opening bracket to its matching close
 * with a scan that knows about strings and escapes, and the first candidate that parses wins. Code
 * fences only influence the order in which candidates are tried: a model that reasons before it
 * answers puts its answer in the trailing fenced block.
 * <p>
 * A candidate that does not parse is retried once with the unescaped-quote repair of
 * {@link JsonHelper}. The repair is a fallback, never a step, so it cannot damage a payload that
 * already parses.
 */
public class ResponsePayloadParser {

    private static final Logger logger = LoggerFactory.getLogger(ResponsePayloadParser.class);

    /**
     * How many candidates are tried before a response is given up on.
     */
    private static final int MAX_CANDIDATES = 10;

    /**
     * How much of a response is kept for a diagnostic. The whole body is of no use to the reader and
     * would end up in a TUI frame or in the engine log file.
     */
    public static final int EXCERPT_LENGTH = 200;

    private final ObjectMapper mapper;

    public ResponsePayloadParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parses the JSON payload of a response.
     *
     * @param response the response text as the model returned it
     * @return the parsed payload
     * @throws ResponsePayloadException when no payload can be located, or the located payload does
     *                                  not parse
     */
    public JsonNode parse(String response) throws ResponsePayloadException {
        String text = response == null ? "" : response;

        // Preferred: a candidate that carries an actual document. An empty fragment quoted in prose
        // must not win over the payload that follows it.
        JsonNode parsed = parseFirstMatching(text, true);

        if (parsed == null) {
            // Second chance: the response carries nothing but an empty document, which is a valid
            // answer to a schema without required properties.
            parsed = parseFirstMatching(text, false);
        }

        if (parsed != null) {
            return parsed;
        }

        throw failure(text);
    }

    /**
     * The payload of a response as text, best effort and without throwing: the located payload when
     * one is found, otherwise the response itself.
     *
     * @param response the response text as the model returned it
     * @return the located payload, or the response when no payload was located
     */
    public String payloadText(String response) {
        String text = response == null ? "" : response;

        for (boolean requireNonEmpty : new boolean[]{true, false}) {
            for (String candidate : candidates(text)) {
                if (asDocument(candidate, requireNonEmpty) != null) {
                    return candidate;
                }

                String repaired = JsonHelper.fixJsonDocument(candidate);

                if (!repaired.equals(candidate) && structurePreserved(candidate, repaired)
                        && asDocument(repaired, requireNonEmpty) != null) {
                    return repaired;
                }
            }
        }

        return text;
    }

    private JsonNode parseFirstMatching(String text, boolean requireNonEmpty) {
        List<String> candidates = candidates(text);

        for (String candidate : candidates) {
            JsonNode parsed = asDocument(candidate, requireNonEmpty);

            if (parsed != null) {
                if (!candidate.equals(text.strip())) {
                    logger.debug("Located the JSON payload ({} of {} characters, {} candidates considered)",
                            candidate.length(), text.length(), candidates.size());
                }

                return parsed;
            }

            JsonNode repaired = repairAsDocument(candidate, requireNonEmpty);

            if (repaired != null) {
                logger.debug("Repaired unescaped quotes in a located JSON payload of {} characters",
                        candidate.length());
                return repaired;
            }
        }

        return null;
    }

    /**
     * Parses a candidate, optionally insisting that it carries something.
     */
    private JsonNode asDocument(String candidate, boolean requireNonEmpty) {
        JsonNode parsed = parseOrNull(candidate);

        if (parsed == null || (requireNonEmpty && isEmptyDocument(parsed))) {
            return null;
        }

        return parsed;
    }

    private JsonNode parseOrNull(String text) {
        try {
            return mapper.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static boolean isEmptyDocument(JsonNode parsed) {
        if (parsed.isObject() || parsed.isArray()) {
            return parsed.isEmpty();
        }

        return true;
    }

    /**
     * Repairs a candidate that does not parse and accepts the result only when the repair left the
     * structure of the document alone. Escaping quotes can otherwise turn a broken object into a
     * string, which would parse and be stored as a wrong payload for the task.
     */
    private JsonNode repairAsDocument(String candidate, boolean requireNonEmpty) {
        String repaired = JsonHelper.fixJsonDocument(candidate);

        if (repaired.equals(candidate) || !structurePreserved(candidate, repaired)) {
            return null;
        }

        return asDocument(repaired, requireNonEmpty);
    }

    /**
     * True when the brackets outside strings are the same before and after the repair, so the repair
     * changed the escaping but not the shape of the document.
     */
    static boolean structurePreserved(String before, String after) {
        return bracketSkeleton(before).equals(bracketSkeleton(after));
    }

    private static String bracketSkeleton(String text) {
        StringBuilder skeleton = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }

                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '}' || c == '[' || c == ']') {
                skeleton.append(c);
            }
        }

        return skeleton.toString();
    }

    private ResponsePayloadException failure(String text) {
        String excerpt = excerptOf(text);

        if (text.isBlank()) {
            return new ResponsePayloadException(ResponsePayloadException.Condition.NO_PAYLOAD_LOCATED,
                    "No JSON payload found in the response: the model returned no response text",
                    excerpt, text.length());
        }

        List<String> candidates = candidates(text);

        if (candidates.isEmpty()) {
            logger.warn("No JSON payload located in the response ({} characters, first {}: \"{}\")",
                    text.length(), excerpt.length(), excerpt);

            return new ResponsePayloadException(ResponsePayloadException.Condition.NO_PAYLOAD_LOCATED,
                    "No JSON payload found in the response (" + text.length() + " characters, first "
                            + excerpt.length() + ": \"" + excerpt + "\")",
                    excerpt, text.length());
        }

        // A candidate was located and none of them parses, so the parser message of the first one
        // describes what is wrong with the payload.
        String parseMessage = parseErrorMessage(candidates.getFirst());

        logger.warn("The located JSON payload could not be parsed: {} ({} characters, first {}: \"{}\")",
                parseMessage, text.length(), excerpt.length(), excerpt);

        return new ResponsePayloadException(ResponsePayloadException.Condition.PAYLOAD_NOT_PARSEABLE,
                "JSON payload found in the response is not parseable: " + parseMessage + " (" + text.length()
                        + " characters, first " + excerpt.length() + ": \"" + excerpt + "\")",
                excerpt, text.length());
    }

    private String parseErrorMessage(String candidate) {
        try {
            mapper.readTree(candidate);
        } catch (JsonProcessingException e) {
            return e.getOriginalMessage();
        }

        return "the payload does not carry a JSON document";
    }

    private static String excerptOf(String text) {
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }

        return text.substring(0, EXCERPT_LENGTH);
    }

    /**
     * The candidate payloads of a response, best first: the blocks of the trailing code fence, then
     * every bracketed region in the order it appears.
     */
    static List<String> candidates(String response) {
        List<String> candidates = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return candidates;
        }

        List<int[]> fencedBlocks = fencedBlocks(response);

        for (int i = fencedBlocks.size() - 1; i >= 0 && candidates.size() < MAX_CANDIDATES; i--) {
            int[] block = fencedBlocks.get(i);
            addBracketedRegions(response, block[0], block[1], candidates);
        }

        addBracketedRegions(response, 0, response.length(), candidates);

        return candidates;
    }

    private static void addBracketedRegions(String text, int from, int to, List<String> candidates) {
        int consumedUntil = from;

        for (int i = from; i < to && candidates.size() < MAX_CANDIDATES; i++) {
            // Only maximal regions: an object nested inside a located one is part of it, not a payload
            // of its own. Without this, a fragment of a broken payload (an array element, say) would
            // win as a parseable document and hide the fact that the payload is malformed.
            if (i < consumedUntil) {
                continue;
            }

            char c = text.charAt(i);

            if (c != '{' && c != '[') {
                continue;
            }

            int close = matchingClose(text, i, to);

            if (close < 0) {
                continue;
            }

            String candidate = text.substring(i, close + 1);

            if (!candidates.contains(candidate)) {
                candidates.add(candidate);
            }

            consumedUntil = close + 1;
        }
    }

    /**
     * Index of the bracket that closes the one at the given index, ignoring brackets inside strings
     * and escaped characters.
     *
     * @return the index of the closing bracket, or -1 when it is missing
     */
    static int matchingClose(String text, int openIndex, int limit) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openIndex; i < limit; i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }

                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;

                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * The content regions of the code fences in the response, in the order they appear.
     */
    private static List<int[]> fencedBlocks(String text) {
        List<int[]> blocks = new ArrayList<>();
        int lineStart = 0;
        int contentStart = -1;
        char fenceChar = 0;
        int fenceLength = 0;

        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            boolean lastLine = lineEnd < 0;
            int end = lastLine ? text.length() : lineEnd;
            String line = text.substring(lineStart, end).strip();

            if (contentStart < 0) {
                int fence = fenceLengthOf(line);

                if (fence > 0) {
                    fenceChar = line.charAt(0);
                    fenceLength = fence;
                    contentStart = lastLine ? text.length() : end + 1;
                }
            } else if (isFenceClose(line, fenceChar, fenceLength)) {
                blocks.add(new int[]{contentStart, lineStart});
                contentStart = -1;
            }

            if (lastLine) {
                break;
            }

            lineStart = end + 1;
        }

        return blocks;
    }

    private static int fenceLengthOf(String line) {
        if (line.length() < 3) {
            return 0;
        }

        char c = line.charAt(0);

        if (c != '`' && c != '~') {
            return 0;
        }

        int length = 0;

        while (length < line.length() && line.charAt(length) == c) {
            length++;
        }

        return length >= 3 ? length : 0;
    }

    private static boolean isFenceClose(String line, char fenceChar, int fenceLength) {
        if (line.isEmpty() || line.charAt(0) != fenceChar) {
            return false;
        }

        int length = 0;

        while (length < line.length() && line.charAt(length) == fenceChar) {
            length++;
        }

        return length >= fenceLength && line.substring(length).isBlank();
    }
}
