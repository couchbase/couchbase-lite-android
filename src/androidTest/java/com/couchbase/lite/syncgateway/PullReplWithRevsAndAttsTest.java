package com.couchbase.lite.syncgateway;

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.SavedRevision;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Created by hideki on 5/5/15.
 * <p/>
 * Test requires sync_gateway
 */
public class PullReplWithRevsAndAttsTest extends LiteTestCaseWithDB {

    public static final String TAG = "PullReplWithRevsAndAttsTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!syncgatewayTestsEnabled()) {
            return;
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/579
     * Sync fails to complete initial sync when in continuous mode
     * <p/>
     * NOTE: This test requires sync_gateway to restart
     */
    public void testPullReplWithRevsAndAtts() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        URL remote = getReplicationURL();

        Database pushDB = manager.getDatabase("prepopdb");
        Document doc = pushDB.getDocument("mydoc");
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("foo", "bar");
        SavedRevision rev1 = doc.putProperties(props);

        StringBuffer sb = new StringBuffer();
        int size = 50 * 1024;
        for (int i = 0; i < size; i++) {
            sb.append("a");
        }
        ByteArrayInputStream body = new ByteArrayInputStream(sb.toString().getBytes());
        UnsavedRevision newRev = doc.createRevision();
        newRev.setAttachment("myattachment", "text/plain; charset=utf-8", body);
        SavedRevision rev2 = newRev.save();

        Replication pusher = pushDB.createPushReplication(remote);
        final CountDownLatch latch1 = new CountDownLatch(1);
        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "push 1:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch1.countDown();
                }
            }
        });
        runReplication(pusher);
        assertEquals(0, latch1.getCount());

        Replication puller = database.createPullReplication(remote);
        final CountDownLatch latch2 = new CountDownLatch(1);
        puller.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "pull 1:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch2.countDown();
                }
            }
        });
        runReplication(puller);
        assertEquals(0, latch2.getCount());

        props = new HashMap<String, Object>(doc.getUserProperties());
        props.put("tag", 3);

        newRev = rev2.createRevision();
        newRev.setUserProperties(props);
        SavedRevision rev3 = newRev.save();

        pusher = pushDB.createPushReplication(remote);
        final CountDownLatch latch3 = new CountDownLatch(1);
        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "push 2:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch3.countDown();
                }
            }
        });
        runReplication(pusher);
        assertEquals(0, latch3.getCount());

        props = new HashMap<String, Object>(doc.getUserProperties());
        props.put("tag", 4);

        newRev = rev3.createRevision();
        newRev.setUserProperties(props);
        SavedRevision rev4 = newRev.save();

        pusher = pushDB.createPushReplication(remote);
        final CountDownLatch latch4 = new CountDownLatch(1);
        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "push 3:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch4.countDown();
                }
            }
        });
        runReplication(pusher);
        assertEquals(0, latch4.getCount());

        puller = database.createPullReplication(remote);
        final CountDownLatch latch5 = new CountDownLatch(1);
        puller.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "pull 2:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch5.countDown();
                }
            }
        });
        runReplication(puller);
        assertEquals(0, latch5.getCount());
    }

}
