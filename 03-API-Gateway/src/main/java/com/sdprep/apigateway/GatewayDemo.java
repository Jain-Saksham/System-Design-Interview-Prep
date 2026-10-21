package com.sdprep.apigateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Starts two fake services and the gateway in one process, then sends sample requests through it.
 * Every request goes over real HTTP on localhost.
 */
public class GatewayDemo {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        FakeBackend userService = FakeBackend.start("user-service", Duration.ZERO);
        FakeBackend orderService = FakeBackend.start("order-service", Duration.ZERO);

        RouteTable routes = new RouteTable(List.of(
                new Route("/users", userService.baseUri()),
                new Route("/orders", orderService.baseUri())));
        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(Map.of("key-mobile-123", "mobile-app"));
        FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(5, 60_000, System::currentTimeMillis);
        BackendClient backendClient = new BackendClient(Duration.ofSeconds(1), Duration.ofSeconds(2));

        ApiGateway gateway = ApiGateway.start(0, new GatewayHandler(routes, authenticator, rateLimiter, backendClient));
        String base = "http://localhost:" + gateway.port();
        System.out.println("Gateway on " + base + " (limit: 5 requests/minute per client)\n");

        call(base + "/users/42", "key-mobile-123");
        call(base + "/orders/7?expand=items", "key-mobile-123");
        call(base + "/users/42", null);
        call(base + "/payments/1", "key-mobile-123");
        call(base + "/users/1", "key-mobile-123");
        orderService.stop(); // simulate an outage
        call(base + "/orders/7", "key-mobile-123");
        call(base + "/users/2", "key-mobile-123"); // 6th request from mobile-app this minute

        gateway.stop();
        userService.stop();
    }

    private static void call(String url, String apiKey) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (apiKey != null) {
            request.header(GatewayHandler.API_KEY_HEADER, apiKey);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String path = URI.create(url).getPath();
        String body = response.body().replaceAll("\"requestId\":\"[^\"]+\"", "\"requestId\":\"…\"");
        System.out.printf("GET %-20s key=%-5s -> %d %s%n", path, apiKey == null ? "none" : "valid",
                response.statusCode(), body);
    }
}
