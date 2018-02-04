//
// SessionAuthenticator.java
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

import java.util.Locale;
import java.util.Map;

import static com.couchbase.lite.ReplicatorConfiguration.kC4ReplicatorOptionCookies;

/**
 * SessionAuthenticator class is an authenticator that will authenticate by using the sessin ID of
 * the session created by a Sync Gateway
 */
public final class SessionAuthenticator extends Authenticator {

    private final static String DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME = "SyncGatewaySession";

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private String sessionID;
    private String cookieName;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    /**
     * Initializes with the Sync Gateway session ID and uses the default cookie name.
     *
     * @param sessionID Sync Gateway session ID
     */
    public SessionAuthenticator(String sessionID) {
        this(sessionID, DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME);
    }

    /**
     * Initializes with the session ID and the cookie name. If the given cookieName
     * is null, the default cookie name will be used.
     *
     * @param sessionID  Sync Gateway session ID
     * @param cookieName The cookie name
     */
    public SessionAuthenticator(String sessionID, String cookieName) {
        this.sessionID = sessionID;
        this.cookieName = cookieName != null ? cookieName : DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return session ID of the session created by a Sync Gateway.
     */
    public String getSessionID() {
        return sessionID;
    }

    /**
     * Return session cookie name that the session ID value will be set to when communicating
     * the Sync Gateaway.
     */
    public String getCookieName() {
        return cookieName;
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------
    @Override
    void authenticate(Map<String, Object> options) {
        String current = (String) options.get(kC4ReplicatorOptionCookies);
        StringBuffer cookieStr = current != null ? new StringBuffer(current) : new StringBuffer();

        if (cookieStr.length() > 0)
            cookieStr.append("; ");
        cookieStr.append(String.format(Locale.ENGLISH, "%s=%s", cookieName, sessionID));

        options.put(kC4ReplicatorOptionCookies, cookieStr.toString());
    }
}
