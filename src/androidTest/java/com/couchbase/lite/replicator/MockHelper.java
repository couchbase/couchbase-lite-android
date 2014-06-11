package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

public class MockHelper {

    public static MockWebServer getMockWebServer() {

        MockWebServer server = new MockWebServer();

        MockResponse fakeCheckpointResponse = new MockResponse();
        set404NotFoundJson(fakeCheckpointResponse);
        server.enqueue(fakeCheckpointResponse);
        return server;

    }

    public static void set200OKJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 200 OK").setHeader("Content-Type", "application/json");
    }

    public static void set404NotFoundJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 404 NOT FOUND").setHeader("Content-Type", "application/json");
    }


}
