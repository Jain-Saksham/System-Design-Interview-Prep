package com.sdprep.crawler;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A tiny fake internet for the demo and tests. Records every fetch so tests can inspect behaviour. */
public class InMemoryWeb implements PageFetcher {

    private final Map<URI, String> pagesByUrl = new HashMap<>();
    private final List<URI> fetchLog = new ArrayList<>();

    public InMemoryWeb page(String url, String html) {
        pagesByUrl.put(URI.create(url), html);
        return this;
    }

    @Override
    public FetchedPage fetch(URI url) {
        fetchLog.add(url);
        String body = pagesByUrl.get(url);
        return body == null ? new FetchedPage(url, 404, "") : new FetchedPage(url, 200, body);
    }

    public List<URI> fetchLog() {
        return List.copyOf(fetchLog);
    }
}
