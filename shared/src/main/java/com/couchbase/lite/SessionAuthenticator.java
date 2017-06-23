package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * SessionAuthenticator class is an authenticator that will authenticate by using the sessin ID of
 * the session created by a Sync Gateway
 */
public class SessionAuthenticator extends Authenticator {

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private String sessionID;
    private Date expires;
    private String cookieName;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    public SessionAuthenticator(String sessionID, Object expires, String cookieName) {
        this.sessionID = sessionID;
        this.expires = convert(expires);
        this.cookieName = cookieName;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    public String getSessionID() {
        return sessionID;
    }

    public Date getExpires() {
        return expires;
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
        // TODO: How about expires?
        cookieStr.append(String.format(Locale.ENGLISH, "%s=%s", cookieName, sessionID));

        options.put(kC4ReplicatorOptionCookies, cookieStr.toString());
    }

    private Date convert(Object expires) {
        if (expires == null) return null;

        if (expires instanceof Date)
            return (Date) expires;

        if (expires instanceof String)
            return DateUtils.fromJson((String) expires);

        return null;
    }
}
