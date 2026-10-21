package com.sdprep.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * A stand-in microservice for the demo and tests. It echoes back what it received,
 * which makes it easy to see what the gateway forwarded.
 */
public class FakeBackend {

    private final HttpServer server;

    private FakeBackend(HttpServer server) {
        this.server = server;
    }

    public static FakeBackend start(String serviceName, Duration responseDelay) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> respond(exchange, serviceName, responseDelay));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return new FakeBackend(server);
    }

    public URI baseUri() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, String serviceName, Duration delay) throws IOException {
        sleep(delay);
        String json = """
                {"service":"%s","path":"%s","clientId":"%s","apiKeyForwarded":%b}"""
                .formatted(serviceName,
                        exchange.getRequestURI(),
                        exchange.getRequestHeaders().getFirst(GatewayHandler.CLIENT_ID_HEADER),
                        exchange.getRequestHeaders().containsKey(GatewayHandler.API_KEY_HEADER));
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
