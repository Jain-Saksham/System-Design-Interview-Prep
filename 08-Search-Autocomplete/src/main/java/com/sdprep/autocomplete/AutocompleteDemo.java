package com.sdprep.autocomplete;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runnable walkthrough: top-3 suggestions per prefix, new searches applied on rebuild, blocked terms. */
public class AutocompleteDemo {

    public static void main(String[] args) {
        Map<String, Long> history = Map.of(
                "java", 900L, "javascript", 1_200L, "java interview questions", 400L,
                "jacket", 300L, "jaguar", 150L, "jam recipe", 80L, "jail break", 500L);
        AutocompleteService autocomplete = new AutocompleteService(history, new QueryCounter(), Set.of("jail break"), 3);

        show(autocomplete, "j");
        show(autocomplete, "jav");
        show(autocomplete, "JAVA ");
        show(autocomplete, "jz");

        System.out.println("\nUsers search \"java streams\" 1,000 times...");
        for (int i = 0; i < 1_000; i++) {
            autocomplete.recordSearch("Java  Streams");
        }
        show(autocomplete, "java s");
        System.out.println("  (not visible yet: counts are applied when the trie is rebuilt)");

        autocomplete.rebuild();
        System.out.println("\nAfter rebuild:");
        show(autocomplete, "java s");
        show(autocomplete, "jav");
    }

    private static void show(AutocompleteService autocomplete, String prefix) {
        List<String> suggestions = autocomplete.suggest(prefix).stream()
                .map(s -> s.query() + " (" + s.count() + ")")
                .toList();
        System.out.printf("  %-9s -> %s%n", "\"" + prefix + "\"", suggestions);
    }
}
