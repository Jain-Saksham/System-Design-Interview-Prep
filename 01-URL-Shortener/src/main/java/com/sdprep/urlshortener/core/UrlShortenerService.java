package com.sdprep.urlshortener.core;

import com.sdprep.urlshortener.storage.UrlRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/** Core business rules: create short links and resolve them. No framework code here. */
public class UrlShortenerService {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern ALIAS_FORMAT = Pattern.compile("[A-Za-z0-9_-]{3,30}");
    private static final Set<String> RESERVED_ALIASES = Set.of("api"); // would clash with /api routes

    private final UrlRepository repository;
    private final IdGenerator idGenerator;
    private final ClickCounter clickCounter;
    private final Clock clock;

    public UrlShortenerService(UrlRepository repository, IdGenerator idGenerator,
                               ClickCounter clickCounter, Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clickCounter = clickCounter;
        this.clock = clock;
    }

    /**
     * @param customAlias optional; null means "generate a code"
     * @param expiresIn   optional; null means "never expires"
     */
    public ShortUrl shorten(String longUrl, String customAlias, Duration expiresIn) {
        validateLongUrl(longUrl);
        Instant now = clock.instant();
        Instant expiresAt = expiresIn == null ? null : now.plus(expiresIn);

        if (customAlias != null) {
            return saveCustomAlias(customAlias, longUrl, now, expiresAt);
        }
        return saveWithGeneratedCode(longUrl, now, expiresAt);
    }

    /** Returns the long URL and counts the click. */
    public String resolve(String code) {
        ShortUrl shortUrl = getActive(code);
        clickCounter.recordClick(code);
        return shortUrl.longUrl();
    }

    /** Returns link details without counting a click. */
    public ShortUrl getActive(String code) {
        ShortUrl shortUrl = repository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));
        if (shortUrl.isExpired(clock.instant())) {
            throw new LinkExpiredException(code);
        }
        return shortUrl;
    }

    public long clicksFor(String code) {
        return clickCounter.clicksFor(code);
    }

    private ShortUrl saveCustomAlias(String alias, String longUrl, Instant now, Instant expiresAt) {
        if (!ALIAS_FORMAT.matcher(alias).matches() || RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new InvalidRequestException("Alias must be 3-30 characters of letters, digits, '-' or '_'");
        }
        ShortUrl shortUrl = new ShortUrl(alias, longUrl, now, expiresAt);
        // saveIfAbsent is atomic, so two users racing for the same alias can't both win.
        if (!repository.saveIfAbsent(shortUrl)) {
            throw new AliasTakenException(alias);
        }
        return shortUrl;
    }

    private ShortUrl saveWithGeneratedCode(String longUrl, Instant now, Instant expiresAt) {
        // A generated code can collide with an existing custom alias; if so, take the next ID.
        while (true) {
            String code = Base62.encode(idGenerator.nextId());
            ShortUrl shortUrl = new ShortUrl(code, longUrl, now, expiresAt);
            if (repository.saveIfAbsent(shortUrl)) {
                return shortUrl;
            }
        }
    }

    private static void validateLongUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank() || longUrl.length() > MAX_URL_LENGTH) {
            throw new InvalidRequestException("URL is required and must be at most " + MAX_URL_LENGTH + " characters");
        }
        try {
            URI uri = new URI(longUrl);
            boolean isWebUrl = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!isWebUrl || uri.getHost() == null) {
                throw new InvalidRequestException("Only absolute http(s) URLs can be shortened");
            }
        } catch (URISyntaxException e) {
            throw new InvalidRequestException("Malformed URL: " + longUrl);
        }
    }
}
