package com.sdprep.crawler;

import java.net.URI;

/**
 * Downloads one URL. Production: an HTTP client with timeouts, size limits, redirect handling,
 * and a descriptive User-Agent. Here an in-memory implementation keeps runs fast and offline.
 */
@FunctionalInterface
public interface PageFetcher {

    /** @return status 404 for missing pages, never null */
    FetchedPage fetch(URI url);

    record FetchedPage(URI url, int status, String body) {
        public boolean isOk() {
            return status == 200;
        }
    }
}
