package com.couchbase.lite;

import java.util.Map;

/**
 * Authenticator is an opaque based authenticator interface and not intended for application to
 * implement a custom authenticator by subclassing Authenticator interface.
 */
public abstract class Authenticator {
    static final String kCBLReplicatorAuthOption = "auth";
    static final String kCBLReplicatorAuthUserName = "username";
    static final String kCBLReplicatorAuthPassword = "password";

    abstract void authenticate(Map<String, Object> options);
}
