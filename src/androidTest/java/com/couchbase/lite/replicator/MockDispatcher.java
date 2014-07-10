package com.couchbase.lite.replicator;

import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Custom dispatcher which allows to queue up MockResponse objects
 * based on the request path.  Eg, there will be a separate response
 * queue for requests to /db/_changes.
 */
public class MockDispatcher extends Dispatcher {

    // Map where the key is a path regex, (eg, "/_changes/*), and
    // the value is a Queue of MockResponse objects
    private Map<String, BlockingQueue<SmartMockResponse>> queueMap;

    // Map where the key is a path regex, (eg, "/_changes/*), and
    // the value is a Queue of RecordedRequest objects this dispatcher has dispatched.
    private Map<String, BlockingQueue<RecordedRequest>> recordedRequestQueueMap;

    // add these headers to every request
    private Map<String, String> headers;

    public enum ServerType { SYNC_GW, COUCHDB }

    public MockDispatcher() {
        super();
        queueMap = new HashMap<String, BlockingQueue<SmartMockResponse>>();
        recordedRequestQueueMap = new HashMap<String, BlockingQueue<RecordedRequest>>();
        headers = new HashMap<String, String>();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setServerType(ServerType serverType) {
        switch (serverType) {
            case SYNC_GW:
                headers.put("Server", "Couchbase Sync Gateway/1.0.0");
                break;
            default:
                headers.remove("Server");
        }
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        System.out.println(String.format("Request: %s", request));
        for(String pathRegex: queueMap.keySet()){
            if (regexMatches(pathRegex, request.getPath())) {
                recordRequest(pathRegex, request);
                BlockingQueue<SmartMockResponse> responseQueue = queueMap.get(pathRegex);
                if (responseQueue == null) {
                    String msg = String.format("No queue found for pathRegex: %s", pathRegex);
                    throw new RuntimeException(msg);
                }
                if (!responseQueue.isEmpty()) {
                    SmartMockResponse smartMockResponse = responseQueue.take();
                    if (smartMockResponse.isSticky()) {
                        responseQueue.put(smartMockResponse); // if it's sticky, put it back in queue
                    }
                    if (smartMockResponse.delayMs() > 0) {
                        System.out.println(String.format("Delaying response for: %d", smartMockResponse.delayMs()));
                        Thread.sleep(smartMockResponse.delayMs());
                        System.out.println("Finished delaying response");
                    }
                    MockResponse mockResponse = smartMockResponse.generateMockResponse(request);
                    System.out.println(String.format("Response: %s", mockResponse.getBody()));
                    addHeaders(mockResponse);
                    return mockResponse;
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

        // get the response queue for this path regex
        BlockingQueue<SmartMockResponse> responseQueue = queueMap.get(pathRegex);
        if (responseQueue == null) {
            // create one on demand if it doesn't already exist
            responseQueue = new LinkedBlockingDeque<SmartMockResponse>();
            queueMap.put(pathRegex, responseQueue);
        }
        // add the response to the queue.  since it's not a smart mock response, wrap it
        responseQueue.add(MockHelper.wrap(response));
    }

    public RecordedRequest takeRequest(String pathRegex) {
        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);
        if (queue == null) {
            return null;
        }
        if (queue.isEmpty()) {
            return null;
        }
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RecordedRequest takeRequestBlocking(String pathRegex) {

        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);

        // since the queue itself will be created lazily, we need to do a silly
        // polling loop until the queue appears (if ever -- otherwise this will never return)
        while (queue == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            queue = recordedRequestQueueMap.get(pathRegex);
        }

        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verifyAllRecordedRequestsTaken() {
        for (String pathRegex : recordedRequestQueueMap.keySet()) {
            BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);
            if (queue == null) {
                continue;
            }
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void reset() {
        recordedRequestQueueMap.clear();
        queueMap.clear();
    }

    private void recordRequest(String pathRegex, RecordedRequest request) {
        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);
        if (queue == null) {
            queue = new LinkedBlockingQueue<RecordedRequest>();
            recordedRequestQueueMap.put(pathRegex, queue);
        }
        queue.add(request);
    }

    private void addHeaders(MockResponse mockResponse) {
        if (!headers.isEmpty()) {
            for (String headerKey : headers.keySet()) {
                String headerVal = headers.get(headerKey);
                mockResponse.setHeader(headerKey, headerVal);
            }
        }
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
