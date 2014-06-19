package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;

public class MockFacebookAuthPost {

    public MockResponse generateMockResponse() {
        MockResponse mockResponse = new MockResponse();
        String jsonBody = "{\n" +
                "   \"error\":\"Unauthorized\",\n" +
                "   \"reason\":\"Facebook verification server status 400\"\n" +
                "}";
        mockResponse.setBody(jsonBody);
        mockResponse.setStatus("HTTP/1.1 401 Unauthorized").setHeader("Content-Type", "application/json");
        return mockResponse;
    }

}
