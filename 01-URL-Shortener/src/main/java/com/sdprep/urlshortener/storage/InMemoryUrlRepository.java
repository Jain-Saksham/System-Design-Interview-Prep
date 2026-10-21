package com.sdprep.urlshortener.storage;

import com.sdprep.urlshortener.core.ShortUrl;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, single-process store. Data is lost on restart. */
public class InMemoryUrlRepository implements UrlRepository {

    private final Map<String, ShortUrl> urlsByCode = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(ShortUrl shortUrl) {
        return urlsByCode.putIfAbsent(shortUrl.code(), shortUrl) == null; // atomic check-and-insert
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        return Optional.ofNullable(urlsByCode.get(code));
    }
}
