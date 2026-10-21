package com.sdprep.crawler;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlNormalizerTest {

    private static final URI BASE = URI.create("https://example.com/docs/page");

    @Test
    void resolvesRelativeLinks() {
        assertEquals(url("https://example.com/docs/other"), UrlNormalizer.normalize(BASE, "other"));
        assertEquals(url("https://example.com/about"), UrlNormalizer.normalize(BASE, "/about"));
        assertEquals(url("https://cdn.example.com/x"), UrlNormalizer.normalize(BASE, "//cdn.example.com/x"));
    }

    @Test
    void canonicalizesEquivalentSpellings() {
        assertEquals(url("http://example.com/a?q=1"), UrlNormalizer.normalize(BASE, "HTTP://Example.COM:80/a?q=1#section"));
        assertEquals(url("https://example.com/"), UrlNormalizer.normalize(BASE, "https://example.com"));
    }

    @Test
    void keepsNonDefaultPorts() {
        assertEquals(url("https://example.com:8443/a"), UrlNormalizer.normalize(BASE, "https://example.com:8443/a"));
    }

    @Test
    void rejectsNonWebAndMalformedLinks() {
        assertTrue(UrlNormalizer.normalize(BASE, "mailto:hi@example.com").isEmpty());
        assertTrue(UrlNormalizer.normalize(BASE, "javascript:void(0)").isEmpty());
        assertTrue(UrlNormalizer.normalize(BASE, "http://exa mple.com/").isEmpty());
    }

    private static Optional<URI> url(String value) {
        return Optional.of(URI.create(value));
    }
}
