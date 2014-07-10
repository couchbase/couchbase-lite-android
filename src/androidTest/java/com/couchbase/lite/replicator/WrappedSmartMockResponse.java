package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

public class WrappedSmartMockResponse implements SmartMockResponse {

    private MockResponse mockResponse;

    public WrappedSmartMockResponse(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        return mockResponse;
    }

    @Override
    public boolean isSticky() {
        return false;
    }

    @Override
    public long delayMs() {
        return 0;
    }
}
