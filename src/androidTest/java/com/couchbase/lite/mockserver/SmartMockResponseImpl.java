package com.couchbase.lite.mockserver;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

/**
 * Created by hideki on 12/11/15.
 */
public class SmartMockResponseImpl  implements SmartMockResponse{
    private MockResponse mockResponse;
    private boolean isSticky;
    private long delayMs;

    public SmartMockResponseImpl(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        return mockResponse;
    }

    @Override
    public boolean isSticky() {
        return this.isSticky;
    }

    @Override
    public long delayMs() {
        return delayMs;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
