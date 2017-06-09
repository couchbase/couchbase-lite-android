/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

/**
 * A CouchbaseLiteException gets raised whenever a Couchbase Lite faces errors.
 */
public final class CouchbaseLiteException extends RuntimeException {

    private static final String[] DOMAINS = {null, "LiteCore", "POSIXErrorDomain", "ForestDB", "SQLite", "Fleece", "Network", "WebSocket"};

    private final int domain;
    private final int code;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public CouchbaseLiteException(String message) {
        super(message);
        this.domain = 0;
        this.code = 0;
    }

    /**
     * Constructs a new exception with the specified cause
     *
     * @param cause the cause
     */
    public CouchbaseLiteException(Throwable cause) {
        super(cause);
        this.domain = 0;
        this.code = 0;
    }

    /**
     * Constructs a new exception with the specified error domain and error code
     *
     * @param domain the error domain
     * @param code   the error code
     */
    public CouchbaseLiteException(int domain, int code) {
        super();
        this.domain = domain;
        this.code = code;
    }

    /**
     * Constructs a new exception with the specified error domain, error code and the specified cause
     *
     * @param domain the error domain
     * @param code   the error code
     * @param cause  the cause
     */
    public CouchbaseLiteException(int domain, int code, Throwable cause) {
        super(cause);
        this.domain = domain;
        this.code = code;
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

    @Override
    public String toString() {
        if (domain > 0 && code > 0)
            return "CouchbaseLiteException{" +
                    "domain=" + domain +
                    ", code=" + code +
                    '}';
        else
            return super.toString();
    }
}
