package com.couchbase.lite.mockserver;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

public class WrappedSmartMockResponse implements SmartMockResponse {

    private MockResponse mockResponse;
    private long delayMs;
    private boolean sticky;


    public WrappedSmartMockResponse(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }

    public WrappedSmartMockResponse(MockResponse mockResponse, boolean sticky) {
        this.mockResponse = mockResponse;
        this.sticky = sticky;
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        return mockResponse;
    }

    @Override
    public boolean isSticky() {
        return this.sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    @Override
    public long delayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
