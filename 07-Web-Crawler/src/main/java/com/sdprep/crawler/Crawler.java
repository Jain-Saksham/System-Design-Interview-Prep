package com.sdprep.crawler;

import com.sdprep.crawler.CrawlResult.CrawledPage;
import com.sdprep.crawler.PageFetcher.FetchedPage;
import com.sdprep.crawler.UrlFrontier.Entry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Breadth-first crawler. The loop: take a polite URL from the frontier → check robots.txt → fetch →
 * skip duplicate content → extract links → queue unseen, in-scope links.
 *
 * <p>Single-threaded for clarity. Production runs many workers, each owning a set of hosts.
 */
public class Crawler {

    private final PageFetcher fetcher;
    private final CrawlClock clock;
    private final int maxPages;
    private final int maxDepth;
    private final long politenessDelayMillis;

    public Crawler(PageFetcher fetcher, CrawlClock clock, int maxPages, int maxDepth, long politenessDelayMillis) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.maxPages = maxPages;
        this.maxDepth = maxDepth;
        this.politenessDelayMillis = politenessDelayMillis;
    }

    /** Crawls outward from the seeds, staying on the seeds' hosts. */
    public CrawlResult crawl(List<URI> seeds) {
        UrlFrontier frontier = new UrlFrontier(politenessDelayMillis);
        Set<URI> seenUrls = new HashSet<>();          // production: a Bloom filter + persistent store
        Set<String> seenContentHashes = new HashSet<>();
        Map<String, RobotsRules> robotsByHost = new HashMap<>();
        Set<String> allowedHosts = new HashSet<>();

        List<CrawledPage> crawled = new ArrayList<>();
        List<URI> blocked = new ArrayList<>();
        List<URI> duplicates = new ArrayList<>();
        List<URI> failed = new ArrayList<>();

        // Robots.txt is checked when a URL is discovered, so blocked URLs never use up a politeness slot.
        Discovery discovery = (url, depth) -> {
            if (!allowedHosts.contains(url.getHost()) || !seenUrls.add(url)) {
                return;
            }
            RobotsRules robots = robotsByHost.computeIfAbsent(url.getHost(), host -> fetchRobots(url));
            if (robots.isAllowed(url.getPath())) {
                frontier.add(url, depth);
            } else {
                blocked.add(url);
            }
        };

        for (URI seed : seeds) {
            UrlNormalizer.normalize(seed, seed.toString()).ifPresent(url -> {
                allowedHosts.add(url.getHost());
                discovery.found(url, 0);
            });
        }

        while (!frontier.isEmpty() && crawled.size() < maxPages) {
            Optional<Entry> next = frontier.next(clock.nowMillis());
            if (next.isEmpty()) {
                clock.sleepUntil(frontier.nextReadyTime()); // every host with work is cooling down
                continue;
            }
            Entry entry = next.get();
            URI url = entry.url();

            FetchedPage page = fetcher.fetch(url);
            if (!page.isOk()) {
                failed.add(url);
                continue;
            }
            if (!seenContentHashes.add(sha256(page.body()))) {
                duplicates.add(url); // same content under another URL (mirrors, /index.html vs /)
                continue;
            }
            crawled.add(new CrawledPage(url, entry.depth(), clock.nowMillis()));

            if (entry.depth() < maxDepth) {
                LinkExtractor.extractLinks(url, page.body()).forEach(link -> discovery.found(link, entry.depth() + 1));
            }
        }
        return new CrawlResult(crawled, blocked, duplicates, failed);
    }

    /** Called for every URL found: seeds and extracted links. */
    @FunctionalInterface
    private interface Discovery {
        void found(URI url, int depth);
    }

    private RobotsRules fetchRobots(URI anyUrlOnHost) {
        FetchedPage robots = fetcher.fetch(anyUrlOnHost.resolve("/robots.txt"));
        return robots.isOk() ? RobotsRules.parse(robots.body()) : RobotsRules.allowAll();
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
