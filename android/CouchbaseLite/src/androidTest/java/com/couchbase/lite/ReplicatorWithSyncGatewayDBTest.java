package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReplicatorWithSyncGatewayDBTest extends BaseReplicatorTest {
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String DB_NAME = "db";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        if (config.replicatorTestsEnabled()) {
            remote_PUT_db(DB_NAME);
        }

    }

    @After
    public void tearDown() throws Exception {
        if (config.replicatorTestsEnabled()) {
            remote_DELETE_db(DB_NAME);
        }
        super.tearDown();
    }

    private boolean remote_PUT_db(String db) throws IOException {
        OkHttpClient client = new OkHttpClient();
        String url = String.format(Locale.ENGLISH, "http://%s:4985/%s/", this.config.remoteHost(), db);
        RequestBody body = RequestBody.create(JSON, "{\"server\": \"walrus:\", \"users\": { \"GUEST\": { \"disabled\": false, \"admin_channels\": [\"*\"] } }, \"unsupported\": {\"replicator_2\":true}}");
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .put(body)
                .build();
        Response response = client.newCall(request).execute();
        return response.code() >= 200 && response.code() < 300;
    }

    private boolean remote_DELETE_db(String db) throws IOException {
        OkHttpClient client = new OkHttpClient();
        String url = String.format(Locale.ENGLISH, "http://%s:4985/%s/", this.config.remoteHost(), db);
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .delete()
                .build();
        Response response = client.newCall(request).execute();
        return response.code() >= 200 && response.code() < 300;
    }

    @Test
    public void testEmptyPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        Endpoint target = getRemoteEndpoint(DB_NAME, false);
        ReplicatorConfiguration config = makeConfig(true, false, false, target);
        run(config, 0, null);
    }

    @Test
    public void testPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        // Create 100 docs in local db
        loadJSONResource("names_100.json");

        // target SG URI
        Endpoint target = getRemoteEndpoint(DB_NAME, false);

        // Push replicate from db to SG
        ReplicatorConfiguration config = makeConfig(true, false, false, target);
        run(config, 0, null);

        // Pull replicate from SG to otherDB.
        config = makeConfig(false, true, false, this.otherDB, target);
        run(config, 0, null);
        assertEquals(100, this.otherDB.getCount());
    }

    /**
     * How to test reaciability.
     * 1. Run sync gateway
     * 2. Disable Wifi with the device
     * 3. Run  testContinuousPush()
     * 4. Confirm if the replicator stops
     * 5. Enable Wifi
     * 6. Confirm if the replicator starts
     * 7. Confirm if sync gateway receives some messages
     */
    @Test
    public void testContinuousPush() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        loadJSONResource("names_100.json");

        timeout = 180; // 3min
        Endpoint target = getRemoteEndpoint(DB_NAME, false);
        ReplicatorConfiguration config = makeConfig(true, false, true, target);
        run(config, 0, null);
    }

    @Test
    public void testChannelPull() throws CouchbaseLiteException, InterruptedException, URISyntaxException {
        if (!config.replicatorTestsEnabled())
            return;

        assertEquals(0, otherDB.getCount());
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    String docID = String.format(Locale.ENGLISH, "doc-%d", i);
                    MutableDocument doc = new MutableDocument(docID);
                    doc.setValue("foo", "var");
                    try {
                        db.save(doc);
                    } catch (CouchbaseLiteException e) {
                        fail();
                    }
                }
                for (int i = 0; i < 10; i++) {
                    String docID = String.format(Locale.ENGLISH, "doc-%d", i + 5);
                    MutableDocument doc = new MutableDocument(docID);
                    doc.setValue("channels", "my_channel");
                    try {
                        db.save(doc);
                    } catch (CouchbaseLiteException e) {
                        fail();
                    }
                }
            }
        });

        Endpoint target = getRemoteEndpoint(DB_NAME, false);
        ReplicatorConfiguration config = makeConfig(true, false, false, target);
        run(config, 0, null);

        config = makeConfig(false, true, false, otherDB, target);
        config.setChannels(Arrays.asList("my_channel"));
        run(config, 0, null);
        assertEquals(10, otherDB.getCount());
    }

    /**
     * Push and Pull replication against Sync Gateway with Document which has attachment.
     * https://github.com/couchbase/couchbase-lite-core/issues/354
     */
    @Test
    public void testPushToRemoteDBWithAttachment() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        // store doc with attachment into db.
        {
            // 2.39MB image -> causes `Compression buffer overflow`
            //InputStream is = getAsset("image.jpg");
            // 507KB image -> works fine.
            InputStream is = getAsset("attachment.png");
            try {
                Blob blob = new Blob("image/jpg", is);
                MutableDocument doc1 = new MutableDocument("doc1");
                doc1.setValue("name", "Tiger");
                doc1.setBlob("image.jpg", blob);
                save(doc1);
            } finally {
                is.close();
            }
            assertEquals(1, db.getCount());
        }

        // target SG URI
        Endpoint target = getRemoteEndpoint(DB_NAME, false);

        // Push replicate from db to SG
        ReplicatorConfiguration config = makeConfig(true, false, false, target);
        run(config, 0, null);

        // Pull replicate from SG to otherDB.
        config = makeConfig(false, true, false, this.otherDB, target);
        run(config, 0, null);
        assertEquals(1, this.otherDB.getCount());
        Document doc = otherDB.getDocument("doc1");
        assertNotNull(doc);
        Blob blob = doc.getBlob("image.jpg");
        assertNotNull(blob);
    }

    // DO NOT RUN
    //@Test
    public void testContinuousPushNeverending() throws URISyntaxException, InterruptedException {
        // NOTE: This test never stops even after the replication goes idle.
        // It can be used to test the response to connectivity issues like killing the remote server.

        // target SG URI
        Endpoint target = getRemoteEndpoint(DB_NAME, false);

        // Push replicate from db to SG
        ReplicatorConfiguration config = makeConfig(true, false, true, target);
        final Replicator repl = run(config, 0, null);
        repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Log.w(TAG, "changed() change -> " + change);
            }
        });

        try {
            Thread.sleep(3 * 60 * 1000);
        } catch (Exception e) {
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1545
    @Test
    public void testPushDocAndDocChangeListener() throws CouchbaseLiteException, URISyntaxException, InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;

        String docID = "doc1";

        // 1. save new Document
        MutableDocument mDoc = new MutableDocument(docID);
        Document doc = db.save(mDoc);

        // 2. Set document change listner
        final CountDownLatch latch1 = new CountDownLatch(1);
        DocumentChangeListener listener1 = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                assertNotNull(change);
                latch1.countDown();
            }
        };
        ListenerToken token1 = db.addDocumentChangeListener(docID, listener1);

        // 3. Setup Push&Pull continuous replicator
        timeout = 180; // 3min
        Endpoint target = getRemoteEndpoint(DB_NAME, false);
        ReplicatorConfiguration config = makeConfig(true, true, true, target);
        Replicator repl = new Replicator(config);

        // 4. Set replicator change listener to detect replicator IDLE state.
        final CountDownLatch latch2 = new CountDownLatch(1); // for before update doc
        final CountDownLatch latch3 = new CountDownLatch(2); // for after update doc
        ReplicatorChangeListener listener2 = new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                CouchbaseLiteException error = status.getError();
                if (status.getActivityLevel() == Replicator.ActivityLevel.IDLE) {
                    latch2.countDown();
                    latch3.countDown();
                }
            }
        };
        ListenerToken token2 = repl.addChangeListener(listener2);

        // 5. Start Replicator
        repl.start();

        // 6. Wait replicator becomes IDLE state
        assertTrue(latch2.await(10, TimeUnit.SECONDS));

        // 7. Update document
        mDoc = doc.toMutable();
        mDoc.setString("hello", "world");
        doc = db.save(mDoc);

        // 8. Wait replicator becomes IDLE state
        assertTrue(latch3.await(10, TimeUnit.SECONDS));

        // 9. Stop replicator
        repl.removeChangeListener(token2);
        stopContinuousReplicator(repl);
        db.removeChangeListener(token1);

        // 10. Pull replicate from SG to otherDB. And verify the document
        config = makeConfig(false, true, false, this.otherDB, target);
        run(config, 0, null);
        assertEquals(1, this.otherDB.getCount());
        doc = otherDB.getDocument(docID);
        assertNotNull(doc);
        assertEquals("world", doc.getString("hello"));
    }
}
