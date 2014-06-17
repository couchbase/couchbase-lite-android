package com.couchbase.lite.replicator;

import com.couchbase.lite.Misc;
import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.util.HashMap;
import java.util.Map;

public class MockHelper {

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

    public static void setSyncGwServerType(MockResponse mockResponse, boolean syncGwServerType) {
        if (syncGwServerType == true) {
            mockResponse.setHeader("Server", "Couchbase Sync Gateway/1.0.0");
        }
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



}
