# API Gateway: Interview Questions

**1. What belongs in an API gateway, and what doesn't?**
Cross-cutting concerns: TLS, authentication, rate limiting, routing, request IDs, access logs, CORS, maybe response caching. Business logic does **not** belong there; it couples the gateway to every service and turns it into a deployment bottleneck.

**2. API gateway vs load balancer vs service mesh?**
A load balancer spreads traffic across instances of one service (L4/L7). A gateway handles **north-south** traffic (outside → inside) with API-level concerns (auth, limits, routing by path). A service mesh (Istio/Linkerd sidecars) handles **east-west** traffic between services (mTLS, retries, telemetry).

**3. How does authentication work at the gateway?**
Either look up the API key (stored hashed, cached in the gateway) or validate a JWT locally using the identity provider's public keys (JWKS, cached), so there's no network call per request. The gateway then forwards a verified identity header and strips the original credential.

**4. Why can backends trust `X-Client-Id`?**
Only if they're unreachable except via the gateway: private network, network policies, or mTLS between gateway and services. Otherwise anyone could forge the header. The gateway also overwrites any client-supplied `X-Client-Id`.

**5. How do you handle a slow or failing backend?**
Per-route timeouts (504 when exceeded), a circuit breaker per backend (after N failures, fail fast with 503 for a cool-down period, then probe), and separate connection pools so one bad backend can't exhaust resources used by others.

**6. Should the gateway retry failed requests?**
Only idempotent ones (GET, or POST with an idempotency key), with exponential backoff and jitter, and a retry budget (e.g., at most 10% extra traffic). Blind retries during an outage multiply load and make it worse.

**7. Estimate the capacity needed.**
Assume 50k requests/sec at peak. If one instance handles ~5k req/sec (I/O-bound proxying; benchmark your own), you need ~10 instances, plus N+2 for zone failure and deploys, so ~12–15. Added latency per hop should stay around 1–5 ms.

**8. How do route changes get deployed without downtime?**
Store routes in a config store or registry. Instances watch for changes or poll, validate the new config, and swap it atomically in memory. Roll out gradually (canary) since a bad route can break traffic everywhere.

**9. What is a BFF, and when would you add one?**
A Backend-for-Frontend is a client-specific gateway (one for mobile, one for web) that aggregates multiple service calls into one response shaped for that client. Add it when clients' needs diverge enough that a generic gateway would grow client-specific logic.

**10. How do you avoid the gateway becoming a single point of failure?**
Run multiple stateless instances across availability zones behind a load balancer with health checks. Keep dependencies (Redis, config store) highly available, and fail open on a rate-limiter outage so it doesn't block all traffic.

**11. How do you trace a request across services?**
The gateway generates (or accepts) a request/trace ID, returns it to the client, and forwards it (`X-Request-Id` or W3C `traceparent`). Every service logs it and propagates it, so you can follow one request end to end.

**Likely follow-ups:** API versioning (`/v1/...` routes or header-based) · canary routing by percentage · WebSocket support through the gateway · caching GET responses at the gateway · protecting against large payloads (body size limits).
