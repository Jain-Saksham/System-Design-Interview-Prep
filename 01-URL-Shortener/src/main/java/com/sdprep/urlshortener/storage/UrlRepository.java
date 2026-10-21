package com.sdprep.urlshortener.storage;

import com.sdprep.urlshortener.core.ShortUrl;

import java.util.Optional;

/** Storage seam: in-memory locally, PostgreSQL (+ Redis cache) in production. */
public interface UrlRepository {

    /**
     * Saves only if the code is not already used. Must be atomic.
     * In PostgreSQL this is an INSERT on a table whose primary key is {@code code}.
     *
     * @return true if saved, false if the code already exists
     */
    boolean saveIfAbsent(ShortUrl shortUrl);

    Optional<ShortUrl> findByCode(String code);
}
