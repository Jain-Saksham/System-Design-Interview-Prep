package com.sdprep.crawler;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlFrontierTest {

    private final UrlFrontier frontier = new UrlFrontier(1_000);

    @Test
    void sameHostWaitsForPolitenessDelay() {
        frontier.add(URI.create("https://a.com/1"), 0);
        frontier.add(URI.create("https://a.com/2"), 0);

        assertEquals("/1", frontier.next(0).orElseThrow().url().getPath());
        assertTrue(frontier.next(999).isEmpty());
        assertEquals(1_000, frontier.nextReadyTime());
        assertEquals("/2", frontier.next(1_000).orElseThrow().url().getPath());
    }

    @Test
    void otherHostsAreServedWhileOneWaits() {
        frontier.add(URI.create("https://a.com/1"), 0);
        frontier.add(URI.create("https://a.com/2"), 0);
        frontier.add(URI.create("https://b.com/1"), 0);

        assertEquals("a.com", frontier.next(0).orElseThrow().url().getHost());
        assertEquals("b.com", frontier.next(0).orElseThrow().url().getHost());
    }
}
