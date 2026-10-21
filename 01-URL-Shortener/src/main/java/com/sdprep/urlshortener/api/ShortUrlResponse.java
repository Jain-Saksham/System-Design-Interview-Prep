package com.sdprep.urlshortener.api;

import com.sdprep.urlshortener.core.ShortUrl;

import java.time.Instant;

public record ShortUrlResponse(String code, String shortUrl, String longUrl,
                               Instant createdAt, Instant expiresAt, long clicks) {

    static ShortUrlResponse from(ShortUrl link, String baseUrl, long clicks) {
        return new ShortUrlResponse(link.code(), baseUrl + "/" + link.code(), link.longUrl(),
                link.createdAt(), link.expiresAt(), clicks);
    }
}
