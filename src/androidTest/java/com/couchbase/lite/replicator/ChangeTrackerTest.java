package com.couchbase.lite.replicator;

import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChangeTrackerTest extends LiteTestCaseWithDB {

    public static final String TAG = "ChangeTracker";

    /**
     * Test case for CBL Java Core #317 (CBL Java #39)
     * https://github.com/couchbase/couchbase-lite-java-core/issues/317
     */
    public void testChangeTrackerNullPointerException() throws Throwable {
        // Null pointer issue is not 100% reproducible. so try 10 times...
        for(int i = 0; i < 10; i++)
            changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.OneShot, true, true);
    }

    public void testChangeTrackerOneShot() throws Throwable {
        changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.OneShot, true, false);
    }

    public void testChangeTrackerLongPoll() throws Throwable {
        changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.LongPoll, true, false);
    }

    public void changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode mode, final boolean useMockReplicator, final boolean checkLastException) throws Throwable {

        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        final CountDownLatch changeReceivedSignal = new CountDownLatch(1);

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerFinished(ChangeTracker tracker) {
            }

            @Override
            public void changeTrackerCaughtUp() {

            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                if (useMockReplicator) {
                    assertEquals("1", seq.toString());
                }
                changeReceivedSignal.countDown();
            }

            @Override
            public HttpClient getHttpClient() {
                if (useMockReplicator) {
                    CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                    mockHttpClient.setResponder("_changes", new CustomizableMockHttpClient.Responder() {
                        @Override
                        public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                            String json = "{\"results\":[\n" +
                                    "{\"seq\":\"1\",\"id\":\"doc1-138\",\"changes\":[{\"rev\":\"1-82d\"}]}],\n" +
                                    "\"last_seq\":\"*:50\"}";
                            return CustomizableMockHttpClient.generateHttpResponseObject(json);
                        }
                    });
                    return mockHttpClient;
                } else {
                    return new DefaultHttpClient();
                }
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, mode, false, 0, client);
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
        if(checkLastException){
            // CBL Java Core #317
            // This check does not work without fixing Java Core #317 because ChangeTracker status
            // becomes stopped before NullPointer Exception is thrown.
            if(changeTracker.getLastError() != null){
                Log.e(TAG, changeTracker.getLastError().toString());
                assertFalse(changeTracker.getLastError() instanceof NullPointerException);
            }
        }
    }

    public void testChangeTrackerWithConflictsIncluded() throws Throwable {
        URL testURL = getReplicationURL();
        ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, true, 0L, null);
        changeTracker.setUsePOST(false);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&style=all_docs&since=0&limit=50", changeTracker.getChangesFeedPath());

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&style=all_docs&since=0&limit=50", changeTracker.getChangesFeedPath());
        Map<String, Object> postBodyMap = changeTracker.changesFeedPOSTBodyMap();
        assertEquals("longpoll", postBodyMap.get("feed"));
        assertEquals(50, (int) postBodyMap.get("limit"));
        assertEquals(300000L, (long) postBodyMap.get("heartbeat"));
        assertEquals("all_docs", postBodyMap.get("style"));
        assertEquals(0L, (long)postBodyMap.get("since"));
    }

    public void testChangeTrackerWithCompoundLastSequence() throws Throwable {
        URL testURL = getReplicationURL();
        ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, true, "1234:56", null);
        changeTracker.setUsePOST(false);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&style=all_docs&since=1234%3A56&limit=50", changeTracker.getChangesFeedPath());

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&style=all_docs&since=1234%3A56&limit=50", changeTracker.getChangesFeedPath());
        Map<String, Object> postBodyMap = changeTracker.changesFeedPOSTBodyMap();
        assertEquals("longpoll", postBodyMap.get("feed"));
        assertEquals(50, (int)postBodyMap.get("limit"));
        assertEquals(300000L, (long)postBodyMap.get("heartbeat"));
        assertEquals("all_docs", postBodyMap.get("style"));
        assertEquals("1234:56", postBodyMap.get("since"));
    }

    public void testChangeTrackerWithFilterURL() throws Throwable {

        URL testURL = getReplicationURL();
        ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, false, 0L, null);


        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String,Object> filterMap = new HashMap<String,Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        changeTracker.setUsePOST(false);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&since=0&limit=50&filter=filter&param=value", changeTracker.getChangesFeedPath());

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&since=0&limit=50", changeTracker.getChangesFeedPath());
        Map<String, Object> body = changeTracker.changesFeedPOSTBodyMap();
        assertTrue(body.containsKey("filter"));
        assertEquals("filter", body.get("filter"));
        assertTrue(body.containsKey("param"));
        assertEquals("value", body.get("param"));
    }

    public void testChangeTrackerWithDocsIds() throws Exception {
        URL testURL = getReplicationURL();

        ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, false, 0L, null);
        changeTracker.setUsePOST(false);
        List<String> docIds = new ArrayList<String>();
        docIds.add("doc1");
        docIds.add("doc2");
        changeTracker.setDocIDs(docIds);

        String docIdsUnencoded = "[\"doc1\",\"doc2\"]";
        String docIdsEncoded = URLEncoder.encode(docIdsUnencoded);
        String expectedFeedPath = String.format("_changes?feed=longpoll&heartbeat=300000&since=0&limit=50&filter=_doc_ids&doc_ids=%s", docIdsEncoded);
        final String changesFeedPath = changeTracker.getChangesFeedPath();
        assertEquals(expectedFeedPath, changesFeedPath);

        changeTracker.setUsePOST(true);
        assertEquals("_changes?feed=longpoll&heartbeat=300000&since=0&limit=50", changeTracker.getChangesFeedPath());
        Map<String, Object> postBodyMap = changeTracker.changesFeedPOSTBodyMap();
        assertEquals("_doc_ids", postBodyMap.get("filter"));
        assertEquals(docIds, postBodyMap.get("doc_ids"));
        String postBody = changeTracker.changesFeedPOSTBody();
        assertTrue(postBody.contains(docIdsUnencoded));
    }

    public void testChangeTrackerBackoffExceptions() throws Throwable {
        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderThrowExceptionAllRequests();
        testChangeTrackerBackoff(mockHttpClient);
    }

    public void testChangeTrackerBackoffInvalidJson() throws Throwable {
        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderReturnInvalidChangesFeedJson();
        testChangeTrackerBackoff(mockHttpClient);
    }

    public void testChangeTrackerRecoverableError() throws Exception {
        int errorCode = 503;
        String statusMessage = "Transient Error";
        int numExpectedChangeCallbacks = 2;
        runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode.LongPoll, errorCode, statusMessage, numExpectedChangeCallbacks);
    }

    public void testChangeTrackerRecoverableIOException() throws Exception {
        int errorCode = -1;  // special code to tell it to throw an IOException
        String statusMessage = null;
        int numExpectedChangeCallbacks = 2;
        runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode.LongPoll, errorCode, statusMessage, numExpectedChangeCallbacks);
    }

    public void testChangeTrackerNonRecoverableError() throws Exception {
        int errorCode = 404;
        String statusMessage = "NOT FOUND";
        int numExpectedChangeCallbacks = 1;
        runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode.LongPoll, errorCode, statusMessage, numExpectedChangeCallbacks);
    }

    private void runChangeTrackerTransientError(ChangeTracker.ChangeTrackerMode mode,
                                                final int errorCode,
                                                final String statusMessage,
                                                int numExpectedChangeCallbacks) throws Exception {

        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        final CountDownLatch changeReceivedSignal = new CountDownLatch(numExpectedChangeCallbacks);

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {
            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerFinished(ChangeTracker tracker) {
            }

            @Override
            public void changeTrackerCaughtUp() {

            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                changeReceivedSignal.countDown();
            }

            @Override
            public HttpClient getHttpClient() {
                CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                CustomizableMockHttpClient.Responder sentinal = defaultChangesResponder();
                Queue<CustomizableMockHttpClient.Responder> responders = new LinkedList<CustomizableMockHttpClient.Responder>();
                responders.add(defaultChangesResponder());
                responders.add(CustomizableMockHttpClient.transientErrorResponder(errorCode, statusMessage));
                ResponderChain responderChain = new ResponderChain(responders, sentinal);
                mockHttpClient.setResponder("_changes", responderChain);
                return mockHttpClient;
            }

            private CustomizableMockHttpClient.Responder defaultChangesResponder() {
                return new CustomizableMockHttpClient.Responder() {
                    @Override
                    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                        String json = "{\"results\":[\n" +
                                "{\"seq\":\"1\",\"id\":\"doc1-138\",\"changes\":[{\"rev\":\"1-82d\"}]}],\n" +
                                "\"last_seq\":\"*:50\"}";
                        return CustomizableMockHttpClient.generateHttpResponseObject(json);
                    }
                };
            }

        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, mode, false, 0L, client);
        changeTracker.setUsePOST(isTestingAgainstSyncGateway());
        changeTracker.start();

        try {
            boolean success = changeReceivedSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        changeTracker.stop();

        try {
            boolean success = changeTrackerFinishedSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void testChangeTrackerBackoff(final CustomizableMockHttpClient mockHttpClient) throws Throwable {

        URL testURL = getReplicationURL();

        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);

        ChangeTrackerClient client = new ChangeTrackerClient() {
            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                Log.v(TAG, "changeTrackerStopped");
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerFinished(ChangeTracker tracker) {
                Log.v(TAG, "changeTrackerFinished");
            }

            @Override
            public void changeTrackerCaughtUp() {

            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                Log.v(TAG, "changeTrackerReceivedChange: %d" + seq.toString());
            }

            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, false, 0L, client);
        changeTracker.setUsePOST(isTestingAgainstSyncGateway());
        changeTracker.start();

        // sleep for a few seconds
        Thread.sleep(5 * 1000);

        // make sure we got less than 10 requests in those 10 seconds (if it was hammering, we'd get a lot more)
        assertTrue(mockHttpClient.getCapturedRequests().size() < 25);
        //assertTrue(changeTracker.backoff.getNumAttempts() > 0);

        mockHttpClient.clearResponders();
        mockHttpClient.addResponderReturnEmptyChangesFeed();

        // at this point, the change tracker backoff should cause it to sleep for about 3 seconds
        // and so lets wait 3 seconds until it wakes up and starts getting valid responses
        Thread.sleep(3 * 1000);

        // now find the delta in requests received in a 2s period
        int before = mockHttpClient.getCapturedRequests().size();
        Thread.sleep(2 * 1000);
        int after = mockHttpClient.getCapturedRequests().size();

        // assert that the delta is high, because at this point the change tracker should
        // be hammering away
        //assertTrue((after - before) > 25);

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

    // ChangeTrackerMode.Continuous mode does not work, do not use it.
    public void failingTestChangeTrackerContinuous() throws Throwable {

        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        final CountDownLatch changeReceivedSignal = new CountDownLatch(1);

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {
            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerFinished(ChangeTracker tracker) {
            }

            @Override
            public void changeTrackerCaughtUp() {

            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                changeReceivedSignal.countDown();
            }

            @Override
            public HttpClient getHttpClient() {
                return new DefaultHttpClient();
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.Continuous, false, 0L, client);
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
    }
}
