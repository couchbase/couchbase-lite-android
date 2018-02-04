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
