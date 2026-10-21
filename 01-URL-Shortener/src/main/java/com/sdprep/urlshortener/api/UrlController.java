package com.sdprep.urlshortener.api;

import com.sdprep.urlshortener.core.InvalidRequestException;
import com.sdprep.urlshortener.core.ShortUrl;
import com.sdprep.urlshortener.core.UrlShortenerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@RestController
public class UrlController {

    private final UrlShortenerService service;
    private final String baseUrl;

    public UrlController(UrlShortenerService service,
                         @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortUrlResponse> shorten(@RequestBody ShortenRequest request) {
        ShortUrl link = service.shorten(request.longUrl(), request.customAlias(), toDuration(request.expiresInDays()));
        ShortUrlResponse body = ShortUrlResponse.from(link, baseUrl, 0);
        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    /** The hot path. 302 (not 301) so browsers don't cache the redirect and every click is counted. */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(longUrl)).build();
    }

    @GetMapping("/api/urls/{code}")
    public ShortUrlResponse details(@PathVariable String code) {
        return ShortUrlResponse.from(service.getActive(code), baseUrl, service.clicksFor(code));
    }

    private static Duration toDuration(Integer expiresInDays) {
        if (expiresInDays == null) {
            return null;
        }
        if (expiresInDays <= 0) {
            throw new InvalidRequestException("expiresInDays must be positive");
        }
        return Duration.ofDays(expiresInDays);
    }
}
