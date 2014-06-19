package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.couchbase.lite.Misc;
import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Misc helper methods for MockWebserver-based Mock objects
 */
public class MockHelper {

    public static final String PATH_REGEX_CHECKPOINT = "/db/_local.*";
    public static final String PATH_REGEX_CHANGES = "/db/_changes.*";
    public static final String PATH_REGEX_REVS_DIFF = "/db/_revs_diff.*";
    public static final String PATH_REGEX_BULK_DOCS = "/db/_bulk_docs.*";
    public static final String PATH_REGEX_SESSION = "/db/_session.*";
    public static final String PATH_REGEX_FACEBOOK_AUTH = "/db/_facebook.*";

    public static MockWebServer getMockWebServer() {

        MockWebServer server = new MockWebServer();

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                return null;
            }
        });

        return server;

    }

    public static void set200OKJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 200 OK").setHeader("Content-Type", "application/json");
    }

    public static void set201OKJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 201 OK").setHeader("Content-Type", "application/json");
    }

    public static void set404NotFoundJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 404 NOT FOUND").setHeader("Content-Type", "application/json");
    }

    public static void addFake404CheckpointResponse(MockWebServer mockWebServer) {
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        mockWebServer.enqueue(fakeCheckpointResponse);
    }


    public static Map<String, Object> generateRandomJsonMap() {

        Map<String, Object> randomJsonMap = new HashMap<String, Object>();
        randomJsonMap.put(Misc.TDCreateUUID(), false);
        randomJsonMap.put("uuid", Misc.TDCreateUUID());
        return randomJsonMap;
    }

    public static SmartMockResponse wrap(MockResponse mockResponse) {
        return new WrappedSmartMockResponse(mockResponse);
    }

    public static Map<String, Object> getJsonMapFromRequest(byte[] requestBody) throws IOException {
        return Manager.getObjectMapper().readValue(requestBody, Map.class);
    }




}
