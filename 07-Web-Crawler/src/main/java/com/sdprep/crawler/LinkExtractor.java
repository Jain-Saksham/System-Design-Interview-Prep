package com.sdprep.crawler;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds href="..." links in HTML. A regex is enough for well-formed demo pages;
 * production uses a real HTML parser (e.g. jsoup) that copes with broken markup and {@code <base>} tags.
 */
public final class LinkExtractor {

    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private LinkExtractor() {
    }

    /** Normalized, de-duplicated links in the order they appear. */
    public static Set<URI> extractLinks(URI pageUrl, String html) {
        Set<URI> links = new LinkedHashSet<>();
        Matcher matcher = HREF.matcher(html);
        while (matcher.find()) {
            UrlNormalizer.normalize(pageUrl, matcher.group(1)).ifPresent(links::add);
        }
        return links;
    }
}
