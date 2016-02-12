package com.couchbase.lite.mockserver;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

/**
 * Created by hideki on 2/11/16.
 */
public class MockDocumentAllDocs implements SmartMockResponse {
    public MockResponse generateMockResponse(RecordedRequest request) {
        try {
            MockResponse mockResponse = new MockResponse();
            mockResponse.setBody("{\"offset\" : 0, \"rows\" : [],\"total_rows\" : 0}".getBytes());
            mockResponse.setStatus("HTTP/1.1 200 OK");
            return mockResponse;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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