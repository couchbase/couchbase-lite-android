package com.couchbase.lite.replicator;

import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.threading.BackgroundTask;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChangeTrackerTest extends LiteTestCase {

    public static final String TAG = "ChangeTracker";

    public void testChangeTracker() throws Throwable {

        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
            }

            @Override
            public HttpClient getHttpClient() {
            	return new DefaultHttpClient();
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.OneShot, 0, client);
        changeTracker.start();

        try {
            boolean success = changeTrackerFinishedSignal.await(300, TimeUnit.SECONDS);
            assertTrue(success);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void testChangeTrackerLongPoll() throws Throwable {
        changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.LongPoll);
    }

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
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                changeReceivedSignal.countDown();
            }

            @Override
            public HttpClient getHttpClient() {
                return new DefaultHttpClient();
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.Continuous, 0, client);
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

    public void changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode mode) throws Throwable {

        final CountDownLatch changeTrackerFinishedSignal = new CountDownLatch(1);
        final CountDownLatch changeReceivedSignal = new CountDownLatch(1);

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                changeTrackerFinishedSignal.countDown();
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                assertEquals("*:1", seq.toString());
                changeReceivedSignal.countDown();
            }

            @Override
            public HttpClient getHttpClient() {
                CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                mockHttpClient.setResponder("_changes", new CustomizableMockHttpClient.Responder() {
                    @Override
                    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                        String json = "{\"results\":[\n" +
                                "{\"seq\":\"*:1\",\"id\":\"doc1-138\",\"changes\":[{\"rev\":\"1-82d\"}]}],\n" +
                                "\"last_seq\":\"*:50\"}";
                        return CustomizableMockHttpClient.generateHttpResponseObject(json);
                    }
                });
                return mockHttpClient;
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, mode, 0, client);
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

    public void testChangeTrackerWithFilterURL() throws Throwable {

        URL testURL = getReplicationURL();
        ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, 0, null);

        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String,Object> filterMap = new HashMap<String,Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        assertEquals("_changes?feed=longpoll&limit=50&heartbeat=300000&since=0&filter=filter&param=value", changeTracker.getChangesFeedPath());

    }

    public void testChangeTrackerWithDocsIds() {

        URL testURL = getReplicationURL();

        ChangeTracker changeTrackerDocIds = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, 0, null);
        List<String> docIds = new ArrayList<String>();
        docIds.add("doc1");
        docIds.add("doc2");
        changeTrackerDocIds.setDocIDs(docIds);

        String docIdsEncoded = URLEncoder.encode("[\"doc1\",\"doc2\"]");
        String expectedFeedPath = String.format("_changes?feed=longpoll&limit=50&heartbeat=300000&since=0&filter=_doc_ids&doc_ids=%s", docIdsEncoded);
        final String changesFeedPath = changeTrackerDocIds.getChangesFeedPath();
        assertEquals(expectedFeedPath, changesFeedPath);

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

    private void testChangeTrackerBackoff(final CustomizableMockHttpClient mockHttpClient) throws Throwable {

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                Log.v(TAG, "changeTrackerStopped");
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                Log.v(TAG, "changeTrackerReceivedChange: " + seq.toString());
            }

            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.LongPoll, 0, client);

        BackgroundTask task = new BackgroundTask() {
            @Override
            public void run() {
                changeTracker.start();
            }
        };
        task.execute();

        // sleep for 10 seconds
        Thread.sleep(5 * 1000);

        // make sure we got less than 10 requests in those 10 seconds (if it was hammering, we'd get a lot more)
        assertTrue(mockHttpClient.getCapturedRequests().size() < 25);
        assertTrue(changeTracker.backoff.getNumAttempts() > 0);

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
        assertTrue((after - before) > 25);

        // the backoff numAttempts should have been reset to 0
        assertTrue(changeTracker.backoff.getNumAttempts() == 0);



    }



}
