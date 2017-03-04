package com.couchbase.lite;

public final class CouchbaseLiteException extends RuntimeException {
    public CouchbaseLiteException(String message) {
        super(message);
    }

    public CouchbaseLiteException(String message, Throwable cause) {
        super(message, cause);
    }
}
