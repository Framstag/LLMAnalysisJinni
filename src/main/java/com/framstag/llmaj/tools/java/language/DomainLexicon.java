package com.framstag.llmaj.tools.java.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tier of every token the analysis knows about, read from a versioned CSV file beside the analysis.
 *
 * <p>The file is data rather than code: the tier of a term is exactly the judgement an analyst wants to
 * correct without a rebuild, and the analysis records the version it used, so two runs stay comparable.
 * The format is a version line, a header line and one term per line:
 *
 * <pre>
 * Version,1
 * Term,Tier,Note
 * impl,technical,implementation suffix
 * realm,unclassified,domain term of the repository context
 * </pre>
 *
 * <p>Blank lines and lines starting with {@code #} are ignored anywhere in the file. A term that the file
 * does not list is {@link LexiconTier#UNCLASSIFIED}; a malformed file is an error, because a lexicon that
 * silently loses its entries would quietly reclassify a domain term as plumbing.
 */
public final class DomainLexicon {

    private static final String VERSION_KEYWORD = "Version";
    private static final List<String> EXPECTED_HEADER = List.of("Term", "Tier", "Note");

    private final String version;
    private final Path source;
    private final Map<String, LexiconTier> tierByTerm;

    private DomainLexicon(String version, Path source, Map<String, LexiconTier> tierByTerm) {
        this.version = version;
        this.source = source;
        this.tierByTerm = tierByTerm;
    }

    /**
     * @return an empty lexicon of the given version, in which every token is a candidate domain term
     */
    public static DomainLexicon empty(String version) {
        return new DomainLexicon(version, null, new HashMap<>());
    }

    public static DomainLexicon load(Path path) throws IOException {
        if (path == null) {
            throw new IOException("No domain lexicon file is configured");
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Domain lexicon file '" + path + "' does not exist");
        }

        List<String> lines = Files.readAllLines(path);

        String version = null;
        boolean headerSeen = false;
        Map<String, LexiconTier> tierByTerm = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split(",", 3);

            if (version == null) {
                if (!VERSION_KEYWORD.equals(fields[0])) {
                    throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                            + ": expected a '" + VERSION_KEYWORD + ",<version>' line before the header");
                }

                if (fields.length < 2 || fields[1].trim().isEmpty()) {
                    throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                            + ": the version line carries no version");
                }

                version = fields[1].trim();
                continue;
            }

            if (!headerSeen) {
                headerSeen = true;

                if (!EXPECTED_HEADER.get(0).equalsIgnoreCase(fields[0].trim())) {
                    throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                            + ": expected the header '" + String.join(",", EXPECTED_HEADER) + "'");
                }

                continue;
            }

            String term = fields[0].trim().toLowerCase();

            if (term.isEmpty()) {
                throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                        + ": the term is empty");
            }

            if (fields.length < 2) {
                throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                        + ": the tier of term '" + term + "' is missing");
            }

            LexiconTier tier;

            try {
                tier = LexiconTier.fromString(fields[1]);
            } catch (IllegalArgumentException e) {
                throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1) + ": " + e.getMessage());
            }

            if (tier == LexiconTier.UNCLASSIFIED) {
                throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                        + ": term '" + term + "' is listed as unclassified, which is what an unlisted term is");
            }

            LexiconTier recordedTier = tierByTerm.get(term);

            // A term listed twice would silently take the last tier, and a tier that depends on the line
            // order of a file is a classification nobody can reason about.
            if (recordedTier != null && recordedTier != tier) {
                throw new IOException("Domain lexicon file '" + path + "', line " + (i + 1)
                        + ": term '" + term + "' is already listed as " + recordedTier.getValue()
                        + " and cannot be " + tier.getValue() + " as well");
            }

            tierByTerm.put(term, tier);
        }

        if (version == null) {
            throw new IOException("Domain lexicon file '" + path + "' holds no '"
                    + VERSION_KEYWORD + ",<version>' line");
        }

        return new DomainLexicon(version, path, tierByTerm);
    }

    public String getVersion() {
        return version;
    }

    /**
     * @return the file the lexicon was read from, or null when the lexicon is empty
     */
    public Path getSource() {
        return source;
    }

    public int size() {
        return tierByTerm.size();
    }

    /**
     * @return the tier of the token, or {@link LexiconTier#UNCLASSIFIED} when the lexicon does not list it
     */
    public LexiconTier tierOf(String token) {
        if (token == null) {
            return LexiconTier.UNCLASSIFIED;
        }

        return tierByTerm.getOrDefault(token.toLowerCase(), LexiconTier.UNCLASSIFIED);
    }
}
