//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.replicator;

import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.WrappedSmartMockResponse;
import com.couchbase.lite.support.CouchbaseLiteHttpClientFactory;
import com.couchbase.lite.support.PersistentCookieJar;
import com.couchbase.lite.util.Utils;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class RemoteRequestTest extends LiteTestCaseWithDB {

    /**
     * Make RemoteRequests will retry correctly.
     * <p/>
     * Return MAX_RETRIES - 1 503 transient errors, followed by a 404 non-transient error.
     * It should retry for the 503 errors and return after it gets the 404.
     */
    public void testRetryLastRequestSuccess() throws Exception {
        // lower retry to speed up test
        com.couchbase.lite.replicator.RemoteRequestRetry.RETRY_DELAY_MS = 5;

        PersistentCookieJar cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // respond with 503 error for the first MAX_RETRIES - 1 requests
        int num503Responses = com.couchbase.lite.replicator.RemoteRequestRetry.MAX_RETRIES - 1;
        for (int i = 0; i < num503Responses; i++) {
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT,
                    new MockResponse().setResponseCode(503));
        }
        // on last request, respond with 404 error
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        try {
            server.start();

            String urlString = String.format(Locale.ENGLISH, "%s/%s", server.url("/db").url(), "_local");
            URL url = new URL(urlString);

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("foo", "bar");

            Map<String, Object> requestHeaders = new HashMap<String, Object>();

            final CountDownLatch received404Error = new CountDownLatch(1);

            RemoteRequestCompletion completionBlock = new RemoteRequestCompletion() {
                @Override
                public void onCompletion(Response httpResponse, Object result, Throwable e) {
                    if (e instanceof RemoteRequestResponseException) {
                        RemoteRequestResponseException htre = (RemoteRequestResponseException) e;
                        if (htre.getCode() == 404) {
                            received404Error.countDown();
                        }
                    }
                }
            };

            ScheduledExecutorService requestExecutorService =
                    Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService =
                    Executors.newSingleThreadScheduledExecutor();

            RemoteRequestRetry request = new RemoteRequestRetry(
                    RemoteRequestRetry.RemoteRequestType.REMOTE_REQUEST,
                    requestExecutorService,
                    workExecutorService,
                    factory,
                    "GET",
                    url,
                    true,
                    true,
                    requestBody,
                    null,
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
            for (int i = 0; i < com.couchbase.lite.replicator.RemoteRequestRetry.MAX_RETRIES; i++) {
                RecordedRequest recordedRequest =
                        dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
                assertNotNull(recordedRequest);
            }

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(requestExecutorService);
            Utils.shutdownAndAwaitTermination(workExecutorService);
        } finally {
            assertTrue(MockHelper.shutdown(server, dispatcher));
        }
    }

    public void testRetryAllRequestsFail() throws Exception {
        // lower retry to speed up test
        com.couchbase.lite.replicator.RemoteRequestRetry.RETRY_DELAY_MS = 5;

        PersistentCookieJar cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // respond with 503 error for all requests
        int num503Responses = com.couchbase.lite.replicator.RemoteRequestRetry.MAX_RETRIES + 1;
        for (int i = 0; i < num503Responses; i++) {
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT,
                    new MockResponse().setResponseCode(503));
        }
        try {
            server.start();

            String urlString = String.format(Locale.ENGLISH, "%s/%s", server.url("/db").url(), "_local");
            URL url = new URL(urlString);

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("foo", "bar");

            Map<String, Object> requestHeaders = new HashMap<String, Object>();

            final CountDownLatch received503Error = new CountDownLatch(1);

            RemoteRequestCompletion completionBlock = new RemoteRequestCompletion() {
                @Override
                public void onCompletion(Response httpResponse, Object result, Throwable e) {
                    if (e instanceof RemoteRequestResponseException) {
                        RemoteRequestResponseException htre = (RemoteRequestResponseException) e;
                        if (htre.getCode() == 503) {
                            received503Error.countDown();
                        }
                    }
                }
            };

            ScheduledExecutorService requestExecutorService =
                    Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService =
                    Executors.newSingleThreadScheduledExecutor();

            RemoteRequestRetry request = new RemoteRequestRetry(
                    RemoteRequestRetry.RemoteRequestType.REMOTE_REQUEST,
                    requestExecutorService,
                    workExecutorService,
                    factory,
                    "GET",
                    url,
                    true,
                    true,
                    requestBody,
                    null,
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
            for (int i = 0; i < com.couchbase.lite.replicator.RemoteRequestRetry.MAX_RETRIES; i++) {
                RecordedRequest recordedRequest =
                        dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
                assertNotNull(recordedRequest);
            }

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(requestExecutorService);
            Utils.shutdownAndAwaitTermination(workExecutorService);
        } finally {
            assertTrue(MockHelper.shutdown(server, dispatcher));
        }
    }

    /**
     * Reproduce a severe issue where the pusher stops working because it's remoteRequestExecutor
     * is full of tasks which are all blocked trying to add more tasks to the queue.
     */
    public void failingTestRetryQueueDeadlock() throws Exception {
        // lower retry to speed up test
        com.couchbase.lite.replicator.RemoteRequestRetry.RETRY_DELAY_MS = 5;

        PersistentCookieJar cookieStore = database.getPersistentCookieStore();
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

            server.start();

            URL url = new URL(String.format(Locale.ENGLISH, "%s/%s", server.url("/db").url(), "_local"));

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("foo", "bar");

            Map<String, Object> requestHeaders = new HashMap<String, Object>();

            int numRequests = 10;

            final CountDownLatch received503Error = new CountDownLatch(numRequests);

            RemoteRequestCompletion completionBlock = new RemoteRequestCompletion() {
                @Override
                public void onCompletion(Response httpResponse, Object result, Throwable e) {
                    if (e instanceof RemoteRequestResponseException) {
                        RemoteRequestResponseException htre = (RemoteRequestResponseException) e;
                        if (htre.getCode() == 503) {
                            received503Error.countDown();
                        }
                    }
                }
            };

            ScheduledExecutorService requestExecutorService =
                    Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService =
                    Executors.newSingleThreadScheduledExecutor();

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
                        true,
                        requestBody,
                        null,
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
            assertTrue(MockHelper.shutdown(server, dispatcher));
        }
    }

    /**
     * ReplicatorInternal_Tests.m
     * - (void) test22_ParseAuthChallenge
     */
    public void testParseAuthHeader() {
        String authHeader =
                "OIDC login=\"http://127.0.0.1:4984/openid_db/_oidc_testing/authorize?client_id=sync_gateway&redirect_uri=http%3A%2F%2F127.0.0.1%3A4984%2Fopenid_db%2F_oidc_callback&response_type=code&scope=openid+email&state=\"";
        Map challenge = RemoteRequest.parseAuthHeader(authHeader);
        assertNotNull(challenge);
        assertEquals(3, challenge.size());
        assertEquals(authHeader, challenge.get("WWW-Authenticate"));
        assertEquals("OIDC", challenge.get("Scheme"));
        assertTrue(challenge.containsKey("login"));
        assertEquals("http://127.0.0.1:4984/openid_db/_oidc_testing/authorize?client_id=sync_gateway&redirect_uri=http%3A%2F%2F127.0.0.1%3A4984%2Fopenid_db%2F_oidc_callback&response_type=code&scope=openid+email&state=", challenge.get("login"));

        authHeader = null;
        challenge = RemoteRequest.parseAuthHeader(authHeader);
        assertNull(challenge);

        authHeader = "";
        challenge = RemoteRequest.parseAuthHeader(authHeader);
        assertNull(challenge);


        authHeader = "Basic realm=Couchbase";
        challenge = RemoteRequest.parseAuthHeader(authHeader);
        assertNotNull(challenge);
        Map expect = new HashMap();
        expect.put("WWW-Authenticate", authHeader);
        expect.put("Scheme", "Basic");
        expect.put("realm", "Couchbase");
        assertEquals(expect, challenge);

        authHeader = "OIDC login=\"http://example.com/login?foo=bar\"";
        challenge = RemoteRequest.parseAuthHeader(authHeader);
        assertNotNull(challenge);
        expect = new HashMap();
        expect.put("WWW-Authenticate", authHeader);
        expect.put("Scheme", "OIDC");
        expect.put("login", "http://example.com/login?foo=bar");
        assertEquals(expect, challenge);

        authHeader = "OIDC login=\"http://example.com/login?foo=bar\",something=other";
        challenge = RemoteRequest.parseAuthHeader(authHeader);
        assertNotNull(challenge);
        expect = new HashMap();
        expect.put("WWW-Authenticate", authHeader);
        expect.put("Scheme", "OIDC");
        expect.put("login", "http://example.com/login?foo=bar");
        assertEquals(expect, challenge);
    }
}
