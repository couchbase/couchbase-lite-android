package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;

/**
 * Wraps a MockResponse to allow for dynamic changes at the time
 * that a request is being processed.  Eg, it updates its response
 * body based on the request.
 */
public class SmartMockResponse {

    private MockResponse mockResponse;

    public SmartMockResponse(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }



}
