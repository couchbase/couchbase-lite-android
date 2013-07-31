package com.couchbase.cblite.support;

import java.util.Map;

public interface CBLMultipartReaderDelegate {

    public void startedPart(Map<String, String> headers);

    public void appendToPart(byte[] data);

    public void finishedPart();

}
