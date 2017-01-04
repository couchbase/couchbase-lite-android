package com.couchbase.lite.syncgateway;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.replicator.Replication;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by hideki on 1/3/17.
 */

public class CouchDBTest extends LiteTestCaseWithDB {

    public static final String TAG = "AutoPruningTest";

    @Override
    protected void setUp() throws Exception {
        if (!couchdbTestsEnabled()) {
            return;
        }
        super.setUp();
    }


    // https://github.com/couchbase/couchbase-lite-java-core/issues/1534
    // Push Replication Fails With createTarget Set
    public void testCreateRemoteDB() throws MalformedURLException, InterruptedException, CouchbaseLiteException {
        if (!couchdbTestsEnabled()) {
            return;
        }

        Document doc = database.createDocument();
        Map<String, Object> dict = new HashMap<String, Object>();
        dict.put("test","1");
        doc.putProperties(dict);

        URL remote = getCouchDBURL();
        URL url = new URL(remote.toString() + "/db4");
        Replication push = database.createPushReplication(url);
        push.setCreateTarget(true);
        push.setContinuous(true);

        final CountDownLatch pushIdle = new CountDownLatch(1);
        push.addChangeListener(new ReplicationIdleObserver(pushIdle));

        push.start();
        assertTrue(pushIdle.await(30, TimeUnit.SECONDS));

        final CountDownLatch pushDone = new CountDownLatch(1);
        push.addChangeListener(new ReplicationFinishedObserver(pushDone));

        push.stop();

        assertTrue(pushDone.await(30, TimeUnit.SECONDS));
    }
}
