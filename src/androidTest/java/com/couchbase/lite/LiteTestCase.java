package com.couchbase.lite;

import com.couchbase.lite.listener.LiteListener;
import com.couchbase.test.lite.*;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.router.*;
import com.couchbase.lite.router.Router;
import com.couchbase.lite.storage.Cursor;
import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public abstract class LiteTestCase extends LiteTestCaseBase {

    public static final String TAG = "LiteTestCase";

    private static boolean initializedUrlHandler = false;

    protected static LiteListener testListener = null;

    protected ObjectMapper mapper = new ObjectMapper();

    protected Manager manager = null;
    protected Database database = null;
    protected String DEFAULT_TEST_DB = "cblite-test";

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "setUp");
        super.setUp();

        //for some reason a traditional static initializer causes junit to die
        if(!initializedUrlHandler) {
            URLStreamHandlerFactory.registerSelfIgnoreError();
            initializedUrlHandler = true;
        }

        loadCustomProperties();
        startCBLite();
        startDatabase();
        if (Boolean.parseBoolean(System.getProperty("LiteListener"))) {
            startListener();
        }
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    protected void startCBLite() throws IOException {
        LiteTestContext context = new LiteTestContext();
        Manager.enableLogging(Log.TAG, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_SYNC, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_QUERY, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_VIEW, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_CHANGE_TRACKER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_BLOB_STORE, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_DATABASE, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_LISTENER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_MULTI_STREAM_WRITER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_REMOTE_REQUEST, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_ROUTER, Log.VERBOSE);
        manager = new Manager(context, Manager.DEFAULT_OPTIONS);
    }

    protected void stopCBLite() {
        if(manager != null) {
            manager.close();
        }
    }

    protected void startListener() throws IOException, CouchbaseLiteException {
        // In theory we only set up the listener once across all tests because this mimics the behavior
        // of the sync gateway which was the original server these tests are run against which has a single
        // instance used all the time. But the other reason we only start the listener once is that
        // there is a bug in TJWS (https://github.com/couchbase/couchbase-lite-java-listener/issues/43) that
        // keeps the listener from stopping even when you tell it to stop.
        if (testListener == null) {
            LiteTestContext context = new LiteTestContext("testlistener");
            Manager listenerManager = new Manager(context, Manager.DEFAULT_OPTIONS);
            listenerManager.getDatabase(getReplicationDatabase());
            testListener = new LiteListener(listenerManager, getReplicationPort(), null);
            testListener.start();
        }
    }

    protected Database startDatabase() throws CouchbaseLiteException {
        database = ensureEmptyDatabase(DEFAULT_TEST_DB);
        return database;
    }

    protected void stopDatabase() {
        if(database != null) {
            database.close();
        }
    }

    protected Database ensureEmptyDatabase(String dbName) throws CouchbaseLiteException {
        Database db = manager.getExistingDatabase(dbName);
        if(db != null) {
            db.delete();
        }
        db = manager.getDatabase(dbName);
        return db;
    }

    protected void loadCustomProperties() throws IOException {

        Properties systemProperties = System.getProperties();
        InputStream mainProperties = getAsset("test.properties");
        if(mainProperties != null) {
            systemProperties.load(mainProperties);
        }
        try {
            InputStream localProperties = getAsset("local-test.properties");
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

    protected URL getReplicationURL()  {
        try {
            if(getReplicationAdminUser() != null && getReplicationAdminUser().trim().length() > 0) {
                return new URL(String.format("%s://%s:%s@%s:%d/%s", getReplicationProtocol(), getReplicationAdminUser(), getReplicationAdminPassword(), getReplicationServer(), getReplicationPort(), getReplicationDatabase()));
            } else {
                return new URL(String.format("%s://%s:%d/%s", getReplicationProtocol(), getReplicationServer(), getReplicationPort(), getReplicationDatabase()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected boolean isTestingAgainstSyncGateway() {
        return getReplicationPort() == 4984;
    }

    protected URL getReplicationURLWithoutCredentials() throws MalformedURLException {
        return new URL(String.format("%s://%s:%d/%s", getReplicationProtocol(), getReplicationServer(), getReplicationPort(), getReplicationDatabase()));
    }

    @Override
    protected void tearDown() throws Exception {
        Log.v(TAG, "tearDown");
        super.tearDown();
        stopDatabase();
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

    public Map<String, Object> getReplicationAuthParsedJson() throws IOException {
        String authJson = "{\n" +
                "    \"facebook\" : {\n" +
                "        \"email\" : \"jchris@couchbase.com\"\n" +
                "     }\n" +
                "   }\n";
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> authProperties  = mapper.readValue(authJson,
                new TypeReference<HashMap<String,Object>>(){});
        return authProperties;

    }

    public Map<String, Object> getPushReplicationParsedJson() throws IOException {

        Map<String,Object> authProperties = getReplicationAuthParsedJson();

        Map<String,Object> targetProperties = new HashMap<String,Object>();
        targetProperties.put("url", getReplicationURL().toExternalForm());
        targetProperties.put("auth", authProperties);

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", targetProperties);
        return properties;
    }

    public Map<String, Object> getPullReplicationParsedJson() throws IOException {

        Map<String,Object> authProperties = getReplicationAuthParsedJson();

        Map<String,Object> sourceProperties = new HashMap<String,Object>();
        sourceProperties.put("url", getReplicationURL().toExternalForm());
        sourceProperties.put("auth", authProperties);

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", sourceProperties);
        properties.put("target", DEFAULT_TEST_DB);
        return properties;
    }


    protected URLConnection sendRequest(String method, String path, Map<String, String> headers, Object bodyObj) {
        try {
            URL url = new URL("cblite://" + path);
            URLConnection conn = (URLConnection)url.openConnection();
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

            Router router = new com.couchbase.lite.router.Router(manager, conn);
            router.start();
            return conn;
        } catch (MalformedURLException e) {
            fail();
        } catch(IOException e) {
            fail();
        }
        return null;
    }

    protected Object parseJSONResponse(URLConnection conn) {
        Object result = null;
        Body responseBody = conn.getResponseBody();
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

    protected Object sendBody(String method, String path, Object bodyObj, int expectedStatus, Object expectedResult) {
        URLConnection conn = sendRequest(method, path, null, bodyObj);
        Object result = parseJSONResponse(conn);
        Log.v(TAG, String.format("%s %s --> %d", method, path, conn.getResponseCode()));
        Assert.assertEquals(expectedStatus, conn.getResponseCode());
        if(expectedResult != null) {
            Assert.assertEquals(expectedResult, result);
        }
        return result;
    }

    protected Object send(String method, String path, int expectedStatus, Object expectedResult) {
        return sendBody(method, path, null, expectedStatus, expectedResult);
    }

    public static void createDocuments(final Database db, final int n) {
        //TODO should be changed to use db.runInTransaction
        for (int i=0; i<n; i++) {
            Map<String,Object> properties = new HashMap<String,Object>();
            properties.put("testName", "testDatabase");
            properties.put("sequence", i);
            createDocumentWithProperties(db, properties);
        }
    };

    static Future createDocumentsAsync(final Database db, final int n) {
        return db.runAsync(new AsyncTask() {
            @Override
            public void run(Database database) {
                db.beginTransaction();
                createDocuments(db, n);
                db.endTransaction(true);
            }
        });

    };


    public static Document createDocumentWithProperties(Database db, Map<String,Object>  properties) {
        Document  doc = db.createDocument();
        Assert.assertNotNull(doc);
        Assert.assertNull(doc.getCurrentRevisionId());
        Assert.assertNull(doc.getCurrentRevision());
        Assert.assertNotNull("Document has no ID", doc.getId()); // 'untitled' docs are no longer untitled (8/10/12)
        try{
            doc.putProperties(properties);
        } catch( Exception e){
            Log.e(TAG, "Error creating document", e);
            assertTrue("can't create new document in db:" + db.getName() + " with properties:" + properties.toString(), false);
        }
        Assert.assertNotNull(doc.getId());
        Assert.assertNotNull(doc.getCurrentRevisionId());
        Assert.assertNotNull(doc.getUserProperties());

        // should be same doc instance, since there should only ever be a single Document instance for a given document
        Assert.assertEquals(db.getDocument(doc.getId()), doc);

        Assert.assertEquals(db.getDocument(doc.getId()).getId(), doc.getId());

        return doc;
    }

    public static Document createDocWithAttachment(Database database, String attachmentName, String content) throws Exception {

        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");

        Document doc = createDocumentWithProperties(database, properties);
        SavedRevision rev = doc.getCurrentRevision();

        assertEquals(rev.getAttachments().size(), 0);
        assertEquals(rev.getAttachmentNames().size(), 0);
        assertNull(rev.getAttachment(attachmentName));

        ByteArrayInputStream body = new ByteArrayInputStream(content.getBytes());

        UnsavedRevision rev2 = doc.createRevision();
        rev2.setAttachment(attachmentName, "text/plain; charset=utf-8", body);

        SavedRevision rev3 = rev2.save();
        assertNotNull(rev3);
        assertEquals(rev3.getAttachments().size(), 1);
        assertEquals(rev3.getAttachmentNames().size(), 1);

        Attachment attach = rev3.getAttachment(attachmentName);
        assertNotNull(attach);
        assertEquals(doc, attach.getDocument());
        assertEquals(attachmentName, attach.getName());
        List<String> attNames = new ArrayList<String>();
        attNames.add(attachmentName);
        assertEquals(rev3.getAttachmentNames(), attNames);

        assertEquals("text/plain; charset=utf-8", attach.getContentType());
        InputStream attachInputStream = attach.getContent();
        assertEquals(IOUtils.toString(attachInputStream, "UTF-8"), content);
        attachInputStream.close();
        assertEquals(content.getBytes().length, attach.getLength());

        return doc;
    }


    public void stopReplication(Replication replication) throws Exception {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationStoppedObserver replicationStoppedObserver = new ReplicationStoppedObserver(replicationDoneSignal);
        replication.addChangeListener(replicationStoppedObserver);

        replication.stop();

        boolean success = replicationDoneSignal.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        // give a little padding to give it a chance to save a checkpoint
        Thread.sleep(2 * 1000);

    }

    public void runReplication(Replication replication) {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);


        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        replication.addChangeListener(replicationFinishedObserver);

        replication.start();

        CountDownLatch replicationDoneSignalPolling = replicationWatcherThread(replication);

        Log.d(TAG, "Waiting for replicator to finish");
        try {
            boolean success = replicationDoneSignal.await(120, TimeUnit.SECONDS);
            assertTrue(success);

            success = replicationDoneSignalPolling.await(120, TimeUnit.SECONDS);
            assertTrue(success);

            Log.d(TAG, "replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        replication.removeChangeListener(replicationFinishedObserver);



    }

    public CountDownLatch replicationWatcherThread(final Replication replication) {

        final CountDownLatch doneSignal = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean started = false;
                boolean done = false;
                while (!done) {

                    if (replication.isRunning()) {
                        started = true;
                    }
                    final boolean statusIsDone = (replication.getStatus() == Replication.ReplicationStatus.REPLICATION_STOPPED ||
                            replication.getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE);
                    if (started && statusIsDone) {
                        done = true;
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

                doneSignal.countDown();

            }
        }).start();
        return doneSignal;

    }

    public static class ReplicationFinishedObserver implements Replication.ChangeListener {

        public boolean replicationFinished = false;
        private CountDownLatch doneSignal;

        public ReplicationFinishedObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Replication replicator = event.getSource();
            Log.d(TAG, replicator + " changed.  " + replicator.getCompletedChangesCount() + " / " + replicator.getChangesCount());

            if (replicator.getCompletedChangesCount() < 0) {
                String msg = String.format("%s: replicator.getCompletedChangesCount() < 0", replicator);
                Log.d(TAG, msg);
                throw new RuntimeException(msg);
            }

            if (replicator.getChangesCount() < 0) {
                String msg = String.format("%s: replicator.getChangesCount() < 0", replicator);
                Log.d(TAG, msg);
                throw new RuntimeException(msg);
            }

            // see https://github.com/couchbase/couchbase-lite-java-core/issues/100
            if (replicator.getCompletedChangesCount() > replicator.getChangesCount()) {
                String msg = String.format("replicator.getCompletedChangesCount() - %d > replicator.getChangesCount() - %d", replicator.getCompletedChangesCount(), replicator.getChangesCount());
                Log.d(TAG, msg);
                throw new RuntimeException(msg);
            }

            if (!replicator.isRunning()) {
                replicationFinished = true;
                String msg = String.format("ReplicationFinishedObserver.changed called, set replicationFinished to: %b", replicationFinished);
                Log.d(TAG, msg);
                doneSignal.countDown();
            }
            else {
                String msg = String.format("ReplicationFinishedObserver.changed called, but replicator still running, so ignore it");
                Log.d(TAG, msg);
            }
        }

        boolean isReplicationFinished() {
            return replicationFinished;
        }

    }

    public static class ReplicationRunningObserver implements Replication.ChangeListener {

        private CountDownLatch doneSignal;

        public ReplicationRunningObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Replication replicator = event.getSource();
            if (replicator.isRunning()) {
                doneSignal.countDown();
            }
        }

    }

    public static class ReplicationIdleObserver implements Replication.ChangeListener {

        private CountDownLatch doneSignal;

        public ReplicationIdleObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Replication replicator = event.getSource();
            if (replicator.getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE) {
                doneSignal.countDown();
            }
        }

    }

    public static class ReplicationStoppedObserver implements Replication.ChangeListener {

        private CountDownLatch doneSignal;

        public ReplicationStoppedObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Replication replicator = event.getSource();
            if (replicator.getStatus() == Replication.ReplicationStatus.REPLICATION_STOPPED) {
                doneSignal.countDown();
            }
        }

    }


    public static class ReplicationErrorObserver implements Replication.ChangeListener {

        private CountDownLatch doneSignal;

        public ReplicationErrorObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Replication replicator = event.getSource();
            if (replicator.getLastError() != null) {
                doneSignal.countDown();
            }
        }

    }

    public void dumpTableMaps() throws Exception {
        Cursor cursor = database.getDatabase().rawQuery(
                "SELECT * FROM maps", null);
        while (cursor.moveToNext()) {
            int viewId = cursor.getInt(0);
            int sequence = cursor.getInt(1);
            byte[] key = cursor.getBlob(2);
            String keyStr = null;
            if (key != null) {
                keyStr = new String(key);
            }
            byte[] value = cursor.getBlob(3);
            String valueStr = null;
            if (value != null) {
                valueStr = new String(value);
            }
            Log.d(TAG, String.format("Maps row viewId: %s seq: %s, key: %s, val: %s",
                    viewId, sequence, keyStr, valueStr));
        }
    }

    public void dumpTableRevs() throws Exception {
        Cursor cursor = database.getDatabase().rawQuery(
                "SELECT * FROM revs", null);
        while (cursor.moveToNext()) {
            int sequence = cursor.getInt(0);
            int doc_id = cursor.getInt(1);
            byte[] revid = cursor.getBlob(2);
            String revIdStr = null;
            if (revid != null) {
                revIdStr = new String(revid);
            }
            int parent = cursor.getInt(3);
            int current = cursor.getInt(4);
            int deleted = cursor.getInt(5);
            Log.d(TAG, String.format("Revs row seq: %s doc_id: %s, revIdStr: %s, parent: %s, current: %s, deleted: %s",
                    sequence, doc_id, revIdStr, parent, current, deleted));

        }

    }

    public static SavedRevision createRevisionWithRandomProps(SavedRevision createRevFrom, boolean allowConflict) throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(UUID.randomUUID().toString(), "val");
        UnsavedRevision unsavedRevision = createRevFrom.createRevision();
        unsavedRevision.setUserProperties(properties);
        return unsavedRevision.save(allowConflict);
    }

}
