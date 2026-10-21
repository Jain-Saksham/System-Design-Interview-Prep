package com.sdprep.urlshortener.core;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String code) {
        super("No link for code: " + code);
    }
}
