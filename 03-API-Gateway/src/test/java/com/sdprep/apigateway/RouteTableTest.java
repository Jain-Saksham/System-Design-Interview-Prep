package com.sdprep.apigateway;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTableTest {

    private static final URI ORDERS = URI.create("http://orders:8080");
    private static final URI ADMIN = URI.create("http://admin:8080");

    private final RouteTable routes = new RouteTable(List.of(
            new Route("/orders", ORDERS),
            new Route("/orders/admin", ADMIN)));

    @Test
    void matchesPrefixAndSubPaths() {
        assertEquals(ORDERS, routes.find("/orders").orElseThrow().backendBaseUri());
        assertEquals(ORDERS, routes.find("/orders/7/items").orElseThrow().backendBaseUri());
    }

    @Test
    void longestPrefixWins() {
        assertEquals(ADMIN, routes.find("/orders/admin/stats").orElseThrow().backendBaseUri());
    }

    @Test
    void doesNotMatchPartialSegments() {
        assertTrue(routes.find("/ordersXYZ").isEmpty());
    }

    @Test
    void unknownPathHasNoRoute() {
        assertTrue(routes.find("/payments").isEmpty());
    }

    @Test
    void targetKeepsPathAndQuery() {
        Route route = new Route("/orders", ORDERS);
        assertEquals(URI.create("http://orders:8080/orders/7?expand=items"), route.targetFor("/orders/7?expand=items"));
    }
}
