package com.sdprep.urlshortener.core;

public class AliasTakenException extends RuntimeException {
    public AliasTakenException(String alias) {
        super("Alias already in use: " + alias);
    }
}
