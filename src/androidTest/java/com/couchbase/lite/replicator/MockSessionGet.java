package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;

public class MockSessionGet {

    public MockResponse generateMockResponse() {
        MockResponse mockResponse = new MockResponse();
        String jsonBody = "{\n" +
                "   \"authentication_handlers\":[\n" +
                "      \"default\",\n" +
                "      \"cookie\"\n" +
                "   ],\n" +
                "   \"ok\":true,\n" +
                "   \"userCtx\":{\n" +
                "      \"channels\":{\n" +
                "         \"*\":1\n" +
                "      },\n" +
                "      \"name\":null\n" +
                "   }\n" +
                "}";
        mockResponse.setBody(jsonBody);
        MockHelper.set200OKJson(mockResponse);
        return mockResponse;
    }

}
