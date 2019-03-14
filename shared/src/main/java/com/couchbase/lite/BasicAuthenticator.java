//
// BasicAuthenticator.java
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

import java.util.HashMap;
import java.util.Map;


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

    public BasicAuthenticator(@NonNull String username, @NonNull String password) {
        if (username == null) { throw new IllegalArgumentException("username cannot be null."); }
        if (password == null) { throw new IllegalArgumentException("password cannot be null."); }

        this.username = username;
        this.password = password;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getPassword() {
        return password;
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------

    @Override
    void authenticate(Map<String, Object> options) {
        final Map<String, Object> auth = new HashMap<>();
        auth.put(ReplicatorConfiguration.kC4ReplicatorAuthType, ReplicatorConfiguration.kC4AuthTypeBasic);
        auth.put(ReplicatorConfiguration.kCBLReplicatorAuthUserName, username);
        auth.put(ReplicatorConfiguration.kCBLReplicatorAuthPassword, password);
        options.put(ReplicatorConfiguration.kCBLReplicatorAuthOption, auth);
    }
}
