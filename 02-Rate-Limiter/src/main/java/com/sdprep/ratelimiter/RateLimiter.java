package com.sdprep.ratelimiter;

/** Decides whether a client's request may proceed right now. Implementations are thread-safe. */
public interface RateLimiter {

    /**
     * @param clientId whatever we limit by: user ID, API key, or IP address
     * @return true to allow the request, false to reject it (HTTP 429 Too Many Requests)
     */
    boolean tryAcquire(String clientId);
}
