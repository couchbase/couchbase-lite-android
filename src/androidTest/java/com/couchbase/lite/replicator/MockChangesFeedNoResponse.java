package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

public class MockChangesFeedNoResponse implements SmartMockResponse {

    private boolean isSticky;
    private long delayMs;

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        return new MockResponse();
    }

    @Override
    public boolean isSticky() {
        return this.isSticky;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }

    @Override
    public long delayMs() {
        return this.delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
