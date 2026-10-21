package com.sdprep.crawler;

import com.sdprep.crawler.CrawlResult.CrawledPage;

import java.net.URI;
import java.util.List;

/** Crawls a small in-memory web across two sites, with simulated time to show politeness. */
public class CrawlerDemo {

    public static void main(String[] args) {
        InMemoryWeb web = new InMemoryWeb()
                .page("https://shop.example/robots.txt", "User-agent: *\nDisallow: /admin")
                .page("https://shop.example/", """
                        <a href="/products">Products</a> <a href="/admin">Admin</a>
                        <a href="https://blog.example/">Blog</a> <a href="https://ads.tracker/x">Ad</a>""")
                .page("https://shop.example/products", """
                        <a href="/products/1">Item 1</a> <a href="/products/2#reviews">Item 2</a> <a href="/">Home</a>""")
                .page("https://shop.example/products/1", "<p>Item 1</p>")
                .page("https://shop.example/products/2", "<p>Item 2</p> <a href='/index.html'>Home</a>")
                .page("https://shop.example/index.html", """
                        <a href="/products">Products</a> <a href="/admin">Admin</a>
                        <a href="https://blog.example/">Blog</a> <a href="https://ads.tracker/x">Ad</a>""")
                .page("https://blog.example/", "<a href='/post-1'>Post 1</a> <a href='/post-2'>Post 2</a>")
                .page("https://blog.example/post-1", "<p>Post 1</p>");

        Crawler crawler = new Crawler(web, CrawlClock.simulated(), 20, 3, 1_000);
        CrawlResult result = crawler.crawl(List.of(URI.create("https://shop.example/"), URI.create("https://blog.example/")));

        System.out.println("Politeness delay: 1000 ms per host. Fetch order (simulated time):");
        for (CrawledPage page : result.crawled()) {
            System.out.printf("  t=%4d ms  depth %d  %s%n", page.fetchedAtMillis(), page.depth(), page.url());
        }
        System.out.println("\nBlocked by robots.txt: " + result.blockedByRobots());
        System.out.println("Duplicate content:     " + result.duplicateContent());
        System.out.println("Failed (non-200):      " + result.failed());
        System.out.println("Never fetched:         https://ads.tracker/x (outside the seed hosts)");
    }
}
