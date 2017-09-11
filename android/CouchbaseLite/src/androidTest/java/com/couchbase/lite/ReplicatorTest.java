package com.couchbase.lite;

import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.IOUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PULL;
import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH;
import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;
import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrTLSCertUntrusted;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
  * Note: https://github.com/couchbase/couchbase-lite-core/tree/master/Replicator/tests/data
  */
public class ReplicatorTest extends BaseTest {

    Database otherDB;
    Replicator repl;
    long timeout;  // seconds

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull,
                                               boolean continuous) {
        return makeConfig(push, pull, continuous, otherDB);
    }

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull,
                                               boolean continuous, String uri) {
        return makeConfig(push, pull, continuous, URI.create(uri));
    }

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull,
                                               boolean continuous, URI target) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(db, target);
        config.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        config.setContinuous(continuous);
        return config;
    }

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull,
                                               boolean continuous, Database target) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(db, target);
        config.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        config.setContinuous(continuous);
        return config;
    }

    private void run(final ReplicatorConfiguration config, final int code, final String domain)
            throws InterruptedException {
        repl = new Replicator(config);
        final CountDownLatch latch = new CountDownLatch(1);
        repl.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(Replicator replicator, Replicator.Status status, CouchbaseLiteException error) {
                final String kActivityNames[] = {"stopped", "offline", "connecting", "idle", "busy"};
                Log.i(TAG, "---Status: %s (%d / %d), lastError = %s",
                        kActivityNames[status.getActivityLevel().getValue()],
                        status.getProgress().getCompleted(), status.getProgress().getTotal(),
                        error);
                if (config.isContinuous()) {
                    if (status.getActivityLevel() == Replicator.ActivityLevel.IDLE &&
                            status.getProgress().getCompleted() == status.getProgress().getTotal()) {
                        if (code != 0) {
                            assertEquals(code, error.getCode());
                            if (domain != null)
                                assertEquals(domain, error.getDomainString());
                        } else {
                            assertNull(error);
                        }
                        latch.countDown();
                    }
                } else {
                    if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                        if (code != 0) {
                            assertNotNull(error);
                            assertEquals(code, error.getCode());
                            if (domain != null)
                                assertEquals(domain, error.getDomainString());
                        } else {
                            assertNull(error);
                        }
                        latch.countDown();
                    }
                }
            }
        });
        repl.start();
        assertTrue(latch.await(timeout, TimeUnit.SECONDS));
    }

    @Before
    public void setUp() throws Exception {
        config = new Config(
                InstrumentationRegistry.getContext().getAssets().open(Config.TEST_PROPERTIES_FILE));
        if (!config.replicatorTestsEnabled())
            return;

        conflictResolver = new ConflictTest.MergeThenTheirsWins();
        super.setUp();

        timeout = 10; // seconds
        otherDB = open("otherdb");
        assertNotNull(otherDB);
    }

    @After
    public void tearDown() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        if (otherDB != null) {
            otherDB.close();
            otherDB = null;
        }

        super.tearDown();
    }

    @Test
    public void testBadURL() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;

        ReplicatorConfiguration config = makeConfig(false, true, false, "blxp://localhost/db");
        run(config, 15, "LiteCore");
    }

    @Test
    public void testEmptyPush() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;

        ReplicatorConfiguration config = makeConfig(true, false, false);
        run(config, 0, null);
    }

    @Test
    public void testPushDoc() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        Document doc1 = new Document("doc1");
        doc1.setObject("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        Document doc2 = new Document("doc2");
        doc2.setObject("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(true, false, false);
        run(config, 0, null);

        assertEquals(2, otherDB.getCount());
        doc2 = otherDB.getDocument("doc2");
        assertEquals("Cat", doc2.getString("name"));
    }

    //TODO: @Test
    public void testPushDocContinuous() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        Document doc1 = new Document("doc1");
        doc1.setObject("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        Document doc2 = new Document("doc2");
        doc2.setObject("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(true, false, true);
        run(config, 0, null);

        assertEquals(2, otherDB.getCount());
        doc2 = otherDB.getDocument("doc2");
        assertEquals("Cat", doc2.getString("name"));
    }

    @Test
    public void testPullDoc() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        // For https://github.com/couchbase/couchbase-lite-core/issues/156
        Document doc1 = new Document("doc1");
        doc1.setObject("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        Document doc2 = new Document("doc2");
        doc2.setObject("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(false, true, false);
        run(config, 0, null);

        assertEquals(2, db.getCount());
        doc2 = db.getDocument("doc2");
        assertEquals("Cat", doc2.getString("name"));
    }

    //TODO @Test
    public void testPullDocContinuous() throws Exception {
        // For https://github.com/couchbase/couchbase-lite-core/issues/156

        if (!config.replicatorTestsEnabled())
            return;

        Document doc1 = new Document("doc1");
        doc1.setObject("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        Document doc2 = new Document("doc2");
        doc2.setObject("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(false, true, true);
        run(config, 0, null);

        assertEquals(2, db.getCount());
        doc2 = db.getDocument("doc2");
        assertEquals("Cat", doc2.getString("name"));
    }

    @Test
    public void testPullConflict() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        Document doc1 = new Document("doc");
        doc1.setObject("species", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());
        doc1.setObject("name", "Hobbes");
        save(doc1);
        assertEquals(1, db.getCount());

        Document doc2 = new Document("doc");
        doc2.setObject("species", "Tiger");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());
        doc2.setObject("pattern", "striped");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(false, true, false);
        run(config, 0, null);
        assertEquals(1, db.getCount());

        doc1 = db.getDocument("doc");
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("species", "Tiger");
        expectedMap.put("name", "Hobbes");
        expectedMap.put("pattern", "striped");
        assertEquals(expectedMap, doc1.toMap());
    }

    @Test
    public void testEmptyPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/db",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(true, false, false, uri);
        run(config, 0, null);
    }

    @Test
    public void testEmptyPullFromRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/db",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        run(config, 0, null);
    }

    @Test
    public void testPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        loadJSONResource("names_100.json");

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/db",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(true, false, false, uri);
        run(config, 0, null);
    }


    @Test
    public void testAuthenticationFailure() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/seekrit",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        run(config, 401, "WebSocket");
    }

    @Test
    public void testAuthenticatedPullWithIncorrectPassword() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://%s:4994/seekrit",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        config.setAuthenticator(new BasicAuthenticator("pupshaw", "frank!"));
        // Retry 3 times then fails with 401
        run(config, 401, "WebSocket");
    }

    @Test
    public void testAuthenticatedPullHardcoded() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://pupshaw:frank@%s:4984/seekrit",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        run(config, 0, null);
    }

    @Test
    public void testAuthenticatedPull() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://%s:4984/seekrit",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        config.setAuthenticator(new BasicAuthenticator("pupshaw", "frank"));
        run(config, 0, null);
    }

    @Test
    public void testSessionAuthenticatorPull() throws InterruptedException, IOException, JSONException {
        if (!config.replicatorTestsEnabled())
            return;

        // Obtain Sync-Gateway Session ID
        SessionAuthenticator auth = getSessionAuthenticatorFromSG();
        Log.e(TAG, "auth -> " + auth);

        String uri = String.format(Locale.ENGLISH, "blip://%s:4984/seekrit",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        config.setAuthenticator(auth);
        run(config, 0, null);
    }

    SessionAuthenticator getSessionAuthenticatorFromSG() throws IOException, JSONException {
        // Obtain Sync-Gateway Session ID
        final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient();
        String url = String.format(Locale.ENGLISH, "http://%s:4985/seekrit/_session", config.remoteHost());
        RequestBody body = RequestBody.create(JSON, "{\"name\": \"pupshaw\"}");
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        String respBody = response.body().string();
        Log.e(TAG, "json string -> " + respBody);
        JSONObject json = new JSONObject(respBody);
        return new SessionAuthenticator(
                json.getString("session_id"),
                DateUtils.fromJson(json.getString("expires")),
                json.getString("cookie_name"));
    }

    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert.
     */
    @Test
    public void testSelfSignedSSLFailure() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blips://%s:4994/beer",
                this.config.remoteHost());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        run(config, kC4NetErrTLSCertUntrusted, "Network");
    }


    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert equal to the one
     * stored in the test resource SelfSigned.cer. (This is the same cert used in the 1.x unit tests.)
     */
    @Test
    public void testSelfSignedSSLPinned() throws InterruptedException, IOException {
        if (!config.replicatorTestsEnabled())
            return;

        timeout = 180; // seconds

        InputStream is = getAsset("cert.cer");
        byte[] cert = IOUtils.toByteArray(is);
        is.close();

        String uri = String.format(Locale.ENGLISH, "blips://%s:4994/beer",
                this.config.remoteHost());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        config.setPinnedServerCertificate(cert);
        run(config, 0, null);
    }

    // TODO: Fails with https://github.com/couchbase/couchbase-lite-core/issues/149
    // @Test
//    public void testMissingHost() throws InterruptedException {
//        if (!config.replicatorTestsEnabled())
//            return;
//        timeout = 200;
//        String uri = String.format(Locale.ENGLISH, "blip://foo.couchbase.com/db");
//        ReplicatorConfiguration config = makeConfig(false, true, uri);
//        config.setContinuous(true);
//        run(config, kC4NetErrUnknownHost, "Network");
//    }

    /**
     * How to test reaciability.
     * 1. Run sync gateway
     * 2. Disable Wifi with the device
     * 3. Run  testContinuousPush()
     * 4. Confirm if the replicator stops
     * 5. Enable Wifi
     * 6. Confirm if the replicator starts
     * 7. Confirm if sync gateway recevies some messages
     */
    /**
     @Test public void testContinuousPush() throws Exception {
     if (!config.replicatorTestsEnabled())
     return;

     loadJSONResource("names_100.json");

     timeout = 180; // 3min
     String uri = String.format(Locale.ENGLISH, "blip://%s:%d/%s", this.config.remoteHost(), this.config.remotePort(), this.config.remoteDB());
     ReplicatorConfiguration config = makeConfig(true, false, uri);
     config.setContinuous(true);
     run(config, 0, null);
     }
     */
}
