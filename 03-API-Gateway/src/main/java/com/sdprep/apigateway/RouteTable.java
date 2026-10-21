package com.sdprep.apigateway;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the backend for a path. The longest matching prefix wins, so a specific route
 * like "/orders/admin" can override a general one like "/orders".
 */
public class RouteTable {

    private final List<Route> routesLongestPrefixFirst;

    public RouteTable(List<Route> routes) {
        this.routesLongestPrefixFirst = routes.stream()
                .sorted(Comparator.comparingInt((Route route) -> route.pathPrefix().length()).reversed())
                .toList();
    }

    public Optional<Route> find(String path) {
        return routesLongestPrefixFirst.stream()
                .filter(route -> route.matches(path))
                .findFirst();
    }
}
