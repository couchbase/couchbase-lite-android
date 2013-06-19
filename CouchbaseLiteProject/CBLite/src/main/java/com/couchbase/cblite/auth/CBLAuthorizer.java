package com.couchbase.cblite.auth;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class CBLAuthorizer {

    public static final String LOGIN_PARAMETER_ASSERTION = "assertion";

    public boolean usesCookieBasedLogin() {
        return false;
    }

    public Map<String, String> loginParametersForSite(URL site) {
        return null;
    }

    public String loginPathForSite(URL site) {
        return null;
    }

}