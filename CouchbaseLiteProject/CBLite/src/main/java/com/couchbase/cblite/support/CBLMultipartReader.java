package com.couchbase.cblite.support;

import java.nio.charset.Charset;
import java.util.StringTokenizer;

public class CBLMultipartReader {

    private String contentType;
    private byte[] boundary;
    private CBLMultipartReaderDelegate delegate;

    public CBLMultipartReader(String contentType, CBLMultipartReaderDelegate delegate) {

        this.contentType = contentType;
        this.delegate = delegate;

        parseContentType();

    }

    public byte[] getBoundary() {
        return boundary;
    }

    public boolean finished() {
        // TODO
        return true;
    }

    public void appendData(byte[] data) {
        // TODO
    }

    private void parseContentType() {

        StringTokenizer tokenizer = new StringTokenizer(contentType, ";");
        boolean first = true;
        while (tokenizer.hasMoreTokens()) {
            String param = tokenizer.nextToken().trim();
            if (first == true) {
                if (!param.startsWith("multipart/")) {
                    throw new IllegalArgumentException(contentType + " does not start with multipart/");
                }
                first = false;
            }
            else {
                if (param.startsWith("boundary=")) {
                    String tempBoundary = param.substring(9);
                    if (tempBoundary.startsWith("\"")) {
                        if (tempBoundary.length() < 2 || !tempBoundary.endsWith("\"")) {
                            throw new IllegalArgumentException(contentType + " is not valid");
                        }
                        tempBoundary = tempBoundary.substring(1, tempBoundary.length()-1);
                    }
                    if (tempBoundary.length() < 1) {
                        throw new IllegalArgumentException(contentType + " has zero-length boundary");
                    }
                    tempBoundary = String.format("\r\n--%s", tempBoundary);
                    boundary = tempBoundary.getBytes(Charset.forName("UTF-8"));
                    break;
                }
            }
        }


    }




}
