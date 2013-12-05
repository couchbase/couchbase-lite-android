package com.couchbase.lite;

import com.couchbase.lite.replicator.changetracker.ChangeTracker.TDChangeTrackerMode;
import com.couchbase.lite.replicator.changetracker.ChangeTrackerClient;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class ChangeTrackerTest extends CBLiteTestCase {

    public static final String TAG = "ChangeTracker";

    public void testChangeTracker() throws Throwable {

        URL testURL = getReplicationURL();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(com.couchbase.lite.replicator.changetracker.ChangeTracker tracker) {
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

        final com.couchbase.lite.replicator.changetracker.ChangeTracker changeTracker = new com.couchbase.lite.replicator.changetracker.ChangeTracker(testURL, TDChangeTrackerMode.OneShot, 0, client);

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

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(com.couchbase.lite.replicator.changetracker.ChangeTracker tracker) {
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

        final com.couchbase.lite.replicator.changetracker.ChangeTracker changeTracker = new com.couchbase.lite.replicator.changetracker.ChangeTracker(testURL, TDChangeTrackerMode.LongPoll, 0, client);

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

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(com.couchbase.lite.replicator.changetracker.ChangeTracker tracker) {
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

        final com.couchbase.lite.replicator.changetracker.ChangeTracker changeTracker = new com.couchbase.lite.replicator.changetracker.ChangeTracker(testURL, TDChangeTrackerMode.Continuous, 0, client);

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
        com.couchbase.lite.replicator.changetracker.ChangeTracker changeTracker = new com.couchbase.lite.replicator.changetracker.ChangeTracker(testURL, TDChangeTrackerMode.Continuous, 0, null);

        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String,Object> filterMap = new HashMap<String,Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        assertEquals("_changes?feed=continuous&heartbeat=300000&since=0&filter=filter&param=value", changeTracker.getChangesFeedPath());

    }

    public void testChangeTrackerBackoff() throws Throwable {

        URL testURL = getReplicationURL();
        final MockHttpClient mockHttpClient = new MockHttpClient();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(com.couchbase.lite.replicator.changetracker.ChangeTracker tracker) {
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

        final com.couchbase.lite.replicator.changetracker.ChangeTracker changeTracker = new com.couchbase.lite.replicator.changetracker.ChangeTracker(testURL, com.couchbase.lite.replicator.changetracker.ChangeTracker.TDChangeTrackerMode.Continuous, 0, client);

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
                        numTimesExectutedAfter10seconds = mockHttpClient.getNumTimesExecuteCalled();
                    }

                    // take another snapshot after 20 seconds have passed
                    if (i == 20) {
                        // by now it should have backed off, so the delta between 10s and 20s should be small
                        int delta = mockHttpClient.getNumTimesExecuteCalled() - numTimesExectutedAfter10seconds;
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
        final MockHttpClientNeverResponds mockHttpClient = new MockHttpClientNeverResponds();

        ChangeTrackerClient client = new ChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(com.couchbase.lite.replicator.changetracker.ChangeTracker tracker) {
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

        final com.couchbase.lite.replicator.changetracker.ChangeTracker changeTracker = new com.couchbase.lite.replicator.changetracker.ChangeTracker(testURL, TDChangeTrackerMode.LongPoll, 0, client);

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
                        numTimesExectutedAfter10seconds = mockHttpClient.getNumTimesExecuteCalled();
                    }

                    // take another snapshot after 20 seconds have passed
                    if (i == 20) {
                        // by now it should have backed off, so the delta between 10s and 20s should be small
                        int delta = mockHttpClient.getNumTimesExecuteCalled() - numTimesExectutedAfter10seconds;
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



class MockHttpClientNeverResponds implements org.apache.http.client.HttpClient {

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
        HttpResponse response = new HttpResponse() {
            @Override
            public StatusLine getStatusLine() {
                StatusLine statusLine = new StatusLine() {
                    @Override
                    public ProtocolVersion getProtocolVersion() {
                        return null;
                    }

                    @Override
                    public int getStatusCode() {
                        return 200;
                    }

                    @Override
                    public String getReasonPhrase() {
                        return null;
                    }
                };
                return statusLine;
            }

            @Override
            public void setStatusLine(StatusLine statusLine) {

            }

            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i) {

            }

            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {

            }

            @Override
            public void setStatusCode(int i) throws IllegalStateException {

            }

            @Override
            public void setReasonPhrase(String s) throws IllegalStateException {

            }

            @Override
            public HttpEntity getEntity() {
                StringEntity stringEntity = null;
                try {
                    stringEntity = new StringEntity("invalid_json");
                } catch (UnsupportedEncodingException e) {
                    new IllegalStateException(e);
                }
                return stringEntity;
            }

            @Override
            public void setEntity(HttpEntity httpEntity) {

            }

            @Override
            public Locale getLocale() {
                return null;
            }

            @Override
            public void setLocale(Locale locale) {

            }

            @Override
            public ProtocolVersion getProtocolVersion() {
                return null;
            }

            @Override
            public boolean containsHeader(String s) {
                return false;
            }

            @Override
            public Header[] getHeaders(String s) {
                return new Header[0];
            }

            @Override
            public Header getFirstHeader(String s) {
                return null;
            }

            @Override
            public Header getLastHeader(String s) {
                return null;
            }

            @Override
            public Header[] getAllHeaders() {
                return new Header[0];
            }

            @Override
            public void addHeader(Header header) {

            }

            @Override
            public void addHeader(String s, String s2) {

            }

            @Override
            public void setHeader(Header header) {

            }

            @Override
            public void setHeader(String s, String s2) {

            }

            @Override
            public void setHeaders(Header[] headers) {

            }

            @Override
            public void removeHeader(Header header) {

            }

            @Override
            public void removeHeaders(String s) {

            }

            @Override
            public HeaderIterator headerIterator() {
                return null;
            }

            @Override
            public HeaderIterator headerIterator(String s) {
                return null;
            }

            @Override
            public HttpParams getParams() {
                return null;
            }

            @Override
            public void setParams(HttpParams httpParams) {

            }
        };
        return response;
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