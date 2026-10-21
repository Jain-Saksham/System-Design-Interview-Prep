package com.sdprep.urlshortener.core;

import java.time.Instant;

/** One code → long URL mapping. {@code expiresAt} is null for links that never expire. */
public record ShortUrl(String code, String longUrl, Instant createdAt, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
