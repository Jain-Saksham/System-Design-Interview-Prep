package com.sdprep.autocomplete;

import java.util.Locale;

/** "  Java   Streams " → "java streams", so equivalent queries are counted and matched together. */
public final class QueryNormalizer {

    private QueryNormalizer() {
    }

    public static String normalize(String query) {
        return query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
