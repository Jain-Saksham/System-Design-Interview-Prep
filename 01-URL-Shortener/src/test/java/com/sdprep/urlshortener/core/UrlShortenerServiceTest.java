package com.sdprep.urlshortener.core;

import com.sdprep.urlshortener.storage.InMemoryUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlShortenerServiceTest {

    private static final String LONG_URL = "https://example.com/articles/system-design";

    private MutableClock clock;
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new UrlShortenerService(new InMemoryUrlRepository(), new IdGenerator(), new ClickCounter(), clock);
    }

    @Test
    void shortenedCodeResolvesToOriginalUrl() {
        ShortUrl link = service.shorten(LONG_URL, null, null);

        assertEquals("1000", link.code()); // first generated ID, Base62-encoded
        assertEquals(LONG_URL, service.resolve(link.code()));
    }

    @Test
    void sameUrlShortenedTwiceGetsDifferentCodes() {
        assertNotEquals(service.shorten(LONG_URL, null, null).code(),
                        service.shorten(LONG_URL, null, null).code());
    }

    @Test
    void customAliasIsUsedAsCode() {
        ShortUrl link = service.shorten(LONG_URL, "sys-design", null);

        assertEquals("sys-design", link.code());
        assertEquals(LONG_URL, service.resolve("sys-design"));
    }

    @Test
    void duplicateCustomAliasIsRejected() {
        service.shorten(LONG_URL, "taken", null);

        assertThrows(AliasTakenException.class, () -> service.shorten("https://other.com", "taken", null));
    }

    @Test
    void generatedCodeSkipsAnAliasThatAlreadyUsesIt() {
        service.shorten(LONG_URL, "1000", null); // custom alias equal to the first generated code

        ShortUrl generated = service.shorten("https://other.com", null, null);

        assertEquals("1001", generated.code());
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(InvalidRequestException.class, () -> service.shorten("not a url", null, null));
        assertThrows(InvalidRequestException.class, () -> service.shorten("ftp://example.com", null, null));
        assertThrows(InvalidRequestException.class, () -> service.shorten(LONG_URL, "a!", null));
        assertThrows(InvalidRequestException.class, () -> service.shorten(LONG_URL, "api", null));
    }

    @Test
    void linkStopsResolvingAfterExpiry() {
        ShortUrl link = service.shorten(LONG_URL, null, Duration.ofDays(1));

        clock.advance(Duration.ofHours(23));
        assertEquals(LONG_URL, service.resolve(link.code()));

        clock.advance(Duration.ofHours(1));
        assertThrows(LinkExpiredException.class, () -> service.resolve(link.code()));
    }

    @Test
    void unknownCodeIsNotFound() {
        assertThrows(LinkNotFoundException.class, () -> service.resolve("nope"));
    }

    @Test
    void resolvingCountsClicksButViewingDetailsDoesNot() {
        ShortUrl link = service.shorten(LONG_URL, null, null);

        service.resolve(link.code());
        service.resolve(link.code());
        service.getActive(link.code());

        assertEquals(2, service.clicksFor(link.code()));
    }

    /** A clock that tests can move forward. */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
