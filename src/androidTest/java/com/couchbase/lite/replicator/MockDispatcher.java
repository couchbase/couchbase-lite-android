package com.couchbase.lite.replicator;

import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class MockDispatcher extends Dispatcher {

    // Map where the key is a path regex, (eg, "/_changes/*), and
    // the value is a Queue of MockResponse objects
    private Map<String, BlockingQueue<SmartMockResponse>> queueMap;

    public MockDispatcher() {
        super();
        queueMap = new HashMap<String, BlockingQueue<SmartMockResponse>>();
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        System.out.println(String.format("Request: %s", request));
        for(String pathRegex: queueMap.keySet()){
            if (regexMatches(pathRegex, request.getPath())) {
                BlockingQueue<SmartMockResponse> responseQueue = queueMap.get(pathRegex);
                if (responseQueue == null) {
                    String msg = String.format("No queue found for pathRegex: %s", pathRegex);
                    throw new RuntimeException(msg);
                }
                if (!responseQueue.isEmpty()) {
                    SmartMockResponse smartMockResponse = responseQueue.take();
                    return smartMockResponse.generateMockResponse(request);
                } else {
                    return new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_ACCEPTABLE); // fail fast
                }
            }
        }
        return new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_ACCEPTABLE); // fail fast
    }

    public void enqueueResponse(String pathRegex, SmartMockResponse response) {
        BlockingQueue<SmartMockResponse> responseQueue = queueMap.get(pathRegex);
        if (responseQueue == null) {
            responseQueue = new LinkedBlockingDeque<SmartMockResponse>();
            queueMap.put(pathRegex, responseQueue);
        }
        responseQueue.add(response);
    }

    public void enqueueResponse(String pathRegex, MockResponse response) {
        BlockingQueue<SmartMockResponse> responseQueue = queueMap.get(pathRegex);
        if (responseQueue == null) {
            responseQueue = new LinkedBlockingDeque<SmartMockResponse>();
            queueMap.put(pathRegex, responseQueue);
        }
        responseQueue.add(MockHelper.wrap(response));
    }

    private boolean regexMatches(String pathRegex, String actualPath) {
        try {
            return actualPath.matches(pathRegex);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


}
