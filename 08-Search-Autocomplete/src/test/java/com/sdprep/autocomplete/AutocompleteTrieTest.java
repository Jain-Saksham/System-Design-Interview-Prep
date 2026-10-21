package com.sdprep.autocomplete;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutocompleteTrieTest {

    private final AutocompleteTrie trie = AutocompleteTrie.build(Map.of(
            "car", 50L, "card", 80L, "care", 80L, "carpet", 10L, "cat", 200L, "dog", 5L), 3);

    @Test
    void returnsTopKByCountWithAlphabeticalTieBreak() {
        assertEquals(List.of("cat", "card", "care"), queries(trie.suggest("ca")));
        assertEquals(List.of("card", "care", "car"), queries(trie.suggest("car")));
    }

    @Test
    void fullQueryIsItsOwnSuggestion() {
        assertEquals(List.of("dog"), queries(trie.suggest("dog")));
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals(queries(trie.suggest("car")), queries(trie.suggest("  CAR ")));
    }

    @Test
    void unknownOrEmptyPrefixHasNoSuggestions() {
        assertTrue(trie.suggest("xyz").isEmpty());
        assertTrue(trie.suggest("cats").isEmpty());
        assertTrue(trie.suggest("   ").isEmpty());
    }

    @Test
    void neverReturnsMoreThanK() {
        assertEquals(3, trie.suggest("c").size());
    }

    private static List<String> queries(List<Suggestion> suggestions) {
        return suggestions.stream().map(Suggestion::query).toList();
    }
}
