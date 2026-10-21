package com.sdprep.urlshortener.api;

/** Body of POST /api/urls. Only {@code longUrl} is required. */
public record ShortenRequest(String longUrl, String customAlias, Integer expiresInDays) {
}
