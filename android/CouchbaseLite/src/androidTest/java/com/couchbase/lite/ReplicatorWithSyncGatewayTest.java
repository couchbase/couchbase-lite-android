package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Note: https://github.com/couchbase/couchbase-lite-core/tree/master/Replicator/tests/data
 */
public class ReplicatorWithSyncGatewayTest extends BaseReplicatorTest {
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

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
    public void testEmptyPullFromRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled()) return;

        Endpoint target = getRemoteEndpoint("scratch", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        run(config, 0, null);
    }

    @Test
    public void testAuthenticationFailure() throws Exception {
        if (!config.replicatorTestsEnabled()) return;

        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        run(config, 401, "WebSocket");
    }

    @Test
    public void testAuthenticatedPullWithIncorrectPassword() throws Exception {
        if (!config.replicatorTestsEnabled()) return;

        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setAuthenticator(new BasicAuthenticator("pupshaw", "frank!"));
        run(config, 401, "WebSocket"); // Retry 3 times then fails with 401
    }

    @Test
    public void testAuthenticatedPull() throws Exception {
        if (!config.replicatorTestsEnabled()) return;

        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setAuthenticator(new BasicAuthenticator("pupshaw", "frank"));
        run(config, 0, null);
    }

    @Test
    public void testSessionAuthenticatorPull() throws Exception {
        if (!config.replicatorTestsEnabled()) return;

        // Obtain Sync-Gateway Session ID
        SessionAuthenticator auth = getSessionAuthenticatorFromSG();
        Log.e(TAG, "auth -> " + auth);

        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setAuthenticator(auth);
        run(config, 0, null);
    }

    SessionAuthenticator getSessionAuthenticatorFromSG() throws Exception {
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
                json.getString("cookie_name"));
    }
}
