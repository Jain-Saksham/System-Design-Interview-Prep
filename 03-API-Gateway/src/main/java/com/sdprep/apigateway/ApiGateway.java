package com.sdprep.apigateway;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Runs the gateway as an HTTP server. One instance here; many behind a load balancer in production. */
public class ApiGateway {

    private final HttpServer server;

    private ApiGateway(HttpServer server) {
        this.server = server;
    }

    /** @param port 0 picks any free port (useful in tests) */
    public static ApiGateway start(int port, GatewayHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", handler);
        // A gateway mostly waits on backends, so cheap virtual threads fit well.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return new ApiGateway(server);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }
}
