package com.sdprep.autocomplete;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutocompleteServiceTest {

    @Test
    void newSearchesAppearOnlyAfterRebuild() {
        AutocompleteService service = new AutocompleteService(Map.of("apple", 10L), new QueryCounter(), Set.of(), 5);

        for (int i = 0; i < 20; i++) {
            service.recordSearch("Apricot");
        }
        assertEquals(List.of("apple"), queries(service.suggest("ap")));

        service.rebuild();
        assertEquals(List.of("apricot", "apple"), queries(service.suggest("ap")));
    }

    @Test
    void recentSearchesAddToHistoricalCounts() {
        AutocompleteService service = new AutocompleteService(Map.of("apple", 10L, "apricot", 12L), new QueryCounter(), Set.of(), 5);

        for (int i = 0; i < 5; i++) {
            service.recordSearch("apple");
        }
        service.rebuild();

        assertEquals(new Suggestion("apple", 15), service.suggest("ap").get(0));
    }

    @Test
    void blockedQueriesAreNeverSuggested() {
        AutocompleteService service = new AutocompleteService(Map.of("bad word", 999L, "badminton", 5L),
                new QueryCounter(), Set.of("Bad Word"), 5);

        service.recordSearch("bad word");
        service.rebuild();

        assertEquals(List.of("badminton"), queries(service.suggest("bad")));
    }

    @Test
    void blankSearchesAreIgnored() {
        QueryCounter counter = new QueryCounter();
        counter.record("   ");
        assertTrue(counter.snapshot().isEmpty());
    }

    private static List<String> queries(List<Suggestion> suggestions) {
        return suggestions.stream().map(Suggestion::query).toList();
    }
}
