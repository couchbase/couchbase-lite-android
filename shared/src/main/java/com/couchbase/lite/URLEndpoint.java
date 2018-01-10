package com.couchbase.lite;

import java.net.URI;
import java.net.URISyntaxException;

import static com.couchbase.litecore.C4Replicator.kC4Replicator2Scheme;
import static com.couchbase.litecore.C4Replicator.kC4Replicator2TLSScheme;

public class URLEndpoint implements Endpoint {
    private String host = null;
    private int port = -1;
    private String path = null;
    private boolean secure = false;
    private URI uri = null;

    public URLEndpoint(String host, boolean secure) throws URISyntaxException {
        this(host, null, secure);
    }

    public URLEndpoint(String host, String path, boolean secure) throws URISyntaxException {
        this(host, -1, path, secure);
    }

    public URLEndpoint(String host, int port, String path, boolean secure) throws URISyntaxException {
        this.host = host;
        this.port = port;
        this.path = path;
        this.secure = secure;

        String scheme = secure ? kC4Replicator2TLSScheme : kC4Replicator2Scheme;
        this.uri = new URI(scheme, null, host, port, path, null, null);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public boolean isSecure() {
        return secure;
    }

    URI getURI() {
        return uri;
    }
}
