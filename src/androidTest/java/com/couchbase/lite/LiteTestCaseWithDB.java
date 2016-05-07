package com.couchbase.lite;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockDocumentGet;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.replicator.CustomizableMockHttpClient;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.replicator.ReplicationState;
import com.couchbase.lite.router.Router;
import com.couchbase.lite.router.RouterCallbackBlock;
import com.couchbase.lite.router.URLConnection;
import com.couchbase.lite.router.URLStreamHandlerFactory;
import com.couchbase.lite.storage.SQLiteNativeLibrary;
import com.couchbase.lite.store.SQLiteStore;
import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.support.Version;
import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.cookie.Cookie;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.Cipher;

public class LiteTestCaseWithDB extends LiteTestCase {
    public static final String TAG = "LiteTestCaseWithDB";

    private static boolean initializedUrlHandler = false;

    protected ObjectMapper mapper = new ObjectMapper();

    protected Manager manager = null;
    protected Database database = null;
    protected static final String DEFAULT_TEST_DB = "cblite-test";
    protected static final String DEFAULT_TEST_DIR_NAME = "test";

    protected boolean useForestDB = false;

    private boolean encryptedAttachmentStore = false;

    protected boolean isSQLiteDB() {
        return SQLiteStore.class.equals(database.getStore().getClass());
    }

    protected boolean isUseForestDB() {
        return useForestDB;
    }

    @Override
    public void runBare() throws Throwable {
        long start = System.currentTimeMillis();

        loadCustomProperties();

        // Run Unit Test with SQLiteStore
        if(getTestStorageType() != 2) {
            useForestDB = false;
            super.runBare();
        }

        // Run Unit Test with ForestDBStore
        if(getTestStorageType() != 1) {
            useForestDB = true;
            super.runBare();
        }

        long end = System.currentTimeMillis();
        String name = getName();
        long duration= (end - start)/1000;
        Log.e(TAG, "DURATION: %s: %d sec%s", name, duration, duration >= 6 ? " - [SLOW]" : "");
    }

