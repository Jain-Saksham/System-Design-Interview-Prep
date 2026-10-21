package com.sdprep.autocomplete;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serves suggestions from the current trie and periodically replaces it with one built from fresh counts.
 *
 * <p>The read path never waits for a rebuild: {@code rebuild()} builds a complete new trie, then swaps one
 * reference. Readers see either the old trie or the new one, never a half-built one.
 */
public class AutocompleteService {

    private final Map<String, Long> historicalCounts;
    private final QueryCounter recentSearches;
    private final Set<String> blockedQueries;
    private final int suggestionsPerPrefix;

    private volatile AutocompleteTrie currentTrie; // volatile: readers always see the latest swap

    public AutocompleteService(Map<String, Long> historicalCounts, QueryCounter recentSearches,
                               Set<String> blockedQueries, int suggestionsPerPrefix) {
        this.historicalCounts = Map.copyOf(historicalCounts);
        this.recentSearches = recentSearches;
        this.blockedQueries = blockedQueries.stream().map(QueryNormalizer::normalize).collect(Collectors.toSet());
        this.suggestionsPerPrefix = suggestionsPerPrefix;
        rebuild();
    }

    public List<Suggestion> suggest(String prefix) {
        return currentTrie.suggest(prefix);
    }

    /** Called when a user actually submits a search. Affects suggestions only after the next rebuild. */
    public void recordSearch(String query) {
        recentSearches.record(query);
    }

    /** Production: an offline job (e.g. hourly) builds the trie and ships it to every suggestion server. */
    public void rebuild() {
        Map<String, Long> merged = new HashMap<>();
        historicalCounts.forEach((query, count) -> merged.merge(QueryNormalizer.normalize(query), count, Long::sum));
        recentSearches.snapshot().forEach((query, count) -> merged.merge(query, count, Long::sum));
        merged.keySet().removeAll(blockedQueries); // never suggest offensive or legally removed queries

        currentTrie = AutocompleteTrie.build(merged, suggestionsPerPrefix);
    }
}
