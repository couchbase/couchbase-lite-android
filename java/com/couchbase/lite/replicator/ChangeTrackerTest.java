package com.couchbase.lite.replicator;

import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.threading.BackgroundTask;
import com.couchbase.lite.util.Log;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChangeTrackerTest extends LiteTestCase {

    public static final String TAG = "ChangeTracker";

    public void testChangeTracker() throws Throwable {

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(ChangeTracker tracker) {
                Log.v(TAG, "See change tracker stopped");
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                Log.v(TAG, "See change " + seq.toString());
            }

            @Override
            public HttpClient getHttpClient() {
            	return new DefaultHttpClient();
            }
        };

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.OneShot, 0, client);
        changeTracker.start();

        while(changeTracker.isRunning()) {
            Thread.sleep(1000);
        }

    }

    public void testChangeTrackerLongPoll() throws Throwable {
        changeTrackerTestWithMode(ChangeTracker.ChangeTrackerMode.LongPoll);
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

    public void testChangeTrackerWithFilterURL() throws Throwable {

        URL testURL = getReplicationURL();
        ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.Continuous, 0, null);

        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String,Object> filterMap = new HashMap<String,Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        assertEquals("_changes?feed=continuous&heartbeat=300000&since=0&filter=filter&param=value", changeTracker.getChangesFeedPath());

    }

    public void testChangeTrackerWithDocsIds() {

        URL testURL = getReplicationURL();

        ChangeTracker changeTrackerDocIds = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.Continuous, 0, null);
        List<String> docIds = new ArrayList<String>();
        docIds.add("doc1");
        docIds.add("doc2");
        changeTrackerDocIds.setDocIDs(docIds);

        String docIdsEncoded = URLEncoder.encode("[\"doc1\",\"doc2\"]");
        String expectedFeedPath = String.format("_changes?feed=continuous&heartbeat=300000&since=0&filter=_doc_ids&doc_ids=%s", docIdsEncoded);
        final String changesFeedPath = changeTrackerDocIds.getChangesFeedPath();
        assertEquals(expectedFeedPath, changesFeedPath);

    }

    public void testChangeTrackerBackoff() throws Throwable {

        URL testURL = getReplicationURL();

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderThrowExceptionAllRequests();


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

        final ChangeTracker changeTracker = new ChangeTracker(testURL, ChangeTracker.ChangeTrackerMode.Continuous, 0, client);

        BackgroundTask task = new BackgroundTask() {
            @Override
            public void run() {
                changeTracker.start();
            }
        };
        task.execute();

        try {

            // expected behavior:
            // when:
            //    mockHttpClient throws IOExceptions -> it should start high and then back off and numTimesExecute should be low

            for (int i=0; i<30; i++) {

                int numTimesExectutedAfter10seconds = 0;

                try {
                    Thread.sleep(1000);

                    // take a snapshot of num times the http client was called after 10 seconds
                    if (i == 10) {
                        numTimesExectutedAfter10seconds = mockHttpClient.getCapturedRequests().size();
                    }

                    // take another snapshot after 20 seconds have passed
                    if (i == 20) {
                        // by now it should have backed off, so the delta between 10s and 20s should be small
                        int delta = mockHttpClient.getCapturedRequests().size() - numTimesExectutedAfter10seconds;
                        assertTrue(delta < 25);
                    }


                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            changeTracker.stop();
        }



    }



    public void testChangeTrackerInvalidJson() throws Throwable {

        URL testURL = getReplicationURL();

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderThrowExceptionAllRequests();

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

        try {

            // expected behavior:
            // when:
            //    mockHttpClient throws IOExceptions -> it should start high and then back off and numTimesExecute should be low

            for (int i=0; i<30; i++) {

                int numTimesExectutedAfter10seconds = 0;

                try {
                    Thread.sleep(1000);

                    // take a snapshot of num times the http client was called after 10 seconds
                    if (i == 10) {
                        numTimesExectutedAfter10seconds = mockHttpClient.getCapturedRequests().size();
                    }

                    // take another snapshot after 20 seconds have passed
                    if (i == 20) {
                        // by now it should have backed off, so the delta between 10s and 20s should be small
                        int delta = mockHttpClient.getCapturedRequests().size() - numTimesExectutedAfter10seconds;
                        assertTrue(delta < 25);
                    }


                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            changeTracker.stop();
        }



    }

}
