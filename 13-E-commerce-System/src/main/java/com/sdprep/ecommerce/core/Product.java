package com.sdprep.ecommerce.core;

/** Prices are integer cents (paise): floating point can't represent money exactly. */
public record Product(String id, String name, long priceCents) {
}
