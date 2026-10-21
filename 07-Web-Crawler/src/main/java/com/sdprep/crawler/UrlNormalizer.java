package com.sdprep.crawler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns the many spellings of a URL into one canonical form, so "HTTP://Example.com:80/a#top"
 * and "http://example.com/a" count as the same page. Without this, the crawler fetches duplicates.
 */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    /** Resolves {@code href} against the page it was found on; empty if it isn't a crawlable http(s) URL. */
    public static Optional<URI> normalize(URI base, String href) {
        try {
            URI resolved = base.resolve(new URI(href.trim()));
            String scheme = resolved.getScheme() == null ? "" : resolved.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || resolved.getHost() == null) {
                return Optional.empty(); // mailto:, javascript:, ftp:, ...
            }
            int port = resolved.getPort();
            boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
            String path = resolved.getPath() == null || resolved.getPath().isEmpty() ? "/" : resolved.getPath();
            return Optional.of(new URI(scheme, null, resolved.getHost().toLowerCase(Locale.ROOT),
                    defaultPort ? -1 : port, path, resolved.getQuery(), null)); // null fragment: #top is the same page
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty(); // malformed links are common on the real web; skip them
        }
    }
}
