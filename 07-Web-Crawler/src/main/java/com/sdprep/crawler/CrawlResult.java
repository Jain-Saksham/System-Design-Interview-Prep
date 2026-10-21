package com.sdprep.crawler;

import java.net.URI;
import java.util.List;

/**
 * @param crawled            pages fetched successfully and indexed, in crawl order
 * @param blockedByRobots    URLs skipped because robots.txt disallows them
 * @param duplicateContent   pages whose content matched an already-crawled page (links not followed)
 * @param failed             URLs that returned a non-200 status
 */
public record CrawlResult(List<CrawledPage> crawled, List<URI> blockedByRobots,
                          List<URI> duplicateContent, List<URI> failed) {

    public record CrawledPage(URI url, int depth, long fetchedAtMillis) {
    }
}
