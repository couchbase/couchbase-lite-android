package com.couchbase.lite.support;


import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.WrappedSmartMockResponse;
import com.couchbase.lite.util.Utils;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RemoteRequestTest extends LiteTestCaseWithDB {

    /**
     * Make RemoteRequests will retry correctly.
     *
     * Return MAX_RETRIES - 1 503 transient errors, followed by a 404 non-transient error.
     * It should retry for the 503 errors and return after it gets the 404.
     */
    public void testRetryLastRequestSuccess() throws Exception {

        // lower retry to speed up test
        RemoteRequestRetry.RETRY_DELAY_MS = 5;

        PersistentCookieStore cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // respond with 503 error for the first MAX_RETRIES - 1 requests
        int num503Responses = RemoteRequestRetry.MAX_RETRIES - 1;
        for (int i=0; i<num503Responses; i++) {
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, new MockResponse().setResponseCode(503));
        }
        // on last request, respond with 404 error
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        try {
            server.play();

            String urlString = String.format("%s/%s", server.getUrl("/db"), "_local");
            URL url = new URL(urlString);

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("foo", "bar");

            Map<String, Object> requestHeaders = new HashMap<String, Object>();

            final CountDownLatch received404Error = new CountDownLatch(1);

            RemoteRequestCompletionBlock completionBlock = new RemoteRequestCompletionBlock() {
                @Override
                public void onCompletion(HttpResponse httpResponse, Object result, Throwable e) {
                    if (e instanceof HttpResponseException) {
                        HttpResponseException htre = (HttpResponseException) e;
                        if (htre.getStatusCode() == 404) {
                            received404Error.countDown();
                        }
                    }
                }
            };

            ScheduledExecutorService requestExecutorService = Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService = Executors.newSingleThreadScheduledExecutor();

            RemoteRequestRetry request = new RemoteRequestRetry(
                    RemoteRequestRetry.RemoteRequestType.REMOTE_REQUEST,
                    requestExecutorService,
                    workExecutorService,
                    factory,
                    "GET",
                    url,
                    true,
                    requestBody,
                    database,
                    requestHeaders,
                    completionBlock
            );

            // wait for the future to return
            Future future = request.submit();
            future.get(300, TimeUnit.SECONDS);

            // at this point, the completionBlock should have already been called back
            // with a 404 error, which will decrement countdown latch.
            boolean success = received404Error.await(1, TimeUnit.SECONDS);
            assertTrue(success);

            // make sure that we saw MAX_RETRIES requests sent to server
            for (int i = 0; i < RemoteRequestRetry.MAX_RETRIES; i++) {
                RecordedRequest recordedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
                assertNotNull(recordedRequest);
            }

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(requestExecutorService);
            Utils.shutdownAndAwaitTermination(workExecutorService);
        }finally{
            server.shutdown();
        }
    }


    public void testRetryAllRequestsFail() throws Exception {

        // lower retry to speed up test
        RemoteRequestRetry.RETRY_DELAY_MS = 5;

        PersistentCookieStore cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // respond with 503 error for all requests
        int num503Responses = RemoteRequestRetry.MAX_RETRIES + 1;
        for (int i=0; i<num503Responses; i++) {
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, new MockResponse().setResponseCode(503));
        }
        try {
            server.play();

            String urlString = String.format("%s/%s", server.getUrl("/db"), "_local");
            URL url = new URL(urlString);

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("foo", "bar");

            Map<String, Object> requestHeaders = new HashMap<String, Object>();

            final CountDownLatch received503Error = new CountDownLatch(1);

            RemoteRequestCompletionBlock completionBlock = new RemoteRequestCompletionBlock() {
                @Override
                public void onCompletion(HttpResponse httpResponse, Object result, Throwable e) {
                    if (e instanceof HttpResponseException) {
                        HttpResponseException htre = (HttpResponseException) e;
                        if (htre.getStatusCode() == 503) {
                            received503Error.countDown();
                        }
                    }
                }
            };

            ScheduledExecutorService requestExecutorService = Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService = Executors.newSingleThreadScheduledExecutor();

            // ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(4);
            RemoteRequestRetry request = new RemoteRequestRetry(
                    RemoteRequestRetry.RemoteRequestType.REMOTE_REQUEST,
                    requestExecutorService,
                    workExecutorService,
                    factory,
                    "GET",
                    url,
                    true,
                    requestBody,
                    database,
                    requestHeaders,
                    completionBlock
            );

            // wait for the future to return
            Future future = request.submit();

            future.get(300, TimeUnit.SECONDS);

            // at this point, the completionBlock should have already been called back
            // with a 404 error, which will decrement countdown latch.
            boolean success = received503Error.await(1, TimeUnit.SECONDS);
            assertTrue(success);

            // make sure that we saw MAX_RETRIES requests sent to server
            for (int i = 0; i < RemoteRequestRetry.MAX_RETRIES; i++) {
                RecordedRequest recordedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
                assertNotNull(recordedRequest);
            }

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(requestExecutorService);
            Utils.shutdownAndAwaitTermination(workExecutorService);
        }finally{
            server.shutdown();
        }
    }


    /**
     * Reproduce a severe issue where the pusher stops working because it's remoteRequestExecutor
     * is full of tasks which are all blocked trying to add more tasks to the queue.
     *
     *
     * Failing on Jenkins, looking into it.
     * 
     * @throws Exception
     */
    public void failingTestRetryQueueDeadlock() throws Exception {
        // lower retry to speed up test
        RemoteRequestRetry.RETRY_DELAY_MS = 5;

        PersistentCookieStore cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // respond with 503 error for all requests
            MockResponse response = new MockResponse().setResponseCode(503);
            WrappedSmartMockResponse wrapped = new WrappedSmartMockResponse(response);
            wrapped.setDelayMs(5);
            wrapped.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, wrapped);

            server.play();

            URL url = new URL(String.format("%s/%s", server.getUrl("/db"), "_local"));

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("foo", "bar");

            Map<String, Object> requestHeaders = new HashMap<String, Object>();

            int numRequests = 10;

            final CountDownLatch received503Error = new CountDownLatch(numRequests);

            RemoteRequestCompletionBlock completionBlock = new RemoteRequestCompletionBlock() {
                @Override
                public void onCompletion(HttpResponse httpResponse, Object result, Throwable e) {
                    if (e instanceof HttpResponseException) {
                        HttpResponseException htre = (HttpResponseException) e;
                        if (htre.getStatusCode() == 503) {
                            received503Error.countDown();
                        }
                    }
                }
            };

            ScheduledExecutorService requestExecutorService = Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService = Executors.newSingleThreadScheduledExecutor();

            List<Future> requestFutures = new ArrayList<Future>();
            for (int i = 0; i < numRequests; i++) {
                RemoteRequestRetry request = new RemoteRequestRetry(
                        RemoteRequestRetry.RemoteRequestType.REMOTE_REQUEST,
                        requestExecutorService,
                        workExecutorService,
                        factory,
                        "GET",
                        url,
                        true,
                        requestBody,
                        database,
                        requestHeaders,
                        completionBlock);
                Future future = request.submit();
                requestFutures.add(future);
            }

            for (Future future : requestFutures)
                future.get();

            boolean success = received503Error.await(120, TimeUnit.SECONDS);
            assertTrue(success);

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(requestExecutorService);
            Utils.shutdownAndAwaitTermination(workExecutorService);
        } finally {
            server.shutdown();
        }
    }
}
