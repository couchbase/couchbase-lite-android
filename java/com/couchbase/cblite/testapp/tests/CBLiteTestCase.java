package com.couchbase.cblite.testapp.tests;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.Assert;

import org.codehaus.jackson.map.ObjectMapper;

import android.test.InstrumentationTestCase;
import android.util.Base64;
import android.util.Log;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.router.CBLRouter;
import com.couchbase.cblite.router.CBLURLConnection;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;
import com.couchbase.cblite.support.FileDirUtils;

public abstract class CBLiteTestCase extends InstrumentationTestCase {

    public static final String TAG = "CBLiteTestCase";

    private static boolean initializedUrlHandler = false;

    protected ObjectMapper mapper = new ObjectMapper();

    protected CBLServer server = null;
    protected CBLDatabase database = null;
    protected String DEFAULT_TEST_DB = "cblite-test";

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "setUp");
        super.setUp();

        //for some reason a traditional static initializer causes junit to die
        if(!initializedUrlHandler) {
            CBLURLStreamHandlerFactory.registerSelfIgnoreError();
            initializedUrlHandler = true;
        }

        loadCustomProperties();
        startCBLite();
        startDatabase();
    }

    protected String getServerPath() {
        String filesDir = getInstrumentation().getContext().getFilesDir().getAbsolutePath();
        return filesDir;
    }

    protected void startCBLite() {
        try {
            String serverPath = getServerPath();
            File serverPathFile = new File(serverPath);
            FileDirUtils.deleteRecursive(serverPathFile);
            serverPathFile.mkdir();
            server = new CBLServer(getServerPath());
        } catch (IOException e) {
            fail("Creating server caused IOException");
        }
    }

    protected void stopCBLite() {
        if(server != null) {
            server.close();
        }
    }

    protected void startDatabase() {
        database = ensureEmptyDatabase(DEFAULT_TEST_DB);
        boolean status = database.open();
        Assert.assertTrue(status);
    }

    protected void stopDatabse() {
        if(database != null) {
            database.close();
        }
    }

    protected CBLDatabase ensureEmptyDatabase(String dbName) {
        CBLDatabase db = server.getExistingDatabaseNamed(dbName);
        if(db != null) {
            boolean status = db.deleteDatabase();
            Assert.assertTrue(status);
        }
        db = server.getDatabaseNamed(dbName, true);
        return db;
    }

    protected void loadCustomProperties() throws IOException {

        Properties systemProperties = System.getProperties();
        InputStream mainProperties = getInstrumentation().getContext().getAssets().open("test.properties");
        if(mainProperties != null) {
            systemProperties.load(mainProperties);
        }
        try {
            InputStream localProperties = getInstrumentation().getContext().getAssets().open("local-test.properties");
            if(localProperties != null) {
                systemProperties.load(localProperties);
            }
        } catch (IOException e) {
            Log.w(TAG, "Error trying to read from local-test.properties, does this file exist?");
        }
    }

    protected String getReplicationProtocol() {
        return System.getProperty("replicationProtocol");
    }

    protected String getReplicationServer() {
        return System.getProperty("replicationServer");
    }

    protected int getReplicationPort() {
        return Integer.parseInt(System.getProperty("replicationPort"));
    }

    protected String getReplicationAdminUser() {
        return System.getProperty("replicationAdminUser");
    }

    protected String getReplicationAdminPassword() {
        return System.getProperty("replicationAdminPassword");
    }

    protected String getReplicationDatabase() {
        return System.getProperty("replicationDatabase");
    }

    protected URL getReplicationURL() throws MalformedURLException {
        if(getReplicationAdminUser() != null) {
            return new URL(String.format("%s://%s:%s@%s:%d/%s", getReplicationProtocol(), getReplicationAdminUser(), getReplicationAdminPassword(), getReplicationServer(), getReplicationPort(), getReplicationDatabase()));
        } else {
            return new URL(String.format("%s://%s:%d/%s", getReplicationProtocol(), getReplicationServer(), getReplicationPort(), getReplicationDatabase()));
        }
    }

    protected URL getReplicationURLWithoutCredentials() throws MalformedURLException {
        return new URL(String.format("%s://%s:%d/%s", getReplicationProtocol(), getReplicationServer(), getReplicationPort(), getReplicationDatabase()));
    }

    @Override
    protected void tearDown() throws Exception {
        Log.v(TAG, "tearDown");
        super.tearDown();
        stopDatabse();
        stopCBLite();
    }

    protected Map<String,Object> userProperties(Map<String,Object> properties) {
        Map<String,Object> result = new HashMap<String,Object>();

        for (String key : properties.keySet()) {
            if(!key.startsWith("_")) {
                result.put(key, properties.get(key));
            }
        }

        return result;
    }

    protected  void deleteRemoteDB(URL url) {
        try {
            Log.v(TAG, String.format("Deleting %s", url.toExternalForm()));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String userInfo = url.getUserInfo();
            if(userInfo != null) {
                byte[] authEncBytes = Base64.encode(userInfo.getBytes(), Base64.DEFAULT);

                conn.setRequestProperty("Authorization", "Basic " + new String(authEncBytes));
            }

            conn.setRequestMethod("DELETE");
            conn.connect();
            int responseCode = conn.getResponseCode();
            Assert.assertTrue(responseCode < 300 || responseCode == 404);
        } catch (Exception e) {
            Log.e(TAG, "Exceptiong deleting remote db", e);
        }
    }

    protected CBLURLConnection sendRequest(CBLServer server, String method, String path, Map<String,String> headers, Object bodyObj) {
        try {
            URL url = new URL("cblite://" + path);
            CBLURLConnection conn = (CBLURLConnection)url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(method);
            if(headers != null) {
                for (String header : headers.keySet()) {
                    conn.setRequestProperty(header, headers.get(header));
                }
            }
            Map<String, List<String>> allProperties = conn.getRequestProperties();
            if(bodyObj != null) {
                conn.setDoInput(true);
                ByteArrayInputStream bais = new ByteArrayInputStream(mapper.writeValueAsBytes(bodyObj));
                conn.setRequestInputStream(bais);
            }

            CBLRouter router = new CBLRouter(server, conn);
            router.start();
            return conn;
        } catch (MalformedURLException e) {
            fail();
        } catch(IOException e) {
            fail();
        }
        return null;
    }

    protected Object parseJSONResponse(CBLURLConnection conn) {
        Object result = null;
        CBLBody responseBody = conn.getResponseBody();
        if(responseBody != null) {
            byte[] json = responseBody.getJson();
            String jsonString = null;
            if(json != null) {
                jsonString = new String(json);
                try {
                    result = mapper.readValue(jsonString, Object.class);
                } catch (Exception e) {
                    fail();
                }
            }
        }
        return result;
    }

    protected Object sendBody(CBLServer server, String method, String path, Object bodyObj, int expectedStatus, Object expectedResult) {
        CBLURLConnection conn = sendRequest(server, method, path, null, bodyObj);
        Object result = parseJSONResponse(conn);
        Log.v(TAG, String.format("%s %s --> %d", method, path, conn.getResponseCode()));
        Assert.assertEquals(expectedStatus, conn.getResponseCode());
        if(expectedResult != null) {
            Assert.assertEquals(expectedResult, result);
        }
        return result;
    }

    protected Object send(CBLServer server, String method, String path, int expectedStatus, Object expectedResult) {
        return sendBody(server, method, path, null, expectedStatus, expectedResult);
    }

}
