package com.sdprep.ecommerce;

import com.sdprep.ecommerce.core.CartService;
import com.sdprep.ecommerce.core.Catalog;
import com.sdprep.ecommerce.core.CheckoutService;
import com.sdprep.ecommerce.core.Inventory;
import com.sdprep.ecommerce.core.PaymentGateway;
import com.sdprep.ecommerce.core.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java core and seeds a small catalog so the API is usable right away. */
@Configuration
public class AppConfig {

    /** Fake payments: charges above ₹10,000 are declined (an imaginary card limit). */
    static final long CARD_LIMIT_CENTS = 1_000_000;

    @Bean
    Catalog catalog() {
        Catalog catalog = new Catalog();
        catalog.add(new Product("p1", "Mechanical Keyboard", 499_900));
        catalog.add(new Product("p2", "Wireless Mouse", 149_900));
        catalog.add(new Product("p3", "4K Monitor", 2_499_900));
        return catalog;
    }

    @Bean
    Inventory inventory() {
        Inventory inventory = new Inventory();
        inventory.setStock("p1", 5);
        inventory.setStock("p2", 10);
        inventory.setStock("p3", 1);
        return inventory;
    }

    @Bean
    CartService cartService(Catalog catalog) {
        return new CartService(catalog);
    }

    @Bean
    PaymentGateway paymentGateway() {
        return (userId, amountCents, idempotencyKey) -> amountCents <= CARD_LIMIT_CENTS;
    }

    @Bean
    CheckoutService checkoutService(Catalog catalog, Inventory inventory, CartService carts, PaymentGateway payments) {
        return new CheckoutService(catalog, inventory, carts, payments);
    }
}
