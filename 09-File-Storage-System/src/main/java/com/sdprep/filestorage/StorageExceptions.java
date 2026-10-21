package com.sdprep.filestorage;

/** Errors the storage service reports to callers. */
public final class StorageExceptions {

    private StorageExceptions() {
    }

    /** Bucket or key doesn't exist → HTTP 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** Not enough healthy replicas to read or write safely → HTTP 503. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message) {
            super(message);
        }
    }
}
