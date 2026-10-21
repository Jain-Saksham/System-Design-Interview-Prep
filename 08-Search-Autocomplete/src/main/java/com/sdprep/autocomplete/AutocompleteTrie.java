package com.sdprep.autocomplete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A prefix tree where every node stores the top-k completions for its prefix, computed once at build time.
 * A lookup walks one node per typed character and returns the stored list: O(prefix length),
 * no matter how many queries share the prefix.
 *
 * <p>Immutable after construction, so many threads can read it while a new one is being built.
 */
public final class AutocompleteTrie {

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        final List<Suggestion> topSuggestions = new ArrayList<>(); // sorted best first, at most k
    }

    private final Node root = new Node();
    private final int k;

    private AutocompleteTrie(int k) {
        this.k = k;
    }

    /** @param countsByQuery normalized query → search count */
    public static AutocompleteTrie build(Map<String, Long> countsByQuery, int k) {
        AutocompleteTrie trie = new AutocompleteTrie(k);
        countsByQuery.forEach((query, count) -> trie.insert(new Suggestion(query, count)));
        return trie;
    }

    public List<Suggestion> suggest(String prefix) {
        String normalized = QueryNormalizer.normalize(prefix);
        if (normalized.isEmpty()) {
            return List.of(); // nothing typed yet: no suggestions (or show trending, out of scope)
        }
        Node node = root;
        for (char c : normalized.toCharArray()) {
            node = node.children.get(c);
            if (node == null) {
                return List.of();
            }
        }
        return List.copyOf(node.topSuggestions);
    }

    /** Offers the query to the top-k list of every prefix node along its path. */
    private void insert(Suggestion suggestion) {
        Node node = root;
        for (char c : suggestion.query().toCharArray()) {
            node = node.children.computeIfAbsent(c, unused -> new Node());
            offer(node.topSuggestions, suggestion);
        }
    }

    private void offer(List<Suggestion> top, Suggestion candidate) {
        int position = 0;
        while (position < top.size() && Suggestion.BEST_FIRST.compare(top.get(position), candidate) < 0) {
            position++;
        }
        if (position < k) {
            top.add(position, candidate);
            if (top.size() > k) {
                top.remove(top.size() - 1);
            }
        }
    }
}
