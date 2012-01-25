package com.couchbase.touchdb.testapp.tests;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.replicator.TDPusher;
import com.couchbase.touchdb.replicator.TDReplicator;

public class Replicator extends InstrumentationTestCase {

    public static final String TAG = "Replicator";
    public static final String REMOTE_DB_URL_STR = "http://192.168.1.6:5984/tdreplicator_test";

    private static void deleteRemoteDB() {
        try {
            Log.v(TAG, String.format("Deleting %s", REMOTE_DB_URL_STR));
            URL url = new URL(REMOTE_DB_URL_STR);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.connect();
            int responseCode = conn.getResponseCode();
            Assert.assertTrue(responseCode < 300 || responseCode == 404);
        } catch (Exception e) {
            Log.e(TAG, "Exceptiong deleting remote db", e);
        }
    }

    public void testPusher() throws Throwable {

        String filesDir = getInstrumentation().getContext().getFilesDir().getAbsolutePath();

        TDServer server = null;
        try {
            server = new TDServer(filesDir);
        } catch (IOException e) {
            fail("Creating server caused IOException");
        }

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.touchdb
        TDDatabase old = server.getExistingDatabaseNamed("db");
        if(old != null) {
            old.deleteDatabase();
        }

        final TDDatabase db = server.getDatabaseNamed("db");
        db.open();

        deleteRemoteDB();

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = db.putRevision(rev1, null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        TDRevision rev2 = db.putRevision(new TDRevision(documentProperties), rev1.getRevId(), false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc2");
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        db.putRevision(new TDRevision(documentProperties), null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());




        URL remote = new URL(REMOTE_DB_URL_STR);
        final TDReplicator repl = db.getReplicator(remote, true, false);
        ((TDPusher)repl).setCreateTarget(true);
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }
        });

        Thread.sleep(30*1000);
        Assert.assertEquals("3", repl.getLastSequence());

        db.close();
        server.deleteDatabaseNamed("db");
        server.close();
    }

    public void pusherInsideThread() {

    }

}
