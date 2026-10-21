package com.sdprep.urlshortener;

import com.sdprep.urlshortener.core.ClickCounter;
import com.sdprep.urlshortener.core.IdGenerator;
import com.sdprep.urlshortener.core.UrlShortenerService;
import com.sdprep.urlshortener.storage.InMemoryUrlRepository;
import com.sdprep.urlshortener.storage.UrlRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the plain-Java core into Spring. The core classes have no Spring annotations,
 * so unit tests can create them with {@code new}.
 */
@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    UrlRepository urlRepository() {
        // Production: a PostgreSQL-backed repository (see docs/design-summary.md).
        return new InMemoryUrlRepository();
    }

    @Bean
    UrlShortenerService urlShortenerService(UrlRepository repository, Clock clock) {
        return new UrlShortenerService(repository, new IdGenerator(), new ClickCounter(), clock);
    }
}
