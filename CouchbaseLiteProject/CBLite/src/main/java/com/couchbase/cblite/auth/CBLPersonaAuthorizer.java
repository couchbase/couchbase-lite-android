package com.couchbase.cblite.auth;

import android.util.Base64;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CBLPersonaAuthorizer extends CBLAuthorizer {

    public static final String LOGIN_PARAMETER_ASSERTION = "assertion";

    private static Map<List<String>, String> assertions;
    public static final String ASSERTION_FIELD_EMAIL = "email";
    public static final String ASSERTION_FIELD_ORIGIN = "origin";
    public static final String ASSERTION_FIELD_EXPIRATION = "exp";
    public static final String QUERY_PARAMETER = "personaAssertion";


    // set to true to skip checking whether assertions have expired (useful for testing)
    private boolean skipAssertionExpirationCheck;

    private String emailAddress;

    public CBLPersonaAuthorizer(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public void setSkipAssertionExpirationCheck(boolean skipAssertionExpirationCheck) {
        this.skipAssertionExpirationCheck = skipAssertionExpirationCheck;
    }

    public boolean isSkipAssertionExpirationCheck() {
        return skipAssertionExpirationCheck;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    protected boolean isAssertionExpired(Map<String, Object> parsedAssertion) {

        if (this.isSkipAssertionExpirationCheck() == true) {
            return false;
        }

        Date exp;
        exp = (Date) parsedAssertion.get(ASSERTION_FIELD_EXPIRATION);
        Date now = new Date();
        if (exp.before(now)) {
            Log.w(CBLDatabase.TAG, String.format("%s assertion for %s expired: %s",
                    this.getClass(), this.emailAddress, exp));
            return true;
        }
        return false;

    }

    public String assertionForSite(URL site) {
        String assertion = assertionForEmailAndSite(this.emailAddress, site);
        if (assertion == null) {
            Log.w(CBLDatabase.TAG, String.format("%s %s no assertion found for: %s",
                    this.getClass(), this.emailAddress, site));
            return null;
        }
        Map<String, Object> result = parseAssertion(assertion);
        if (isAssertionExpired(result)) {
            return null;
        }
        return assertion;

    }

    public boolean usesCookieBasedLogin() {
        return true;
    }

    public Map<String, String> loginParametersForSite(URL site) {
        Map<String, String> loginParameters = new HashMap<String, String>();

        String assertion = assertionForSite(site);
        if (assertion != null) {
            loginParameters.put(LOGIN_PARAMETER_ASSERTION, assertion);
            return loginParameters;
        } else {
            return null;
        }

    }

    public String loginPathForSite(URL site) {
        return "/_persona";
    }

    public synchronized static String registerAssertion(String assertion) {

        String email, origin;
        Map<String, Object> result = parseAssertion(assertion);
        email = (String) result.get(ASSERTION_FIELD_EMAIL);
        origin = (String) result.get(ASSERTION_FIELD_ORIGIN);

        // Normalize the origin URL string:
        try {
            URL originURL = new URL(origin);
            if (origin == null) {
                throw new IllegalArgumentException("Invalid assertion, origin was null");
            }
            origin = originURL.toExternalForm().toLowerCase();
        } catch (MalformedURLException e) {
            String message = "Error registering assertion: " + assertion;
            Log.e(CBLDatabase.TAG, message, e);
            throw new IllegalArgumentException(message, e);
        }

        return registerAssertion(assertion, email, origin);

    }

    /**
     * don't use this!! this was factored out for testing purposes, and had to be
     * made public since tests are in their own package.
     */
    public synchronized static String registerAssertion(String assertion, String email, String origin) {

        List<String> key = new ArrayList<String>();
        key.add(email);
        key.add(origin);

        if (assertions == null) {
            assertions = new HashMap<List<String>, String>();
        }
        Log.d(CBLDatabase.TAG, "CBLPersonaAuthorizer registering key: " + key);
        assertions.put(key, assertion);

        return email;

    }

    public static Map<String, Object> parseAssertion(String assertion) {

        // https://github.com/mozilla/id-specs/blob/prod/browserid/index.md
        // http://self-issued.info/docs/draft-jones-json-web-token-04.html

        Map<String, Object> result = new HashMap<String, Object>();

        String[] components = assertion.split("\\.");  // split on "."
        if (components.length < 4) {
            throw new IllegalArgumentException("Invalid assertion given, only " + components.length + " found.  Expected 4+");
        }

        String component1Decoded = new String(Base64.decode(components[1], Base64.DEFAULT));
        String component3Decoded = new String(Base64.decode(components[3], Base64.DEFAULT));

        try {

            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> component1Json = mapper.readValue(component1Decoded, Map.class);
            Map<?, ?> principal = (Map<?, ?>) component1Json.get("principal");
            result.put(ASSERTION_FIELD_EMAIL, principal.get("email"));

            Map<?, ?> component3Json = mapper.readValue(component3Decoded, Map.class);
            result.put(ASSERTION_FIELD_ORIGIN, component3Json.get("aud"));

            Long expObject = (Long) component3Json.get("exp");
            Log.d(CBLDatabase.TAG, "CBLPersonaAuthorizer exp: " + expObject + " class: " + expObject.getClass());
            Date expDate = new Date(expObject.longValue());
            result.put(ASSERTION_FIELD_EXPIRATION, expDate);


        } catch (IOException e) {
            String message = "Error parsing assertion: " + assertion;
            Log.e(CBLDatabase.TAG, message, e);
            throw new IllegalArgumentException(message, e);
        }

        return result;
    }

    public static String assertionForEmailAndSite(String email, URL site) {
        List<String> key = new ArrayList<String>();
        key.add(email);
        key.add(site.toExternalForm().toLowerCase());
        Log.d(CBLDatabase.TAG, "CBLPersonaAuthorizer looking up key: " + key + " from list of assertions");
        return assertions.get(key);
    }

}
