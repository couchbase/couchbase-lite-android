package com.couchbase.lite;

import java.util.Map;

/**
 * Authenticator is an opaque based authenticator interface and not intended for application to
 * implement a custom authenticator by subclassing Authenticator interface.
 * <p>
 * NOTE: Authenticator is intentionary defained as abstract class to make authenicate method access
 * level to package level.
 */
public abstract class Authenticator {
    protected Authenticator() {
    }

    abstract void authenticate(Map<String, Object> options);
}
