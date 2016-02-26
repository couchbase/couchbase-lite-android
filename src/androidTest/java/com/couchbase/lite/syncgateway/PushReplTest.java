package com.couchbase.lite.syncgateway;


import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.Manager;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Created by hideki on 2/25/16.
 */
public class PushReplTest extends LiteTestCaseWithDB {

    public static final String TAG = "PushReplTest";

    @Override
    protected void setUp() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        super.setUp();
    }

    /**
     * Note: For test, needs to restart sync gateway. Default sync gateway does not allow
     * to access admin port from non-local to delete db.
     */
    public void testPushRepl() throws Exception{
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        final int INIT_DOCS = 100;
        final int TOTAL_DOCS = 400;

        // initial 100 docs
        for (int i = 0; i < INIT_DOCS; i++) {
            Document doc = database.getDocument("doc-" + String.format("%03d", i));
            Map<String, Object> props = new HashMap<>();
            props.put("key", i);
            try {
                doc.putProperties(props);
            } catch (CouchbaseLiteException e) {
                Log.e(TAG, "Failed to create new doc", e);
                fail(e.getMessage());
            }
        }

        final CountDownLatch latch = new CountDownLatch(1);

        // thread creates documents during replication.
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = INIT_DOCS; i < TOTAL_DOCS; i++) {
                    Document doc = database.getDocument("doc-" + String.format("%03d", i));
                    Map<String, Object> props = new HashMap<>();
                    props.put("key", i);
                    try {
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Failed to create new doc", e);
                        fail(e.getMessage());

                    }
                }
                latch.countDown();
            }
        });
        thread.start();

        // start push replicator
        final CountDownLatch idle = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(idle);
        ReplicationFinishedObserver stopObserver = new ReplicationFinishedObserver(stop);
        Replication push = database.createPushReplication(getReplicationURL());
        push.setContinuous(true);
        push.addChangeListener(idleObserver);
        push.addChangeListener(stopObserver);
        push.start();
        // wait till thread finishes to create all docs.
        latch.await();
        // wait till push become idle state
        idle.await();
        // stop push
        push.stop();
        // stop till push become stopped state
        stop.await();

        // give sync gateway to process all.
        Thread.sleep(2*1000);

        // verify total document number on remote
        assertEquals(TOTAL_DOCS, ((Number) getAllDocs().get("total_rows")).intValue());
    }

    // GET /{db}/_all_docs
    Map<String, Object> getAllDocs() throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(System.getProperty("replicationUrl") + "/_all_docs");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream in = connection.getInputStream();
            try {
                return Manager.getObjectMapper().readValue(in, Map.class);
            } finally {
                if (in != null)
                    in.close();
            }
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }
}
