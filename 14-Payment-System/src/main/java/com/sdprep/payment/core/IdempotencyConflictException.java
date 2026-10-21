package com.sdprep.payment.core;

/** The key was already used with a different request body → HTTP 422. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("Idempotency-Key " + key + " was already used with a different request");
    }
}
