package com.sdprep.autocomplete;

import java.util.Comparator;

/** A complete query and how often it was searched. */
public record Suggestion(String query, long count) {

    /** Most searched first; ties broken alphabetically so results are stable. */
    public static final Comparator<Suggestion> BEST_FIRST =
            Comparator.comparingLong(Suggestion::count).reversed().thenComparing(Suggestion::query);
}
