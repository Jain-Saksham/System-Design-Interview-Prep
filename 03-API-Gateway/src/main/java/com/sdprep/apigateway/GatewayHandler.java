package com.sdprep.apigateway;

import com.sdprep.apigateway.BackendClient.BackendResponse;
import com.sdprep.apigateway.BackendClient.BackendTimeoutException;
import com.sdprep.apigateway.BackendClient.BackendUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every request passes the same checks, in order:
 * 1. authenticate → 2. rate limit → 3. route → 4. forward.
 * The first failing check answers the client immediately, so backends only see valid traffic.
 */
public class GatewayHandler implements HttpHandler {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String CLIENT_ID_HEADER = "X-Client-Id";

    private final RouteTable routes;
    private final ApiKeyAuthenticator authenticator;
    private final FixedWindowRateLimiter rateLimiter;
    private final BackendClient backendClient;

    public GatewayHandler(RouteTable routes, ApiKeyAuthenticator authenticator,
                          FixedWindowRateLimiter rateLimiter, BackendClient backendClient) {
        this.routes = routes;
        this.authenticator = authenticator;
        this.rateLimiter = rateLimiter;
        this.backendClient = backendClient;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // One ID per request, passed to the backend and returned to the client, ties logs together.
        String requestId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set(REQUEST_ID_HEADER, requestId);
        try (exchange) {
            Optional<String> clientId = authenticator.authenticate(exchange.getRequestHeaders().getFirst(API_KEY_HEADER));
            if (clientId.isEmpty()) {
                sendError(exchange, 401, "Missing or invalid API key", requestId);
                return;
            }
            if (!rateLimiter.tryAcquire(clientId.get())) {
                sendError(exchange, 429, "Rate limit exceeded", requestId);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Optional<Route> route = routes.find(path);
            if (route.isEmpty()) {
                sendError(exchange, 404, "No route for " + path, requestId);
                return;
            }
            forward(exchange, route.get(), clientId.get(), requestId);
        }
    }

    private void forward(HttpExchange exchange, Route route, String clientId, String requestId) throws IOException {
        URI target = route.targetFor(exchange.getRequestURI().toString());
        // The API key is NOT forwarded; backends trust the gateway-verified client ID instead.
        Map<String, String> headers = Map.of(REQUEST_ID_HEADER, requestId, CLIENT_ID_HEADER, clientId);
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        try {
            BackendResponse response = backendClient.forward(exchange.getRequestMethod(), target, headers, requestBody);
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            send(exchange, response.status(), response.body());
        } catch (BackendTimeoutException e) {
            sendError(exchange, 504, "Backend timed out", requestId);
        } catch (BackendUnavailableException e) {
            sendError(exchange, 502, "Backend unavailable", requestId);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message, String requestId) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        String json = "{\"error\":\"%s\",\"requestId\":\"%s\"}".formatted(message, requestId);
        send(exchange, status, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
