package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

/**
 * The BasicAuthenticator class is an authenticator that will authenticate using HTTP Basic
 * auth with the given username and password. This should only be used over an SSL/TLS connection,
 * as otherwise it's very easy for anyone sniffing network traffic to read the password.
 */
public class BasicAuthenticator extends Authenticator {

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
    /* package */ void authenticate(Map<String, Object> options) {
        Map<String, Object> auth = new HashMap<>();
        auth.put(kCBLReplicatorAuthUserName, username);
        auth.put(kCBLReplicatorAuthPassword, password);
        options.put(kCBLReplicatorAuthOption, auth);
    }
}
