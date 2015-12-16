/**
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.mockserver;

import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    // Map where key is RecordedReqeust instance, and value is the MockResponse that
    // was returned for that RecordedRequest.
    private Map<RecordedRequest, MockResponse> recordedReponseMap;

    // add these headers to every request
    private Map<String, String> headers;

    public enum ServerType { SYNC_GW, COUCHDB }

    public MockDispatcher() {
        super();
        queueMap = new ConcurrentHashMap<String, BlockingQueue<SmartMockResponse>>();
        recordedRequestQueueMap = new ConcurrentHashMap<String, BlockingQueue<RecordedRequest>>();
        recordedReponseMap = new ConcurrentHashMap<RecordedRequest, MockResponse>();
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

    Object lockResponseQueue = new Object();

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
                    SmartMockResponse smartMockResponse = null;
                    synchronized (lockResponseQueue) {
                        smartMockResponse = responseQueue.take(); // as checked isEmpty() before, chance of blocking is low....
                        if (smartMockResponse.isSticky()) {
                            responseQueue.put(smartMockResponse); // if it's sticky, put it back in queue
                        }
                    }
                    if (smartMockResponse.delayMs() > 0) {
                        System.out.println(String.format("Delaying response %s for %d ms (path: %s)", smartMockResponse, smartMockResponse.delayMs(), pathRegex));
                        Thread.sleep(smartMockResponse.delayMs());
                        System.out.println(String.format("Finished delaying response %s for %d (path: %s)", smartMockResponse, smartMockResponse.delayMs(), pathRegex));
                    }
                    MockResponse mockResponse = smartMockResponse.generateMockResponse(request);
                    System.out.println(String.format("Response: %s", mockResponse.getBody()));
                    addHeaders(mockResponse);
                    recordedReponseMap.put(request, mockResponse);
                    return mockResponse;
                } else {
                    MockResponse mockResponse = new MockResponse();
                    mockResponse.setStatus("HTTP/1.1 406 NOT ACCEPTABLE");
                    recordedReponseMap.put(request, mockResponse);
                    return mockResponse; // fail fast
                }
            }
        }
        MockResponse mockResponse = new MockResponse();
        mockResponse.setStatus("HTTP/1.1 406 NOT ACCEPTABLE");
        recordedReponseMap.put(request, mockResponse);
        return mockResponse; // fail fast
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

    public void clearQueuedResponse(String pathRegex) {
        // get the response queue for this path regex
        BlockingQueue<SmartMockResponse> responseQueue = queueMap.get(pathRegex);
        if (responseQueue != null) {
            responseQueue.clear();
        }
    }

    public void clearRecordedRequests(String pathRegex) {
        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);
        if (queue != null) {
            queue.clear();
        }
    }

    public RecordedRequest takeRequest(String pathRegex) throws TimeoutException{
        return takeRequest(pathRegex, 10000);
    }

    public RecordedRequest takeRequest(String pathRegex, long timeout) throws TimeoutException{
        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);
        if (queue == null) {
            return null;
        }
        if (queue.isEmpty()) {
            return null;
        }
        try {
            RecordedRequest request = queue.poll(timeout, TimeUnit.MILLISECONDS);
            if(request == null)
                throw new TimeoutException();
            return request;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public BlockingQueue<RecordedRequest> getRequestQueueSnapshot(String pathRegex) {
        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);
        if (queue == null) {
            return null;
        }
        BlockingQueue<RecordedRequest> result = new LinkedBlockingQueue<RecordedRequest>(queue);
        return result;
    }

    public MockResponse takeRecordedResponseBlocking(RecordedRequest request) throws TimeoutException {
        return takeRecordedResponseBlocking(request, 10000);
    }

    public MockResponse takeRecordedResponseBlocking(RecordedRequest request, long timeout) throws TimeoutException {
        long start = System.currentTimeMillis();

        while (true) {
            if (!recordedReponseMap.containsKey(request)) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } else {
                MockResponse response = recordedReponseMap.get(request);
                if (response != null) {
                    return response;
                }
            }
            if (System.currentTimeMillis() - start > timeout)
                throw new TimeoutException();
        }
    }

    public RecordedRequest takeRequestBlocking(String pathRegex) throws TimeoutException {
        return takeRequestBlocking(pathRegex, 10000);
    }
    public RecordedRequest takeRequestBlocking(String pathRegex, long timeout) throws TimeoutException {
        long start = System.currentTimeMillis();

        BlockingQueue<RecordedRequest> queue = recordedRequestQueueMap.get(pathRegex);

        // since the queue itself will be created lazily, we need to do a silly
        // polling loop until the queue appears (if ever -- otherwise this will never return)
        while (queue == null) {
            if (System.currentTimeMillis() - start > timeout)
                throw new TimeoutException();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            queue = recordedRequestQueueMap.get(pathRegex);
        }

        try {
            RecordedRequest request = queue.poll(timeout, TimeUnit.MILLISECONDS);
            if(request == null)
                throw new TimeoutException();
            return request;
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
