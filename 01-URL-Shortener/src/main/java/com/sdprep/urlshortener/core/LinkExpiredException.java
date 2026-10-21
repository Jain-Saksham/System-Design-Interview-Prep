package com.sdprep.urlshortener.core;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String code) {
        super("Link has expired: " + code);
    }
}
