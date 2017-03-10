package com.couchbase.lite;

public final class CouchbaseLiteException extends RuntimeException {

    private static final String[] DOMAINS = {null, "LiteCore", "POSIXErrorDomain", "ForestDB", "SQLite", "Fleece"};

    private final int domain;
    private final int code;

    public CouchbaseLiteException(String message) {
        super(message);
        this.domain = 0;
        this.code = 0;
    }

    public CouchbaseLiteException(int domain, int code, String message) {
        super(message);
        this.domain = domain;
        this.code = code;
    }

    public CouchbaseLiteException(String message, Throwable cause) {
        super(message, cause);
        this.domain = 0;
        this.code = 0;
    }

    public CouchbaseLiteException(int domain, int code, Throwable cause) {
        super(cause);
        this.domain = domain;
        this.code = code;
    }

    public CouchbaseLiteException(int domain, int code, String message, Throwable cause) {
        super(message, cause);
        this.domain = domain;
        this.code = code;
    }

    public int getDomain() {
        return domain;
    }

    public String getDomainString() {
        if (domain < 0 || domain >= DOMAINS.length)
            return null;
        return DOMAINS[domain];
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        if (domain > 0 && code > 0)
            return "CouchbaseLiteException{" +
                    "domain=" + getDomainString() +
                    ", code=" + code +
                    '}';
        else
            return super.toString();
    }
}
