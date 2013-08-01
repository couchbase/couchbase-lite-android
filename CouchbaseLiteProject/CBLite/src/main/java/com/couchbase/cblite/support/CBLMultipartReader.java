package com.couchbase.cblite.support;

import org.apache.http.util.ByteArrayBuffer;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class CBLMultipartReader {

    private static enum CBLMultipartReaderState {
        kUninitialized,
        kAtStart,
        kInPrologue,
        kInBody,
        kInHeaders,
        kAtEnd,
        kFailed
    }
    private static Charset utf8 = Charset.forName("UTF-8");
    private static byte[] kCRLFCRLF = new String("\r\n\r\n").getBytes(utf8);

    private CBLMultipartReaderState state;
    private ByteArrayBuffer buffer;
    private String contentType;
    private byte[] boundary;
    private CBLMultipartReaderDelegate delegate;
    public Map<String, String> headers;

    public CBLMultipartReader(String contentType, CBLMultipartReaderDelegate delegate) {

        this.contentType = contentType;
        this.delegate = delegate;
        this.buffer = new ByteArrayBuffer(1024);
        this.state = CBLMultipartReaderState.kAtStart;

        parseContentType();

    }

    public byte[] getBoundary() {
        return boundary;
    }

    public byte[] getBoundaryWithoutLeadingCRLF() {
        byte[] rawBoundary = getBoundary();
        byte[] result = Arrays.copyOfRange(rawBoundary, 2, rawBoundary.length);
        return result;
    }

    public boolean finished() {
        return state == CBLMultipartReaderState.kAtEnd;
    }

    private byte[] eomBytes() {
        return new String("--").getBytes(Charset.forName("UTF-8"));
    }

    private boolean memcmp(byte[] array1, byte[] array2, int len) {
        boolean equals = true;
        for (int i=0; i<len; i++) {
            if (array1[i] != array2[i]) {
                equals = false;
            }
        }
        return equals;
    }

    private Range searchFor(byte[] pattern, int start) {

        byte[] byteArrayToSearch;
        if (start == 0) {
            byteArrayToSearch = buffer.toByteArray();
        }
        else {
            byteArrayToSearch = Arrays.copyOfRange(buffer.toByteArray(), start, buffer.length());
        }

        KMPMatch searcher = new KMPMatch();
        int matchIndex = searcher.indexOf(byteArrayToSearch, pattern);
        if (matchIndex > 0) {
            return new Range(matchIndex, pattern.length);
        }
        else {
            return new Range(matchIndex, 0);
        }
    }

    public void parseHeaders(String headersStr) {

        if (headersStr == null || headersStr.length() == 0) {
            throw new IllegalArgumentException("Unparseable UTF-8 in headers");
        }
        headers = new HashMap<String, String>();
        headersStr = headersStr.trim();
        StringTokenizer tokenizer = new StringTokenizer(headersStr, "\r\n");
        while (tokenizer.hasMoreTokens()) {
            String header = tokenizer.nextToken();

            if (!header.contains(":")) {
                throw new IllegalArgumentException("Missing ':' in header line: " + header);
            }
            StringTokenizer headerTokenizer = new StringTokenizer(header, ":");
            String key = headerTokenizer.nextToken().trim();
            String value = headerTokenizer.nextToken().trim();
            headers.put(key, value);

        }

    }

    private void deleteUpThrough(int location) {

        // int start = location + 1;  // start at the first byte after the location

        byte[] newBuffer = Arrays.copyOfRange(buffer.toByteArray(), location, buffer.length());
        buffer.clear();
        buffer.append(newBuffer, 0, newBuffer.length);

    }

    public void appendData(byte[] data) {

        if (buffer == null) {
            return;
        }
        if (data.length == 0) {
            return;
        }
        buffer.append(data, 0, data.length);

        CBLMultipartReaderState nextState;
        do {
            nextState = CBLMultipartReaderState.kUninitialized;
            int bufLen = buffer.length();
            switch (state) {
                case kAtStart: {
                    // The entire message might start with a boundary without a leading CRLF.
                    byte[] boundaryWithoutLeadingCRLF = getBoundaryWithoutLeadingCRLF();
                    if (bufLen >= boundaryWithoutLeadingCRLF.length) {
                        if (Arrays.equals(buffer.toByteArray(), boundaryWithoutLeadingCRLF)) {
                            deleteUpThrough(boundaryWithoutLeadingCRLF.length);
                            nextState = CBLMultipartReaderState.kInHeaders;
                        }
                        else {
                            nextState = CBLMultipartReaderState.kInPrologue;
                        }
                    }
                    break;
                }
                case kInPrologue:
                case kInBody: {
                    // Look for the next part boundary in the data we just added and the ending bytes of
                    // the previous data (in case the boundary string is split across calls)
                    if (bufLen < boundary.length) {
                        break;
                    }
                    int start = Math.max(0, bufLen - data.length - boundary.length);
                    Range r = searchFor(boundary, start);
                    if (r.getLength() > 0) {
                        if (state == CBLMultipartReaderState.kInBody) {
                            byte[] dataToAppend = Arrays.copyOfRange(buffer.toByteArray(), 0, r.getLocation());
                            delegate.appendToPart(dataToAppend);
                            delegate.finishedPart();
                        }
                        deleteUpThrough(r.getLocation() + r.getLength());
                        nextState = CBLMultipartReaderState.kInHeaders;
                    }
                    break;
                }
                case kInHeaders: {
                    // First check for the end-of-message string ("--" after separator):
                    if (bufLen >= 2 &&
                            memcmp(buffer.toByteArray(), eomBytes(), 2 )) {
                        state = CBLMultipartReaderState.kAtEnd;
                        close();
                        return;
                    }
                    // Otherwise look for two CRLFs that delimit the end of the headers:
                    Range r = searchFor(kCRLFCRLF, 0);
                    if (r.getLength() > 0) {
                        byte[] headersBytes = Arrays.copyOf(buffer.toByteArray(), r.getLocation());
                        // byte[] headersBytes = Arrays.copyOfRange(buffer.toByteArray(), 0, r.getLocation())  <-- better?

                        String headersString = new String(headersBytes, utf8);
                        parseHeaders(headersString);
                        deleteUpThrough(r.getLocation() + r.getLength());
                        delegate.startedPart(headers);
                        nextState = CBLMultipartReaderState.kInBody;

                    }
                    break;

                }
                default: {
                    throw new IllegalStateException("Unexpected data after end of MIME body");
                }
            }

            if (nextState != CBLMultipartReaderState.kUninitialized) {
                state = nextState;
            }

        } while (nextState != CBLMultipartReaderState.kUninitialized && buffer.length() > 0);


    }

    private void close() {
        buffer = null;
        boundary = null;
        state = CBLMultipartReaderState.kUninitialized;
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

class Range {
    private int location;
    private int length;

    Range(int location, int length) {
        this.location = location;
        this.length = length;
    }

    int getLocation() {
        return location;
    }

    int getLength() {
        return length;
    }
}

/**
 * Knuth-Morris-Pratt Algorithm for Pattern Matching
 */
class KMPMatch {
    /**
     * Finds the first occurrence of the pattern in the text.
     */
    public int indexOf(byte[] data, byte[] pattern) {
        int[] failure = computeFailure(pattern);

        int j = 0;
        if (data.length == 0)
            return -1;

        for (int i = 0; i < data.length; i++) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == data[i]) { j++; }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }

    /**
     * Computes the failure function using a boot-strapping process,
     * where the pattern is matched against itself.
     */
    private int[] computeFailure(byte[] pattern) {
        int[] failure = new int[pattern.length];

        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j > 0 && pattern[j] != pattern[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == pattern[i]) {
                j++;
            }
            failure[i] = j;
        }

        return failure;
    }
}