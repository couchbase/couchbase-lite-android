//
// CouchbaseLiteException.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.Map;


/**
 * A CouchbaseLiteException gets raised whenever a Couchbase Lite faces errors.
 */
public final class CouchbaseLiteException extends Exception implements CBLError.Domain {
    private final String domain;
    private final int code;
    private final Map<String, Object> info;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public CouchbaseLiteException(String message) {
        super(message);
        this.domain = CBLErrorDomain;
        this.code = 0;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified cause
     *
     * @param cause the cause
     */
    public CouchbaseLiteException(Throwable cause) {
        super(cause);
        this.domain = CBLErrorDomain;
        this.code = 0;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified error domain and error code
     *
     * @param domain the error domain
     * @param code   the error code
     */
    public CouchbaseLiteException(@NonNull String domain, int code) {
        super();
        if (domain == null) { throw new IllegalArgumentException("domain cannot be null."); }
        this.domain = domain;
        this.code = code;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified detail message, error domain and error code
     *
     * @param message the detail message
     * @param domain  the error domain
     * @param code    the error code
     */
    public CouchbaseLiteException(String message, @NonNull String domain, int code) {
        super(message);
        if (domain == null) { throw new IllegalArgumentException("domain cannot be null."); }
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
    public CouchbaseLiteException(@NonNull String domain, int code, Throwable cause) {
        super(cause);
        if (domain == null) { throw new IllegalArgumentException("domain cannot be null."); }
        this.domain = domain;
        this.code = code;
        this.info = null;
    }

    /**
     * Constructs a new exception with the specified error domain, error code and the specified cause
     *
     * @param message the detail message
     * @param cause   the cause
     * @param domain  the error domain
     * @param code    the error code
     */
    public CouchbaseLiteException(@NonNull String message, Throwable cause, String domain, int code) {
        super(message, cause);
        if (domain == null) { throw new IllegalArgumentException("domain cannot be null."); }
        this.domain = domain;
        this.code = code;
        this.info = null;
    }

    public CouchbaseLiteException(@NonNull String domain, int code, Map<String, Object> info) {
        super();
        if (domain == null) { throw new IllegalArgumentException("domain cannot be null."); }
        this.domain = domain;
        this.code = code;
        this.info = info;
    }

    /**
     * Access the error domain for this error.
     *
     * @return The numerical domain code for this error.
     */
    @NonNull
    public String getDomain() {
        return domain;
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

    @NonNull
    @Override
    public String toString() {
        return "CouchbaseLiteException{" +
            "domain='" + domain + '\'' +
            ", code=" + code +
            ", msg=" + super.getMessage() +
            '}';
    }
}
