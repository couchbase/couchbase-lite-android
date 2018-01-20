package com.couchbase.lite;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URL based replication target endpoint
 */
public final class URLEndpoint implements Endpoint {
    //---------------------------------------------
    // Constant variables
    //---------------------------------------------
    private static final String kURLEndpointScheme = "ws";
    private static final String kURLEndpointTLSScheme = "wss";

    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private final URI url;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructor with the url. The supported URL schemes
     * are ws and wss for transferring data over a secure channel.
     *
     * @param url   The url.
     */
    public URLEndpoint(URI url) {
        if (url == null) {
            throw new IllegalArgumentException("The url parameter cannot be null.");
        }

        String scheme = url.getScheme();
        if (!(kURLEndpointScheme.equals(scheme) || kURLEndpointTLSScheme.equals(scheme))) {
            throw new IllegalArgumentException(
                    "The url parameter has an unsupported URL scheme (" + scheme + ") " +
                    "The supported URL schemes are " + kURLEndpointScheme + " and " + kURLEndpointTLSScheme + ".");
        }

        this.url = url;
    }

    @Override
    public String toString() {
        return "URLEndpoint{" +
                "url=" + url +
                '}';
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns the url.
     */
    public URI getURL() {
        return url;
    }
}
