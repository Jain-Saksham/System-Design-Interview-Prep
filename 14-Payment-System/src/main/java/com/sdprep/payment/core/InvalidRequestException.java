package com.sdprep.payment.core;

/** Bad input → HTTP 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
