package com.couchbase.lite;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public final class Blob {
    private String contentType;
    private byte[] content;
    private InputStream contentStream;

    public Blob(String contentType, byte[] content) {
        this.contentType = contentType;
        this.content = content;
    }

    public Blob(String contentType, InputStream contentStream) {
        this.contentType = contentType;
        this.contentStream = contentStream;
    }

    public Blob(String contentType, URL url) throws IOException {
        this.contentType = contentType;
        this.contentStream = url.openStream();
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public InputStream getContentStream() {
        return contentStream;
    }

    public long length() {
        return 0L;
    }

    public String digest() {
        return null;
    }

    public Map<String, Object> getProperties() {
        return null;
    }
}
