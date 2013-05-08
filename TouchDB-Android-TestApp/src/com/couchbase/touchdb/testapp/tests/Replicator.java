package com.couchbase.touchdb.testapp.tests;

import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.replicator.TDPusher;
import com.couchbase.touchdb.replicator.TDReplicator;

public class Replicator extends TouchDBTestCase {

    public static final String TAG = "Replicator";

    public void testPusher() throws Throwable {

        URL remote = getReplicationURL();

        deleteRemoteDB(remote);

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        TDRevision rev2 = database.putRevision(new TDRevision(documentProperties), rev1.getRevId(), false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc2");
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new TDRevision(documentProperties), null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        final TDReplicator repl = database.getReplicator(remote, true, false, server.getWorkExecutor());
        ((TDPusher)repl).setCreateTarget(true);
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }
        });

        while(repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals("3", repl.getLastSequence());
    }

    public void testPuller() throws Throwable {

        //force a push first, to ensure that we have data to pull
        testPusher();

        URL remote = getReplicationURL();

        final TDReplicator repl = database.getReplicator(remote, false, false, server.getWorkExecutor());
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Pull them from the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }
        });

        while(repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        String lastSequence = repl.getLastSequence();
        Assert.assertTrue("2".equals(lastSequence) || "3".equals(lastSequence));
        Assert.assertEquals(2, database.getDocumentCount());


        //wait for a short time here
        //we want to ensure that the previous replicator has really finished
        //writing its local state to the server
        Thread.sleep(2*1000);

        final TDReplicator repl2 = database.getReplicator(remote, false, false, server.getWorkExecutor());
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Pull them from the remote:
                repl2.start();
                Assert.assertTrue(repl2.isRunning());
            }
        });

        while(repl2.isRunning()) {
            Log.i(TAG, "Waiting for replicator2 to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals(3, database.getLastSequence());

        TDRevision doc = database.getDocumentWithIDAndRev("doc1", null, EnumSet.noneOf(TDDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("2-"));
        Assert.assertEquals(1, doc.getProperties().get("foo"));

        doc = database.getDocumentWithIDAndRev("doc2", null, EnumSet.noneOf(TDDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("1-"));
        Assert.assertEquals(true, doc.getProperties().get("fnord"));

    }

}