    public void runBare(boolean forestDB) throws Throwable {
        long start = System.currentTimeMillis();

        useForestDB = forestDB;
        super.runBare();

        long end = System.currentTimeMillis();
        String name = getName();
        long duration= (end - start)/1000;
        Log.e(TAG, "DURATION: %s: %d sec%s", name, duration, duration >= 6 ? " - [SLOW]" : "");
    }

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "setUp");
        super.setUp();

        loadCustomProperties();

        if (!useForestDB)
            setupSQLiteNativeLibrary();

        //for some reason a traditional static initializer causes junit to die
        if (!initializedUrlHandler) {
            URLStreamHandlerFactory.registerSelfIgnoreError();
            initializedUrlHandler = true;
        }

        startCBLite();
        startDatabase();
    }

    protected void setupSQLiteNativeLibrary() {
        int library = getSQLiteLibrary();
        if (library == 0)
            SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME = SQLiteNativeLibrary.JNI_SQLITE_DEFAULT_LIBRARY;
        else if (library == 1)
            SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME = SQLiteNativeLibrary.JNI_SQLITE_CUSTOM_LIBRARY;
        else if (library == 2)
            SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME = SQLiteNativeLibrary.JNI_SQLCIPHER_LIBRARY;
        else
            throw new IllegalArgumentException("Invalid Native Library : " + library);
    }

    protected static boolean syncgatewayTestsEnabled() {
        return Boolean.parseBoolean(System.getProperty("syncgatewayTestsEnabled"));
    }

    protected static int getSQLiteLibrary() {
        return Integer.parseInt(System.getProperty("sqliteLibrary"));
    }

    protected static int getTestStorageType() {
        return Integer.parseInt(System.getProperty("storageType"));
    }

    protected boolean isEncryptionTestEnabled() {
        if (!isAndriod()) {
            boolean support256Key = false;
            try {
                support256Key = Cipher.getMaxAllowedKeyLength("AES") >= 256;
            } catch (NoSuchAlgorithmException e) { }
            if (!support256Key)
                return false;
        }

        return !isSQLiteDB() ||
                (isSQLiteDB() && SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME == SQLiteNativeLibrary.JNI_SQLCIPHER_LIBRARY);
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    protected Context getDefaultTestContext(boolean deleteContent) {
        return getTestContext(DEFAULT_TEST_DIR_NAME, deleteContent);
    }

    protected Context getTestContext(String dirName, boolean deleteContent) {
        Context context = getTestContext(dirName);
        if (deleteContent && context.getFilesDir().exists())
            assertTrue(FileDirUtils.cleanDirectory(context.getFilesDir()));
        return context;
    }

    protected void startCBLite() throws IOException {
        Manager.enableLogging(TAG, Log.VERBOSE);
        Manager.enableLogging(Log.TAG, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_SYNC, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_SYNC_ASYNC_TASK, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_BATCHER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_QUERY, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_VIEW, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_CHANGE_TRACKER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_BLOB_STORE, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_DATABASE, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_LISTENER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_MULTI_STREAM_WRITER, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_REMOTE_REQUEST, Log.VERBOSE);
        Manager.enableLogging(Log.TAG_ROUTER, Log.VERBOSE);

        Context context = getDefaultTestContext(true);
        manager = createManager(context);
    }

    protected Manager createManager(Context context) throws IOException {
        ManagerOptions options = new ManagerOptions();
        Manager manager = new Manager(context, options);
        if (useForestDB)
            manager.setStorageType(Manager.FORESTDB_STORAGE);
        return manager;
    }

    protected void stopCBLite() {
        int DEFAULT_VALUE = Utils.DEFAULT_TIME_TO_WAIT_4_SHUTDOWN;
        Utils.DEFAULT_TIME_TO_WAIT_4_SHUTDOWN = 0;
        try {
            if (manager != null) {
                manager.close();
            }
        }
        finally{
            Utils.DEFAULT_TIME_TO_WAIT_4_SHUTDOWN = DEFAULT_VALUE;
        }
    }

    protected Database startDatabase() throws CouchbaseLiteException {
        database = ensureEmptyDatabase(DEFAULT_TEST_DB);
        return database;
    }

    protected void stopDatabase() {
        if (database != null) {
            database.close();
        }
    }

    protected Database ensureEmptyDatabase(String dbName) throws CouchbaseLiteException {
        Database db = manager.getExistingDatabase(dbName);
        if (db != null) {
            db.delete();
        }
        db = manager.getDatabase(dbName);
        return db;
    }

    protected void loadCustomProperties() throws IOException {
        Properties systemProperties = System.getProperties();
        InputStream mainProperties = getAsset("test.properties");
        if (mainProperties != null) {
            systemProperties.load(new InputStreamReader(mainProperties, "UTF-8"));
            mainProperties.close();
        }
        try {
            InputStream localProperties = getAsset("local-test.properties");
            if (localProperties != null) {
                systemProperties.load(new InputStreamReader(localProperties, "UTF-8"));
                localProperties.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error trying to read from local-test.properties, does this file exist?");
        }
    }

    protected URL getReplicationURL() throws MalformedURLException {
        return new URL(System.getProperty("replicationUrl"));
    }

    protected URL getAdminReplicationURL() throws MalformedURLException {
        return new URL(System.getProperty("adminReplicationUrl"));
    }

    protected boolean isTestingAgainstSyncGateway() {
        try {
            URL url = getReplicationURL();
            return url.getPort() == 4984;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        Log.v(TAG, "tearDown");
        super.tearDown();
        stopDatabase();
        stopCBLite();
    }

    protected Map<String, Object> userProperties(Map<String, Object> properties) {
        Map<String, Object> result = new HashMap<String, Object>();

        for (String key : properties.keySet()) {
            if (!key.startsWith("_")) {
                result.put(key, properties.get(key));
            }
        }

        return result;
    }

    public boolean isEncryptedAttachmentStore() {
        return encryptedAttachmentStore;
    }

    public void setEncryptedAttachmentStore(boolean encrypted)
            throws Exception {
        SymmetricKey key = encrypted ? new SymmetricKey() : null;
        database.getAttachmentStore().changeEncryptionKey(key);
        encryptedAttachmentStore = encrypted;
    }

    public Map<String, Object> getReplicationAuthParsedJson() throws IOException {
        String authJson = "{\n" +
                "    \"facebook\" : {\n" +
                "        \"email\" : \"jchris@couchbase.com\"\n" +
                "     }\n" +
                "   }\n";
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> authProperties = mapper.readValue(authJson,
                new TypeReference<HashMap<String, Object>>() {
                });
        return authProperties;

    }

    public Map<String, Object> getPushReplicationParsedJson(URL url) throws IOException {

        Map<String, Object> targetProperties = new HashMap<String, Object>();
        targetProperties.put("url", url.toExternalForm());

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", targetProperties);
        return properties;
    }

    public Map<String, Object> getPullReplicationParsedJson(URL url) throws IOException {

        Map<String, Object> sourceProperties = new HashMap<String, Object>();
        sourceProperties.put("url", url.toExternalForm());

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("source", sourceProperties);
        properties.put("target", DEFAULT_TEST_DB);
        return properties;
    }


    protected URLConnection sendRequest(String method, String path,
                                        Map<String, String> headers, Object bodyObj) {
        try {
            URL url = new URL("cblite://" + path);
            URLConnection conn = (URLConnection) url.openConnection();
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setRequestMethod(method);
            if (headers != null) {
                for (String header : headers.keySet()) {
                    conn.setRequestProperty(header, headers.get(header));
                }
            }
            Map<String, List<String>> allProperties = conn.getRequestProperties();
            if (bodyObj != null) {
                conn.setDoInput(true);
                ByteArrayInputStream bais = new ByteArrayInputStream(mapper.writeValueAsBytes(bodyObj));
                conn.setRequestInputStream(bais);
            }

            Router router = new com.couchbase.lite.router.Router(manager, conn);

            final CountDownLatch latch = new CountDownLatch(1);
            router.setCallbackBlock(new RouterCallbackBlock() {
                @Override
                public void onResponseReady() {
                    latch.countDown();
                }
            });

            router.start();

            boolean success = false;
            try {
                // NOTE: latch.await() should be fine. 60 sec for just in case.
                success = latch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            assertTrue(success);

            return conn;
        } catch (MalformedURLException e) {
            fail();
        } catch (IOException e) {
            fail();
        }
        return null;
    }

    protected Object parseJSONResponse(URLConnection conn) {
        Object result = null;
        Body responseBody = conn.getResponseBody();
        if (responseBody != null) {
            byte[] json = responseBody.getJson();
            String jsonString = null;
            if (json != null) {
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

    protected Object sendBody(String method, String path, Object bodyObj,
                              int expectedStatus, Object expectedResult) {
        URLConnection conn = sendRequest(method, path, null, bodyObj);
        conn.setRequestProperty("Content-Type", "application/json");
        Object result = parseJSONResponse(conn);
        Log.v(TAG, "%s %s --> %d", method, path, conn.getResponseCode());
        Assert.assertEquals(expectedStatus, conn.getResponseCode());
        if (expectedResult != null) {
            Assert.assertEquals(expectedResult, result);
        }
        return result;
    }

    protected Object send(String method, String path, int expectedStatus, Object expectedResult) {
        return sendBody(method, path, null, expectedStatus, expectedResult);
    }

    public static void createDocuments(final Database db, final int n) {
        //TODO should be changed to use db.runInTransaction
        for (int i = 0; i < n; i++) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("testName", "testDatabase");
            properties.put("sequence", i);
            createDocumentWithProperties(db, properties);
        }
    }

    static Future createDocumentsAsync(final Database db, final int n) {
        return db.runAsync(new AsyncTask() {
            @Override
            public void run(Database database) {
                db.runInTransaction(new TransactionalTask() {
                    @Override
                    public boolean run() {
                        createDocuments(db, n);
                        return true;
                    }
                });
            }
        });
    }

    public static Document createDocumentWithProperties(Database db,
                                                        Map<String, Object> properties) {
        Document doc = db.createDocument();
        Assert.assertNotNull(doc);
        Assert.assertNull(doc.getCurrentRevisionId());
        Assert.assertNull(doc.getCurrentRevision());
        Assert.assertNotNull("Document has no ID", doc.getId());
        // 'untitled' docs are no longer untitled (8/10/12)
        try {
            doc.putProperties(properties);
        } catch (Exception e) {
            Log.e(TAG, "Error creating document", e);
            assertTrue("can't create new document in db:" + db.getName() + " with properties:" +
                    properties.toString(), false);
        }
        Assert.assertNotNull(doc.getId());
        Assert.assertNotNull(doc.getCurrentRevisionId());
        Assert.assertNotNull(doc.getUserProperties());

        // should be same doc instance, since there should only ever be a single Document
        // instance for a given document
        Assert.assertEquals(db.getDocument(doc.getId()), doc);

        Assert.assertEquals(db.getDocument(doc.getId()).getId(), doc.getId());

        return doc;
    }

    public static Document createDocWithAttachment(Database database, String attachmentName,
                                                   String content, Map<String, Object> properties)
            throws Exception {

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

    public static Document createDocWithAttachment(Database database,
                                                   String attachmentName, String content)
            throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");
        return createDocWithAttachment(database, attachmentName, content, properties);
    }

    public void stopReplication(Replication replication) throws Exception {
        final CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        replication.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));

        replication.stop();

        boolean success = replicationDoneSignal.await(30, TimeUnit.SECONDS);
        assertTrue(success);
    }

    protected String createDocumentsForPushReplication(String docIdTimestamp)
            throws CouchbaseLiteException {
        return createDocumentsForPushReplication(docIdTimestamp, "png");
    }

    protected Document createDocumentForPushReplication(String docId, String attachmentFileName,
                                                        String attachmentContentType)
            throws CouchbaseLiteException {

        Map<String, Object> docJsonMap = MockHelper.generateRandomJsonMap();
        Map<String, Object> docProperties = new HashMap<String, Object>();
        docProperties.put("_id", docId);
        docProperties.putAll(docJsonMap);
        Document document = database.getDocument(docId);
        UnsavedRevision revision = document.createRevision();
        revision.setProperties(docProperties);

        if (attachmentFileName != null) {
            revision.setAttachment(
                    attachmentFileName,
                    attachmentContentType,
                    getAsset(attachmentFileName)
            );
        }

        revision.save();
        return document;

    }

    protected String createDocumentsForPushReplication(String docIdTimestamp, String attachmentType)
            throws CouchbaseLiteException {
        String doc1Id;
        String doc2Id;// Create some documents:
        Map<String, Object> doc1Properties = new HashMap<String, Object>();
        doc1Id = String.format("doc1-%s", docIdTimestamp);
        doc1Properties.put("_id", doc1Id);
        doc1Properties.put("foo", 1);
        doc1Properties.put("bar", false);

        Body body = new Body(doc1Properties);
        RevisionInternal rev1 = new RevisionInternal(body);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);
        assertEquals(Status.CREATED, status.getCode());

        doc1Properties.put("_rev", rev1.getRevID());
        doc1Properties.put("UPDATED", true);

        @SuppressWarnings("unused")
        RevisionInternal rev2 = database.putRevision(new RevisionInternal(doc1Properties),
                rev1.getRevID(), false, status);
        assertEquals(Status.CREATED, status.getCode());

        Map<String, Object> doc2Properties = new HashMap<String, Object>();
        doc2Id = String.format("doc2-%s", docIdTimestamp);
        doc2Properties.put("_id", doc2Id);
        doc2Properties.put("baz", 666);
        doc2Properties.put("fnord", true);

        database.putRevision(new RevisionInternal(doc2Properties), null, false, status);
        assertEquals(Status.CREATED, status.getCode());

        Document doc2 = database.getDocument(doc2Id);
        UnsavedRevision doc2UnsavedRev = doc2.createRevision();
        if (attachmentType.equals("png")) {
            InputStream attachmentStream = getAsset("attachment.png");
            doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        } else if (attachmentType.equals("txt")) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a large attachemnt.");
            }
            ByteArrayInputStream attachmentStream = new ByteArrayInputStream(sb.toString().getBytes());
            doc2UnsavedRev.setAttachment("attachment.txt", "text/plain", attachmentStream);
        } else {
            throw new RuntimeException("invalid attachment type: " + attachmentType);
        }
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        return doc1Id;
    }

    // timeout - second
    public void runReplication(Replication replication) throws Exception {
        runReplication(replication, 120);
    }

    // timeout - second
    public void runReplication(Replication replication, long timeout) throws Exception {
        final CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        replication.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));
        replication.start();
        boolean success = replicationDoneSignal.await(timeout, TimeUnit.SECONDS);
        assertTrue(success);
    }

    public void waitForPutCheckpointRequestWithSeq(MockDispatcher dispatcher, int seq) throws TimeoutException {
        while (true) {
            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            checkUserAgent(request);
            if (request.getMethod().equals("PUT")) {
                String body = request.getUtf8Body();
                if (body.indexOf(Integer.toString(seq)) != -1) {
                    // block until response returned
                    dispatcher.takeRecordedResponseBlocking(request);
                    return;
                }
            }
        }
    }

    protected void checkUserAgent(RecordedRequest request){
        assertNotNull(request);
        String userAgent = request.getHeader("User-Agent");
        assertNotNull(userAgent);
        Log.v(TAG, "[checkUserAgent(RecordedRequest()] UserAgent: " + userAgent);
        assertTrue(userAgent.indexOf(Manager.PRODUCT_NAME + "/" + Version.SYNC_PROTOCOL_VERSION) != -1);
    }

    protected List<RecordedRequest> waitForPutCheckpointRequestWithSequence(MockDispatcher dispatcher,
                                                                            int expectedLastSequence)
            throws IOException, TimeoutException {

        Log.d(TAG, "Wait for PUT checkpoint request with lastSequence: %s", expectedLastSequence);

        List<RecordedRequest> recordedRequests = new ArrayList<RecordedRequest>();

        // wait until mock server gets a checkpoint PUT request with expected lastSequence
        boolean foundExpectedLastSeq = false;
        String expectedLastSequenceStr = String.format("%s", expectedLastSequence);

        while (!foundExpectedLastSeq) {

            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            if (request.getMethod().equals("PUT")) {

                recordedRequests.add(request);

                Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(
                        request.getUtf8Body(), Map.class);
                Log.i(TAG, "lastSequence=" +jsonMap.get("lastSequence"));
                Log.i(TAG, "checkpoint request=" + jsonMap);
                if (jsonMap.containsKey("lastSequence") &&
                        ((String) jsonMap.get("lastSequence")).equals(expectedLastSequenceStr)) {
                    foundExpectedLastSeq = true;
                }

                // wait until mock server responds to the checkpoint PUT request.
                // not sure if this is strictly necessary, but might prevent race conditions.
                dispatcher.takeRecordedResponseBlocking(request);

            }
        }

        return recordedRequests;
    }

    protected void validateCheckpointRequestsRevisions(List<RecordedRequest> checkpointRequests) {
        try {
            int i = 0;
            for (RecordedRequest request : checkpointRequests) {
                Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(
                        request.getUtf8Body(), Map.class);
                if (i == 0) {
                    // the first request is not expected to have a _rev field
                    assertFalse(jsonMap.containsKey("_rev"));
                } else {
                    assertTrue(jsonMap.containsKey("_rev"));
                    // TODO: make sure that each _rev is in sequential order, eg: "0-1", "0-2", etc..
                }
                i += 1;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Document createDocWithProperties(Map<String, Object> props)
            throws CouchbaseLiteException {
        return createDocWithProperties(props, database);
    }

    protected Document createDocWithProperties(Map<String, Object> props, Database db)
            throws CouchbaseLiteException {
        Document doc = db.createDocument();
        UnsavedRevision revUnsaved = doc.createRevision();
        revUnsaved.setUserProperties(props);
        SavedRevision rev = revUnsaved.save();
        assertNotNull(rev);
        return doc;
    }

    protected HttpClientFactory mockFactoryFactory(final CustomizableMockHttpClient mockHttpClient) {
        return new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }

            @Override
            public void addCookies(List<Cookie> cookies) {
            }

            @Override
            public void deleteCookie(String name) {
            }

            @Override
            public CookieStore getCookieStore() {
                return null;
            }
        };
    }

    protected void attachmentAsserts(String docAttachName, Document doc)
            throws IOException, CouchbaseLiteException {
        Attachment attachment = doc.getCurrentRevision().getAttachment(docAttachName);
        assertNotNull(attachment);
        byte[] testAttachBytes = MockDocumentGet.getAssetByteArray(docAttachName);
        int attachLength = testAttachBytes.length;
        assertEquals(attachLength, attachment.getLength());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = attachment.getContent();
        baos.write(is);
        is.close();
        byte[] actualAttachBytes = baos.toByteArray();
        assertEquals(testAttachBytes.length, actualAttachBytes.length);
        for (int i = 0; i < actualAttachBytes.length; i++) {
            boolean ithByteEqual = actualAttachBytes[i] == testAttachBytes[i];
            if (!ithByteEqual) {
                Log.d(Log.TAG, "mismatch");
            }
            assertTrue(ithByteEqual);
        }
    }

    public static SavedRevision createRevisionWithRandomProps(SavedRevision createRevFrom,
                                                              boolean allowConflict)
            throws CouchbaseLiteException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(UUID.randomUUID().toString(), "val");
        UnsavedRevision unsavedRevision = createRevFrom.createRevision();
        unsavedRevision.setUserProperties(properties);
        return unsavedRevision.save(allowConflict);
    }

    /*
    Assert that the bulk docs json in request contains given doc.

    Example bulk docs json.

     {
       "docs":[
         {
           "_id":"b7f5664c-7f84-4ddf-9abc-de4e3f376ae4",
           ..
         }
       ]
     }
     */
    protected void assertBulkDocJsonContainsDoc(RecordedRequest request, Document doc)
            throws Exception {

        Map<String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(
                request.getUtf8Body(), Map.class);
        List docs = (List) bulkDocsJson.get("docs");
        Map<String, Object> firstDoc = (Map<String, Object>) docs.get(0);
        assertEquals(doc.getId(), firstDoc.get("_id"));
    }

    protected boolean isBulkDocJsonContainsDoc(RecordedRequest request, Document doc)
            throws Exception {
        Map<String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(
                request.getUtf8Body(), Map.class);
        List docs = (List) bulkDocsJson.get("docs");
        Iterator<Object> itr = docs.iterator();
        while (itr.hasNext()) {
            Map<String, Object> tmp = (Map<String, Object>) itr.next();
            if (tmp.get("_id").equals(doc.getId()))
                return true;
        }
        return false;
    }

    public static class ReplicationIdleObserver implements Replication.ChangeListener {
        private CountDownLatch idleSignal;

        public ReplicationIdleObserver(CountDownLatch idleSignal) {
            this.idleSignal = idleSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            if (event.getTransition() != null &&
                    event.getTransition().getDestination() == ReplicationState.IDLE) {
                idleSignal.countDown();
            }
        }
    }

    public static class ReplicationFinishedObserver implements Replication.ChangeListener {
        private CountDownLatch doneSignal;

        public ReplicationFinishedObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            if (event.getTransition() != null &&
                    event.getTransition().getDestination() == ReplicationState.STOPPED) {
                doneSignal.countDown();
                assertEquals(event.getChangeCount(), event.getCompletedChangeCount());
            }
        }
    }

    public static class ReplicationOfflineObserver implements Replication.ChangeListener {
        private CountDownLatch offlineSignal;

        public ReplicationOfflineObserver(CountDownLatch offlineSignal) {
            this.offlineSignal = offlineSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            if (event.getTransition() != null &&
                    event.getTransition().getDestination() == ReplicationState.OFFLINE) {
                offlineSignal.countDown();
            }
        }
    }

    public static class ReplicationRunningObserver implements Replication.ChangeListener {
        private CountDownLatch runningSignal;

        public ReplicationRunningObserver(CountDownLatch runningSignal) {
            this.runningSignal = runningSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            if (event.getTransition() != null &&
                    event.getTransition().getDestination() == ReplicationState.RUNNING) {
                runningSignal.countDown();
            }
        }
    }

    protected void reopenTestDB() throws CouchbaseLiteException {
        Log.i(TAG, "---- closing db ----");
        String dbName = database.getName();
        assertTrue(database.close());

        Log.i(TAG, "---- reopening db ----");
        Database db2 = manager.getDatabase(dbName);
        assertNotNull(db2);
        assertTrue(db2 != database);
        database = db2;
    }
}
