/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
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
package com.couchbase.lite.replicator;

import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.util.Log;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ChangeTrackerTest extends LiteTestCaseWithDB {

    public static final String TAG = "ChangeTracker";

    /**
     * Test case for CBL Java Core #317 (CBL Java #39)
     * https://github.com/couchbase/couchbase-lite-java-core/issues/317
     */
    public void testChangeTrackerNullPointerException() throws Throwable {
        // Null pointer issue is not 100% reproducible. so try 10 times...
        for (int i = 0; i < 10; i++)
            changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.OneShot, true, true);
    }

    public void testChangeTrackerOneShot() throws Throwable {
        changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.OneShot, true, false);
    }

    public void testChangeTrackerLongPoll() throws Throwable {
        changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.LongPoll, true, false);
    }

    public void changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode mode,
                                          final boolean useMockReplicator,
                                          final boolean checkLastException) throws Throwable {
        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        final CountDownLatch changeReceivedSignal = new CountDownLatch(1);

        ChangeTrackerClient client = new DefaultChangeTrackerClient() {
            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                if (useMockReplicator) {
                    assertEquals("1", seq.toString());
                }
                changeReceivedSignal.countDown();
            }

            public OkHttpClient getOkHttpClient() {
                Interceptor interceptor = new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        if ("_changes".equals(request.url().pathSegments().get(1))) {
                            String json = "{\"results\":[\n" +
                                    "{\"seq\":\"1\",\"id\":\"doc1-138\",\"changes\":[{\"rev\":\"1-82d\"}]}],\n" +
                                    "\"last_seq\":\"*:50\"}";
                            Response.Builder builder = new Response.Builder()
                                    .request(request)
                                    .code(200)
                                    .protocol(Protocol.HTTP_1_1)
                                    .body(ResponseBody.create(OkHttpUtils.JSON, json));
                            return builder.build();
                        }
                        return chain.proceed(request);
                    }
                };
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };

        final ChangeTracker changeTracker =
                new ChangeTracker(getReplicationURL(), mode, false, 0, client);
        changeTracker.setUsePOST(isTestingAgainstSyncGateway());
        changeTracker.start();

        try {
            boolean success = changeReceivedSignal.await(300, TimeUnit.SECONDS);
            assertTrue(success);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        changeTracker.stop();

        try {
            boolean success = changeTrackerFinishedSignal.await(300, TimeUnit.SECONDS);
            assertTrue(success);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check for NullPointer Exception or any Exception
        if (checkLastException) {
            // CBL Java Core #317
            // This check does not work without fixing Java Core #317 because ChangeTracker status
            // becomes stopped before NullPointer Exception is thrown.
            if (changeTracker.getLastError() != null) {
                Log.e(TAG, changeTracker.getLastError().toString());
                assertFalse(changeTracker.getLastError() instanceof NullPointerException);
            }
        }
    }

    public void testChangeTrackerWithConflictsIncluded() throws Throwable {
        ChangeTracker changeTracker = new ChangeTracker(getReplicationURL(),
                ChangeTracker.ChangeTrackerMode.LongPoll, true, 0L, null);
        changeTracker.setUsePOST(false);
        assertEquals("_changes?feed=longpoll&heartbeat=30000&style=all_docs&since=0&limit=50",
                changeTracker.getChangesFeedPath());

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=30000&style=all_docs&since=0&limit=50",
                changeTracker.getChangesFeedPath());
        Map<String, Object> postBodyMap = changeTracker.changesFeedPOSTBodyMap();
        assertEquals("longpoll", postBodyMap.get("feed"));
        assertEquals(50, (int) postBodyMap.get("limit"));
        assertEquals(30000L, (long) postBodyMap.get("heartbeat"));
        assertEquals("all_docs", postBodyMap.get("style"));
        assertEquals(0L, (long) postBodyMap.get("since"));
    }

    public void testChangeTrackerWithCompoundLastSequence() throws Throwable {
        ChangeTracker changeTracker = new ChangeTracker(getReplicationURL(),
                ChangeTracker.ChangeTrackerMode.LongPoll, true, "1234:56", null);
        changeTracker.setUsePOST(false);
        assertEquals(
                "_changes?feed=longpoll&heartbeat=30000&style=all_docs&since=1234%3A56&limit=50",
                changeTracker.getChangesFeedPath());

        changeTracker.setUsePOST(true);
        assertEquals(
                "_changes?feed=longpoll&heartbeat=30000&style=all_docs&since=1234%3A56&limit=50",
                changeTracker.getChangesFeedPath());
        Map<String, Object> postBodyMap = changeTracker.changesFeedPOSTBodyMap();
        assertEquals("longpoll", postBodyMap.get("feed"));
        assertEquals(50, (int) postBodyMap.get("limit"));
        assertEquals(30000L, (long) postBodyMap.get("heartbeat"));
        assertEquals("all_docs", postBodyMap.get("style"));
        assertEquals("1234:56", postBodyMap.get("since"));
    }

    public void testChangeTrackerWithFilterURL() throws Throwable {
        ChangeTracker changeTracker = new ChangeTracker(getReplicationURL(),
                ChangeTracker.ChangeTrackerMode.LongPoll, false, 0L, null);

        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String, Object> filterMap = new HashMap<String, Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        changeTracker.setUsePOST(false);
        assertEquals(
                "_changes?feed=longpoll&heartbeat=30000&since=0&limit=50&filter=filter&param=value",
                changeTracker.getChangesFeedPath());

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=30000&since=0&limit=50&filter=filter",
                changeTracker.getChangesFeedPath());
        Map<String, Object> body = changeTracker.changesFeedPOSTBodyMap();
        assertTrue(body.containsKey("filter"));
        assertEquals("filter", body.get("filter"));
        assertTrue(body.containsKey("param"));
        assertEquals("value", body.get("param"));
    }

    public void testChangeTrackerWithDocsIds() throws Exception {
        URL testURL = getReplicationURL();

        ChangeTracker changeTracker = new ChangeTracker(testURL,
                ChangeTracker.ChangeTrackerMode.LongPoll, false, 0L, null);
        changeTracker.setUsePOST(false);
        List<String> docIds = new ArrayList<String>();
        docIds.add("doc1");
        docIds.add("doc2");
        changeTracker.setDocIDs(docIds);

        String docIdsUnencoded = "[\"doc1\",\"doc2\"]";
        String docIdsEncoded = URLEncoder.encode(docIdsUnencoded);
        String expectedFeedPath = String.format(Locale.ENGLISH,
                "_changes?feed=longpoll&heartbeat=30000&since=0&limit=50&filter=_doc_ids&doc_ids=%s",
                docIdsEncoded);
        final String changesFeedPath = changeTracker.getChangesFeedPath();
        assertEquals(expectedFeedPath, changesFeedPath);

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=30000&since=0&limit=50&filter=_doc_ids",
                changeTracker.getChangesFeedPath());
        Map<String, Object> postBodyMap = changeTracker.changesFeedPOSTBodyMap();
        assertEquals("_doc_ids", postBodyMap.get("filter"));
        assertEquals(docIds, postBodyMap.get("doc_ids"));
        String postBody = changeTracker.changesFeedPOSTBody();
        assertTrue(postBody.contains(docIdsUnencoded));
    }

    public void testChangeTrackerBackoffExceptions() throws Throwable {
        CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderThrowExceptionAllRequests();
        testChangeTrackerBackoff(interceptor);
    }

    public void testChangeTrackerBackoffInvalidJson() throws Throwable {
        CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderReturnInvalidChangesFeedJson();
        testChangeTrackerBackoff(interceptor);
    }

    public void testChangeTrackerRecoverableError() throws Exception {
        int errorCode = 503;
        String statusMessage = "Transient Error";
        int numExpectedChangeCallbacks = 2;
        runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode.LongPoll,
                errorCode, statusMessage, numExpectedChangeCallbacks);
    }

    public void testChangeTrackerRecoverableIOException() throws Exception {
        int errorCode = -1;  // special code to tell it to throw an IOException
        String statusMessage = null;
        int numExpectedChangeCallbacks = 2;
        runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode.LongPoll,
                errorCode, statusMessage, numExpectedChangeCallbacks);
    }

    public void testChangeTrackerNonRecoverableError() throws Exception {
        int errorCode = 404;
        String statusMessage = "NOT FOUND";
        int numExpectedChangeCallbacks = 1;
        runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode.LongPoll,
                errorCode, statusMessage, numExpectedChangeCallbacks);
    }

    private Interceptor defaultInterceptor() {
        return new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                if ("_changes".equals(request.url().pathSegments().get(1))) {
                    String json = "{\"results\":[\n" +
                            "{\"seq\":\"1\",\"id\":\"doc1-138\",\"changes\":[{\"rev\":\"1-82d\"}]}],\n" +
                            "\"last_seq\":\"*:50\"}";
                    Response.Builder builder = new Response.Builder()
                            .request(request)
                            .code(200)
                            .protocol(Protocol.HTTP_1_1)
                            .body(ResponseBody.create(OkHttpUtils.JSON, json));
                    return builder.build();
                }
                return chain.proceed(request);
            }
        };
    }

    private Interceptor createInterceptor(final int code, final String message) {
        return new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                Response.Builder builder = new Response.Builder()
                        .request(request)
                        .code(code)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(OkHttpUtils.TEXT, message));
                return builder.build();
            }
        };
    }

    private void runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode mode,
                                                final int errorCode,
                                                final String statusMessage,
                                                int numExpectedChangeCallbacks) throws Exception {
        final CountDownLatch finishedSignal = new CountDownLatch(1);
        final CountDownLatch receivedSignal = new CountDownLatch(numExpectedChangeCallbacks);

        final List<Interceptor> interceptors = new ArrayList<Interceptor>();
        interceptors.add(defaultInterceptor());
        interceptors.add(createInterceptor(errorCode, statusMessage));
        ChangeTrackerClient client = new DefaultChangeTrackerClient() {
            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                finishedSignal.countDown();
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                receivedSignal.countDown();
            }

            @Override
            public OkHttpClient getOkHttpClient() {
                Interceptor interceptor = interceptors.remove(0);
                if (interceptor != null) {
                    return new OkHttpClient.Builder().addInterceptor(interceptor).build();
                }
                throw new RuntimeException("no more response");
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(getReplicationURL(),
                mode, false, 0L, client);
        changeTracker.setUsePOST(isTestingAgainstSyncGateway());
        changeTracker.start();
        assertTrue(receivedSignal.await(30, TimeUnit.SECONDS));
        changeTracker.stop();
        assertTrue(finishedSignal.await(30, TimeUnit.SECONDS));
    }

    private void testChangeTrackerBackoff(final CustomizableMockInterceptor interceptor)
            throws Throwable {
        URL testURL = getReplicationURL();
        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        ChangeTrackerClient client = new DefaultChangeTrackerClient() {
            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        final ChangeTracker changeTracker = new ChangeTracker(testURL,
                ChangeTracker.ChangeTrackerMode.LongPoll, false, 0L, client);
        changeTracker.setUsePOST(isTestingAgainstSyncGateway());
        changeTracker.start();
        // sleep for a few seconds
        Thread.sleep(5 * 1000);
        // make sure we got less than 10 requests in those 10 seconds (if it was hammering, we'd get a lot more)
        assertTrue(interceptor.getCapturedRequests().size() < 25);

        interceptor.clearResponders();
        interceptor.addResponseReturnEmptyChangesFeed();
        // at this point, the change tracker backoff should cause it to sleep for about 3 seconds
        // and so lets wait 3 seconds until it wakes up and starts getting valid responses
        Thread.sleep(3 * 1000);

        // now find the delta in requests received in a 2s period
        int before = interceptor.getCapturedRequests().size();
        Thread.sleep(2 * 1000);
        int after = interceptor.getCapturedRequests().size();

        // the backoff numAttempts should have been reset to 0
        assertTrue(changeTracker.backoff.getNumAttempts() == 0);

        changeTracker.stop();
        try {
            boolean success = changeTrackerFinishedSignal.await(300, TimeUnit.SECONDS);
            assertTrue(success);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
