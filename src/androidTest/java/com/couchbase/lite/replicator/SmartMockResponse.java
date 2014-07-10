package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

/**
 * A smarter MockResponse to allow for dynamic changes at the time
 * that a request is being processed.  Eg, it updates its response
 * body based on the request.
 */
public interface SmartMockResponse {

    public MockResponse generateMockResponse(RecordedRequest request);

    public boolean isSticky();

    /**
     * @return the delay, in milliseconds, before the MockDispatcher should
     * return this response.
     */
    public long delayMs();

}
