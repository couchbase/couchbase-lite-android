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
    public SessionAuthenticator(String sessionID) {
        this(sessionID, DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME);
    }

    public SessionAuthenticator(String sessionID, String cookieName) {
        this.sessionID = sessionID;
        this.cookieName = cookieName != null ? cookieName : DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    public String getSessionID() {
        return sessionID;
    }

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
