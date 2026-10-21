# API Gateway: Design Summary

## Problem Statement
In a microservice system, clients would otherwise need to know every service's address, and every service would re-implement authentication, rate limiting, logging, and TLS. An API gateway is the single front door: it applies these cross-cutting concerns once and routes requests to the right service.

## Functional Requirements
1. Route requests to backend services by path (and optionally host, method, or version).
2. Authenticate callers (API keys or JWTs) and pass a verified identity downstream.
3. Enforce per-client rate limits.
4. Return clear errors when backends fail or are slow (502/503/504).
5. Attach a request ID for end-to-end tracing.

## Non-Functional Requirements
- **Low added latency:** a few milliseconds at most; it's on every request's path.
- **High availability:** if the gateway is down, everything is down. Run many stateless instances across zones.
- **Scalability:** scale horizontally with traffic; no per-request shared state except rate-limit counters.
- **Security:** TLS, credential validation, never forward raw credentials, protect backends from malformed or abusive traffic.
- **Observability:** consistent access logs, metrics, and traces for all traffic.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Load balancer** terminates TLS and spreads traffic across gateway instances.
- **Gateway pipeline** (each instance, in order): authenticate → rate limit → route → forward. The first failing step answers immediately, so backends only receive valid traffic.
- **Config store** holds routes and API keys; instances cache them in memory and refresh periodically.
- **Redis** holds shared rate-limit counters.
- **Backends** receive the request plus `X-Request-Id` and `X-Client-Id`, never the client's API key.
- **Observability:** access logs, metrics, and traces are emitted asynchronously, keyed by request ID.

## API Design
The gateway exposes the backends' APIs under one host. Its own contract is in headers and error codes:
```http
GET /orders/7 HTTP/1.1
X-API-Key: key-mobile-123

→ forwarded to order-service:
GET /orders/7
X-Request-Id: 3f2c...   X-Client-Id: mobile-app
```

| Gateway response | When |
|---|---|
| `401 Unauthorized` | Missing or invalid credentials |
| `429 Too Many Requests` | Client over its rate limit |
| `404 Not Found` | No route matches |
| `502 Bad Gateway` | Backend unreachable or returned garbage |
| `503 Service Unavailable` | Circuit breaker open / overloaded |
| `504 Gateway Timeout` | Backend too slow |

## Data Model
The gateway is stateless per request. Its configuration:

| Entity | Fields | Store |
|---|---|---|
| Route | `path_prefix`, `backend` (URL or service name), `timeout_ms`, `auth_required`, `rate_limit_rule` | Config store / service registry, cached in memory |
| API client | `client_id`, `api_key_hash`, `tier`, `status` | DB, cached in memory with short TTL |
| Rate-limit counter | `rl:{client}:{window}` → count | Redis with TTL |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Pipeline order: auth → limit → route** | Unauthenticated traffic is rejected before it can consume rate-limit budget or reach a backend. |
| **Longest-prefix routing** | Lets specific routes (`/orders/admin`) override general ones (`/orders`) without ordering mistakes. |
| **Strip credentials, forward identity** | Backends trust `X-Client-Id` from the gateway; a leaked internal log can't expose API keys. Backends must accept traffic only from the gateway (network policy or mTLS). |
| **Strict timeouts per route** | One slow backend must not tie up gateway threads and take down every route. |
| **Stateless instances** | Scale and replace instances freely; shared state lives in Redis and the config store. |
| **Virtual threads** (local) / async I/O (production) | A gateway mostly waits on I/O; blocking platform threads would cap concurrency. |

## Bottlenecks and Trade-offs
- **Single point of failure:** mitigate with many instances across zones behind a load balancer, plus health checks.
- **Extra network hop:** adds latency to every call. Keep gateway logic lightweight and co-locate with services.
- **Slow backend cascades:** timeouts, a circuit breaker per backend (fail fast with 503 when error rate is high), and bulkheads (separate connection pools per backend).
- **Retries amplify load:** retry only idempotent requests (GET, PUT with idempotency key), with backoff and a retry budget.
- **"Smart gateway" anti-pattern:** putting business logic in the gateway couples it to every service. Keep it to cross-cutting concerns; use a BFF (backend-for-frontend) for client-specific aggregation.

## Scaling Strategy
1. **One gateway instance** with in-memory config (this repo's shape).
2. **Several instances behind a load balancer;** move rate-limit counters to Redis and config to a shared store.
3. **Service discovery** (Consul, Kubernetes services) instead of static backend URLs; add circuit breakers and per-route timeouts.
4. **Per-client-type gateways or BFFs** (mobile, web, partner) when needs diverge.
5. **Multi-region:** gateways in each region with GeoDNS / anycast routing; regional rate limits.
