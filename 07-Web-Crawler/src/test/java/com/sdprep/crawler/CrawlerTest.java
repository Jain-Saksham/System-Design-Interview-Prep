package com.sdprep.crawler;

import com.sdprep.crawler.CrawlResult.CrawledPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerTest {

    private static final URI HOME = URI.create("https://site.test/");

    @Test
    void followsLinksAndFetchesEachUrlOnce() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/", "<a href='/a'>A</a> <a href='/b'>B</a>")
                .page("https://site.test/a", "<a href='/b'>B</a> <a href='/'>home</a>")  // cycle back home
                .page("https://site.test/b", "<a href='/a#top'>A again</a>");

        CrawlResult result = crawler(web, 10, 5).crawl(List.of(HOME));

        assertEquals(List.of("/", "/a", "/b"), paths(result));
        long pageFetches = web.fetchLog().stream().filter(url -> !url.getPath().equals("/robots.txt")).count();
        assertEquals(3, pageFetches);
    }

    @Test
    void respectsMaxDepth() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/", "<a href='/1'>")
                .page("https://site.test/1", "<a href='/2'>")
                .page("https://site.test/2", "<a href='/3'>");

        assertEquals(List.of("/", "/1"), paths(crawler(web, 10, 1).crawl(List.of(HOME))));
    }

    @Test
    void respectsMaxPages() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/", "<a href='/1'> <a href='/2'> <a href='/3'>")
                .page("https://site.test/1", "x").page("https://site.test/2", "y").page("https://site.test/3", "z");

        assertEquals(2, crawler(web, 2, 5).crawl(List.of(HOME)).crawled().size());
    }

    @Test
    void skipsUrlsDisallowedByRobotsTxt() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/robots.txt", "User-agent: *\nDisallow: /private")
                .page("https://site.test/", "<a href='/private/x'> <a href='/public'>")
                .page("https://site.test/private/x", "secret")
                .page("https://site.test/public", "hello");

        CrawlResult result = crawler(web, 10, 5).crawl(List.of(HOME));

        assertEquals(List.of(URI.create("https://site.test/private/x")), result.blockedByRobots());
        assertFalse(web.fetchLog().contains(URI.create("https://site.test/private/x")));
    }

    @Test
    void duplicateContentIsRecordedAndNotExpanded() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/", "<a href='/index.html'> same page")
                .page("https://site.test/index.html", "<a href='/index.html'> same page");

        CrawlResult result = crawler(web, 10, 5).crawl(List.of(HOME));

        assertEquals(List.of("/"), paths(result));
        assertEquals(List.of(URI.create("https://site.test/index.html")), result.duplicateContent());
    }

    @Test
    void staysOnSeedHostsAndRecordsBrokenLinks() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/", "<a href='https://elsewhere.test/'> <a href='/missing'>");

        CrawlResult result = crawler(web, 10, 5).crawl(List.of(HOME));

        assertTrue(web.fetchLog().stream().noneMatch(url -> url.getHost().equals("elsewhere.test")));
        assertEquals(List.of(URI.create("https://site.test/missing")), result.failed());
    }

    @Test
    void fetchesFromTheSameHostAreSpacedByThePolitenessDelay() {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://site.test/", "<a href='/1'> <a href='/2'>")
                .page("https://site.test/1", "one").page("https://site.test/2", "two");

        List<Long> fetchTimes = crawler(web, 10, 5).crawl(List.of(HOME)).crawled().stream()
                .map(CrawledPage::fetchedAtMillis).toList();

        assertEquals(List.of(0L, 500L, 1_000L), fetchTimes);
    }

    private static Crawler crawler(PageFetcher fetcher, int maxPages, int maxDepth) {
        return new Crawler(fetcher, CrawlClock.simulated(), maxPages, maxDepth, 500);
    }

    private static List<String> paths(CrawlResult result) {
        return result.crawled().stream().map(page -> page.url().getPath()).toList();
    }
}
