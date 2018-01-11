package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

import static com.couchbase.lite.ReplicatorConfiguration.kC4AuthTypeBasic;
import static com.couchbase.lite.ReplicatorConfiguration.kC4ReplicatorAuthType;
import static com.couchbase.lite.ReplicatorConfiguration.kCBLReplicatorAuthOption;
import static com.couchbase.lite.ReplicatorConfiguration.kCBLReplicatorAuthPassword;
import static com.couchbase.lite.ReplicatorConfiguration.kCBLReplicatorAuthUserName;

/**
 * The BasicAuthenticator class is an authenticator that will authenticate using HTTP Basic
 * auth with the given username and password. This should only be used over an SSL/TLS connection,
 * as otherwise it's very easy for anyone sniffing network traffic to read the password.
 */
public final class BasicAuthenticator extends Authenticator {

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private String username;
    private String password;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    public BasicAuthenticator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------

    @Override
    void authenticate(Map<String, Object> options) {
        Map<String, Object> auth = new HashMap<>();
        auth.put(kC4ReplicatorAuthType, kC4AuthTypeBasic);
        auth.put(kCBLReplicatorAuthUserName, username);
        auth.put(kCBLReplicatorAuthPassword, password);
        options.put(kCBLReplicatorAuthOption, auth);
    }
}
