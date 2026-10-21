package com.sdprep.apigateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real HTTP: test client → gateway → fake backends, all on localhost. */
class ApiGatewayEndToEndTest {

    private static final String VALID_KEY = "key-123";
    private static final int RATE_LIMIT = 3;

    private final HttpClient client = HttpClient.newHttpClient();
    private FakeBackend userService;
    private FakeBackend slowService;
    private ApiGateway gateway;

    @BeforeEach
    void startServers() throws IOException {
        userService = FakeBackend.start("user-service", Duration.ZERO);
        slowService = FakeBackend.start("slow-service", Duration.ofSeconds(2));
        URI nothingListening = closedPortUri();

        RouteTable routes = new RouteTable(List.of(
                new Route("/users", userService.baseUri()),
                new Route("/reports", slowService.baseUri()),
                new Route("/legacy", nothingListening)));
        GatewayHandler handler = new GatewayHandler(routes,
                new ApiKeyAuthenticator(Map.of(VALID_KEY, "web-app")),
                new FixedWindowRateLimiter(RATE_LIMIT, 60_000, System::currentTimeMillis),
                new BackendClient(Duration.ofMillis(500), Duration.ofMillis(300)));
        gateway = ApiGateway.start(0, handler);
    }

    @AfterEach
    void stopServers() {
        gateway.stop();
        userService.stop();
        slowService.stop();
    }

    @Test
    void forwardsToMatchingBackendWithClientIdentity() throws Exception {
        HttpResponse<String> response = get("/users/42?fields=name", VALID_KEY);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"service\":\"user-service\""));
        assertTrue(response.body().contains("\"path\":\"/users/42?fields=name\""));
        assertTrue(response.body().contains("\"clientId\":\"web-app\""));
        assertTrue(response.body().contains("\"apiKeyForwarded\":false"));
        assertTrue(response.headers().firstValue(GatewayHandler.REQUEST_ID_HEADER).isPresent());
    }

    @Test
    void rejectsMissingOrWrongApiKey() throws Exception {
        assertEquals(401, get("/users/1", null).statusCode());
        assertEquals(401, get("/users/1", "wrong").statusCode());
    }

    @Test
    void unknownPathIs404() throws Exception {
        assertEquals(404, get("/payments/1", VALID_KEY).statusCode());
    }

    @Test
    void requestsOverTheLimitGet429() throws Exception {
        for (int i = 0; i < RATE_LIMIT; i++) {
            assertEquals(200, get("/users/" + i, VALID_KEY).statusCode());
        }
        assertEquals(429, get("/users/99", VALID_KEY).statusCode());
    }

    @Test
    void unreachableBackendIs502AndSlowBackendIs504() throws Exception {
        assertEquals(502, get("/legacy/1", VALID_KEY).statusCode());
        assertEquals(504, get("/reports/1", VALID_KEY).statusCode());
    }

    @Test
    void rejectedRequestsNeverReachTheBackend() throws Exception {
        HttpResponse<String> response = get("/users/1", null);
        assertFalse(response.body().contains("user-service"));
    }

    private HttpResponse<String> get(String path, String apiKey) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + gateway.port() + path));
        if (apiKey != null) {
            request.header(GatewayHandler.API_KEY_HEADER, apiKey);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A localhost URI on a port that was free a moment ago and has nothing listening now. */
    private static URI closedPortUri() throws IOException {
        FakeBackend temporary = FakeBackend.start("temporary", Duration.ZERO);
        URI uri = temporary.baseUri();
        temporary.stop();
        return uri;
    }
}
