package com.sdprep.payment.core;

/** The action isn't allowed in the payment's current state (e.g. refunding a failed payment) → HTTP 409. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}
