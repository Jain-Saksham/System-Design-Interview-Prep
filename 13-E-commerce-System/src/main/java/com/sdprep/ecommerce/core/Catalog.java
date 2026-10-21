package com.sdprep.ecommerce.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Product information. Production: a product DB + search index (Elasticsearch) + heavy caching/CDN. */
public class Catalog {

    private final Map<String, Product> productsById = new LinkedHashMap<>();

    public synchronized void add(Product product) {
        productsById.put(product.id(), product);
    }

    public synchronized Product get(String productId) {
        Product product = productsById.get(productId);
        if (product == null) {
            throw new NotFoundException("Unknown product: " + productId);
        }
        return product;
    }

    public synchronized List<Product> all() {
        Collection<Product> products = productsById.values();
        return List.copyOf(products);
    }
}
