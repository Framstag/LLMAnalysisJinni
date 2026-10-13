package com.framstag.llmaj.tools.java.language;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * How often one token occurs in a module, split by the channel it was found in.
 *
 * <p>The class is a mutable counter: a module report of a real project is walked once and every occurrence
 * increments the channel it came from. Repetition inside one name counts, so {@code ProjectProject}
 * contributes two occurrences of {@code project}.
 */
public final class TermOccurrences {

    private final Map<VocabularyChannel, Integer> occurrencesByChannel = new EnumMap<>(VocabularyChannel.class);

    public void add(VocabularyChannel channel) {
        add(channel, 1);
    }

    public void add(VocabularyChannel channel, int count) {
        if (count <= 0) {
            return;
        }

        occurrencesByChannel.merge(channel, count, Integer::sum);
    }

    /**
     * @return the occurrences over all channels
     */
    public int getOccurrences() {
        int total = 0;

        for (int occurrences : occurrencesByChannel.values()) {
            total += occurrences;
        }

        return total;
    }

    public int getOccurrences(VocabularyChannel channel) {
        return occurrencesByChannel.getOrDefault(channel, 0);
    }

    public Set<VocabularyChannel> getChannels() {
        return Collections.unmodifiableSet(occurrencesByChannel.keySet());
    }
}
