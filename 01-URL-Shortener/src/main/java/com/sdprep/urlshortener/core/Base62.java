package com.sdprep.urlshortener.core;

/**
 * Converts a numeric ID into a short, URL-safe string using 0-9, a-z, A-Z.
 * 7 characters cover 62^7 ≈ 3.5 trillion IDs.
 */
public final class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative: " + id);
        }
        if (id == 0) {
            return "0";
        }
        StringBuilder code = new StringBuilder();
        while (id > 0) {
            code.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return code.reverse().toString(); // digits were produced least-significant first
    }
}
