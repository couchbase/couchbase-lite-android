package com.couchbase.lite;

import java.net.URI;
import java.net.URISyntaxException;

import static com.couchbase.litecore.C4Replicator.kC4Replicator2Scheme;
import static com.couchbase.litecore.C4Replicator.kC4Replicator2TLSScheme;

/**
 * URL based replication target endpoint
 */
public final class URLEndpoint implements Endpoint {
    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private final String host;
    private final int port;
    private final String path;
    private final boolean secure;
    private final URI uri;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructor with the host and the secure flag.
     *
     * @param host   Host name
     * @param secure The secure flag indicating whether the replication data will be sent over secure channels
     * @throws URISyntaxException
     */
    public URLEndpoint(String host, boolean secure) throws URISyntaxException {
        this(host, null, secure);
    }

    /**
     * Constructor with the host, the path and the secure flag.
     *
     * @param host   Host name
     * @param path   Path
     * @param secure The secure flag indicating whether the replication data will be sent over secure channels
     * @throws URISyntaxException
     */
    public URLEndpoint(String host, String path, boolean secure) throws URISyntaxException {
        this(host, -1, path, secure);
    }

    /**
     * Constructor with the host, the port, the path and the secure flag.
     *
     * @param host   Host name
     * @param port   Port number
     * @param path   Path
     * @param secure The secure flag indicating whether the replication data will be sent over secure channels
     * @throws URISyntaxException
     */
    public URLEndpoint(String host, int port, String path, boolean secure) throws URISyntaxException {
        this.host = host;
        this.port = port;
        this.path = path;
        this.secure = secure;

        String scheme = secure ? kC4Replicator2TLSScheme : kC4Replicator2Scheme;
        this.uri = new URI(scheme, null, host, port, path, null, null);
    }

    @Override
    public String toString() {
        return "URLEndpoint{" +
                "uri=" + uri +
                '}';
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns the host component of this URLEndpoint.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port number of this URLEndpoint.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the decoded path component of this URLEndpoint.
     */
    public String getPath() {
        return path;
    }

    /**
     * Return the boolean value indicates whether the replication data will be sent over secure channels.
     */
    public boolean isSecure() {
        return secure;
    }

    URI getURI() {
        return uri;
    }
}
