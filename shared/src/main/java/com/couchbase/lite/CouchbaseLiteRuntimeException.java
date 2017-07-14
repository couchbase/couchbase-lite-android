package com.couchbase.lite;

import java.util.Map;

public final class CouchbaseLiteRuntimeException extends RuntimeException {

    private static final String[] DOMAINS = {
            null,
            "LiteCore",
            "POSIXErrorDomain",
            "ForestDB",
            "SQLite",
            "Fleece",
            "Network",
            "WebSocket"};

    private final int domain;
    private final int code;
    private final Map<String, Object> info;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public CouchbaseLiteRuntimeException(String message) {
        super(message);
        this.domain = 0;
        this.code = 0;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified cause
     *
     * @param cause the cause
     */
    public CouchbaseLiteRuntimeException(Throwable cause) {
        super(cause);
        this.domain = 0;
        this.code = 0;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified error domain and error code
     *
     * @param domain the error domain
     * @param code   the error code
     */
    public CouchbaseLiteRuntimeException(int domain, int code) {
        super();
        this.domain = domain;
        this.code = code;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified error domain, error code and the specified cause
     *
     * @param domain the error domain
     * @param code   the error code
     * @param cause  the cause
     */
    public CouchbaseLiteRuntimeException(int domain, int code, Throwable cause) {
        super(cause);
        this.domain = domain;
        this.code = code;
        this.info = null;
    }

    public CouchbaseLiteRuntimeException(int domain, int code, Map<String, Object> info) {
        super();
        this.domain = domain;
        this.code = code;
        this.info = info;
    }

    /**
     * Access the error domain for this error.
     *
     * @return The numerical domain code for this error.
     */
    public int getDomain() {
        return domain;
    }

    /**
     * Access the error domain for this error.
     *
     * @return The string domain code for this error.
     */
    public String getDomainString() {
        if (domain < 0 || domain >= DOMAINS.length)
            return null;
        return DOMAINS[domain];
    }

    /**
     * Access the error code for this error.
     *
     * @return The numerical error code for this error.
     */
    public int getCode() {
        return code;
    }

    public Map<String, Object> getInfo() {
        return info;
    }

    @Override
    public String toString() {
        if (domain > 0 && code > 0)
            return "CouchbaseLiteRuntimeException{" +
                    "domain=" + domain +
                    ", code=" + code +
                    '}';
        else
            return super.toString();
    }
}
