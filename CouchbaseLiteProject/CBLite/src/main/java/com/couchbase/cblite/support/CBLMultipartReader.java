package com.couchbase.cblite.support;

public class CBLMultipartReader {

    private String contentType;
    private CBLMultipartReaderDelegate delegate;

    public CBLMultipartReader(String contentType, CBLMultipartReaderDelegate delegate) {

        this.contentType = contentType;
        this.delegate = delegate;

        parseContentType();

    }

    public byte[] getBoundary() {
        // TODO
        return new byte[1];
    }

    public boolean finished() {
        // TODO
        return true;
    }

    public void appendData(byte[] data) {
        // TODO
    }

    private void parseContentType() {
        // TODO: parse content type


    }




}
