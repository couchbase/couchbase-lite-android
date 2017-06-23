package com.couchbase.lite;

import java.util.Map;

/**
 * Authenticator is an opaque based authenticator interface and not intended for application to
 * implement a custom authenticator by subclassing Authenticator interface.
 */
public abstract class Authenticator {

    // Replicator option dictionary keys:
    static final String kC4ReplicatorOptionCookies = "cookies";  // HTTP Cookie header value; string
    static final String kCBLReplicatorAuthOption = "auth";       // Auth settings; Dict

    // Auth dictionary keys:
    static final String kCBLReplicatorAuthUserName = "username"; // Auth property; string
    static final String kCBLReplicatorAuthPassword = "password"; // Auth property; string

    abstract void authenticate(Map<String, Object> options);
}
