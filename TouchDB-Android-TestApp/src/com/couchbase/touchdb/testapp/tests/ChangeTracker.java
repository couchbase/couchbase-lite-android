package com.couchbase.touchdb.testapp.tests;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.test.InstrumentationTestCase;
import android.util.Log;

import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker.TDChangeTrackerMode;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTrackerClient;

public class ChangeTracker extends InstrumentationTestCase {

    public static final String TAG = "ChangeTracker";

    public void testChangeTracker() throws Throwable {

        URL testURL = new URL("http://mschoch.ic.ht:5984/touch_test");

        TDChangeTrackerClient client = new TDChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(TDChangeTracker tracker) {
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

        final TDChangeTracker changeTracker = new TDChangeTracker(testURL, TDChangeTrackerMode.OneShot, 0, client);

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

        URL testURL = new URL("http://mschoch.ic.ht:5984/touch_test");

        TDChangeTrackerClient client = new TDChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(TDChangeTracker tracker) {
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

        final TDChangeTracker changeTracker = new TDChangeTracker(testURL, TDChangeTrackerMode.LongPoll, 0, client);

        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                changeTracker.start();
            }
        });

        Thread.sleep(10*1000);

    }

    public void testChangeTrackerContinuous() throws Throwable {

        URL testURL = new URL("http://mschoch.ic.ht:5984/touch_test");

        TDChangeTrackerClient client = new TDChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(TDChangeTracker tracker) {
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

        final TDChangeTracker changeTracker = new TDChangeTracker(testURL, TDChangeTrackerMode.Continuous, 0, client);

        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                changeTracker.start();
            }
        });

        Thread.sleep(10*1000);

    }

    public void testChangeTrackerWithFilterURL() throws Throwable {

        URL testURL = new URL("http://mschoch.ic.ht:5984/touch_test");
        TDChangeTracker changeTracker = new TDChangeTracker(testURL, TDChangeTrackerMode.Continuous, 0, null);

        // set filter
        changeTracker.setFilterName("filter");

        // build filter map
        Map<String,Object> filterMap = new HashMap<String,Object>();
        filterMap.put("param", "value");

        // set filter map
        changeTracker.setFilterParams(filterMap);

        Assert.assertEquals("_changes?feed=continuous&heartbeat=300000&since=0&filter=filter&param=value", changeTracker.getChangesFeedPath());

    }

}
