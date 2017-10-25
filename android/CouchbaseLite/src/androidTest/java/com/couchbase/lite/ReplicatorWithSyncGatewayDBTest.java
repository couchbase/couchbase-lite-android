package com.couchbase.lite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;

public class ReplicatorWithSyncGatewayDBTest extends BaseReplicatorTest {
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String DB_NAME = "db";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        remote_PUT_db(DB_NAME);
    }

    @After
    public void tearDown() throws Exception {
        remote_DELETE_db(DB_NAME);
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
        if (!config.replicatorTestsEnabled()) return;

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/%s",
                this.config.remoteHost(), this.config.remotePort(), DB_NAME);
        ReplicatorConfiguration config = makeConfig(true, false, false, uri);
        run(config, 0, null);
    }

    @Test
    public void testPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled()) return;

        // Create 100 docs in local db
        loadJSONResource("names_100.json");

        // target SG URI
        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/%s",
                this.config.remoteHost(), this.config.remotePort(), DB_NAME);

        // Push replicate from db to SG
        ReplicatorConfiguration config = makeConfig(true, false, false, uri);
        run(config, 0, null);

        // Pull replicate from SG to otherDB.
        config = makeConfig(false, true, false, this.otherDB, uri);
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
        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/%s", this.config.remoteHost(), this.config.remotePort(), DB_NAME);
        ReplicatorConfiguration config = makeConfig(true, false, true, uri);
        run(config, 0, null);
    }
}
