package com.couchbase.cblite.testapp.tests;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.os.AsyncTask;
import android.util.Log;

import com.couchbase.cblite.replicator.changetracker.CBLChangeTracker;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTracker.TDChangeTrackerMode;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTrackerClient;

public class ChangeTracker extends CBLiteTestCase {

    public static final String TAG = "ChangeTracker";

    public void testChangeTracker() throws Throwable {

        URL testURL = getReplicationURL();

        CBLChangeTrackerClient client = new CBLChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(CBLChangeTracker tracker) {
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

        final CBLChangeTracker changeTracker = new CBLChangeTracker(testURL, TDChangeTrackerMode.OneShot, 0, client, null);

        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                changeTracker.start();
            }
        });

        while(changeTracker.isRunning()) {
            Thread.sleep(1000);
        }

    }

    public void testChangeTrackerLongPoll() throws Throwable {

        URL testURL = getReplicationURL();

        CBLChangeTrackerClient client = new CBLChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(CBLChangeTracker tracker) {
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

        final CBLChangeTracker changeTracker = new CBLChangeTracker(testURL, TDChangeTrackerMode.LongPoll, 0, client, null);

        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                changeTracker.start();
            }
        });

        Thread.sleep(10*1000);

    }

    public void testChangeTrackerContinuous() throws Throwable {

        URL testURL = getReplicationURL();

        CBLChangeTrackerClient client = new CBLChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(CBLChangeTracker tracker) {
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

        final CBLChangeTracker changeTracker = new CBLChangeTracker(testURL, TDChangeTrackerMode.Continuous, 0, client, null);

        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                changeTracker.start();
            }
        });

        Thread.sleep(10*1000);

    }

    public void testChangeTrackerWithFilterURL() throws Throwable {

        URL testURL = getReplicationURL();
        CBLChangeTracker changeTracker = new CBLChangeTracker(testURL, TDChangeTrackerMode.Continuous, 0, null, null);

        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String,Object> filterMap = new HashMap<String,Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        Assert.assertEquals("_changes?feed=continuous&heartbeat=300000&since=0&filter=filter&param=value", changeTracker.getChangesFeedPath());

    }

    public void testChangeTrackerBackoff() throws Throwable {

        URL testURL = getReplicationURL();
        final MockHttpClient mockHttpClient = new MockHttpClient();

        CBLChangeTrackerClient client = new CBLChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(CBLChangeTracker tracker) {
                Log.v(TAG, "changeTrackerStopped");
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                Log.v(TAG, "changeTrackerReceivedChange: " + seq.toString());
            }

            @Override
            public org.apache.http.client.HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };

        final CBLChangeTracker changeTracker = new CBLChangeTracker(testURL, CBLChangeTracker.TDChangeTrackerMode.Continuous, 0, client, null);

        AsyncTask task = new AsyncTask<Object, Object, Object>() {
            @Override
            protected Object doInBackground(Object... aParams) {
                changeTracker.start();
                return null;
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
                        numTimesExectutedAfter10seconds = mockHttpClient.getNumTimesExecuteCalled();
                    }

                    // take another snapshot after 20 seconds have passed
                    if (i == 20) {
                        // by now it should have backed off, so the delta between 10s and 20s should be small
                        int delta = mockHttpClient.getNumTimesExecuteCalled() - numTimesExectutedAfter10seconds;
                        Assert.assertTrue(delta < 25);
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

class MockHttpClient implements org.apache.http.client.HttpClient {

    private int numTimesExecuteCalled = 0;

    public int getNumTimesExecuteCalled() {
        return numTimesExecuteCalled;
    }

    @Override
    public HttpParams getParams() {
        return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return null;
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        throw new IOException("Test IOException");
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        throw new IOException("Test IOException");
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        throw new IOException("Test IOException");
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        throw new IOException("Test IOException");
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        throw new IOException("<T> Test IOException");
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        throw new IOException("<T> Test IOException");
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        throw new IOException("<T> Test IOException");
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        throw new IOException("<T> Test IOException");
    }


}