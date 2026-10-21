package com.sdprep.ecommerce.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP with the seeded catalog (p1 keyboard ×5, p3 monitor ×1 priced above the card limit). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh stock per test
class StoreControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void addToCartThenCheckoutThenReadOrder() throws Exception {
        addToCart("alice", "p1", 2);

        mvc.perform(post("/api/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalCents").value(999_800))
                .andExpect(jsonPath("$.id").value("ord-1"));

        mvc.perform(get("/api/orders/ord-1")).andExpect(status().isOk());
        mvc.perform(get("/api/inventory")).andExpect(jsonPath("$.p1.available").value(3));
    }

    @Test
    void declinedPaymentIs402AndStockIsReleased() throws Exception {
        addToCart("bob", "p3", 1);

        mvc.perform(post("/api/orders").header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"bob\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        mvc.perform(get("/api/inventory")).andExpect(jsonPath("$.p3.available").value(1));
    }

    @Test
    void outOfStockIs409AndMissingIdempotencyKeyIs400() throws Exception {
        addToCart("carol", "p1", 6);

        mvc.perform(post("/api/orders").header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"carol\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"carol\"}"))
                .andExpect(status().isBadRequest());
    }

    private void addToCart(String user, String productId, int quantity) throws Exception {
        mvc.perform(post("/api/carts/" + user + "/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().isOk());
    }
}
