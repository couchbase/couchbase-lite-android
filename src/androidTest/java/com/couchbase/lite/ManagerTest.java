package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.store.SQLiteStore;
import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.support.Version;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;
import com.couchbase.lite.util.ZipUtils;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ManagerTest extends LiteTestCaseWithDB {

    public static final String TAG = "ManagerTest";

    public void testServer() throws CouchbaseLiteException {
        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.cblite
        boolean mustExist = true;
        Database old = manager.getDatabase("foo", mustExist);
        if (old != null) {
            old.delete();
        }

        mustExist = false;
        Database db = manager.getDatabase("foo", mustExist);
        assertNotNull(db);
        assertEquals("foo", db.getName());
        assertTrue(db.getPath().startsWith(
                getDefaultTestContext(false).getFilesDir().getAbsolutePath()));
        assertFalse(db.exists());

        // because foo doesn't exist yet
        List<String> databaseNames = manager.getAllDatabaseNames();
        assertTrue(!databaseNames.contains("foo"));

        db.open();
        assertTrue(db.exists());

        databaseNames = manager.getAllDatabaseNames();
        assertTrue(databaseNames.contains("foo"));

        db.close();
        db.delete();
    }

    public void testReplaceDatabaseNamedNoAttachments() throws CouchbaseLiteException {
        // public void replaceDatabase(String, InputStream, Map<String, InputStream>) is
        // for .cblite. This is only applicable for SQLite
        if (!isSQLiteDB()) return;
        
        //Copy database from assets to local storage
        InputStream dbStream = getAsset("noattachments.cblite");
        manager.replaceDatabase("replaced", dbStream, null);
        //Now validate the number of files in the DB
        assertEquals(10, manager.getDatabase("replaced").getDocumentCount());
    }

    public void testReplaceDatabaseNamedWithAttachments() throws CouchbaseLiteException {
        // public void replaceDatabase(String, InputStream, Map<String, InputStream>) is
        // for .cblite. This is only applicable for SQLite
        if (!isSQLiteDB()) return;

        InputStream dbStream = getAsset("withattachments.cblite");
        Map<String, InputStream> attachments = new HashMap<String, InputStream>();
        InputStream blobStream = getAsset("attachments/356a192b7913b04c54574d18c28d46e6395428ab.blob");
        attachments.put("356a192b7913b04c54574d18c28d46e6395428ab.blob", blobStream);
        manager.replaceDatabase("replaced2", dbStream, attachments);
        //Validate the number of files in the DB
        assertEquals(1, manager.getDatabase("replaced2").getDocumentCount());
        //get the attachment from the document
        Document doc = manager.getDatabase("replaced2").getExistingDocument("168e0c56-4588-4df4-8700-4d5115fa9c74");
        assertNotNull(doc);
        RevisionInternal gotRev1 = database.getDocument(doc.getId(), doc.getCurrentRevisionId(), true);
    }

    /**
     * Test for void replaceDatabase(String, InputStream, Map<String, InputStream>)
     */
    public void testReplaceDatabase() throws CouchbaseLiteException, IOException {
        // public void replaceDatabase(String, InputStream, Map<String, InputStream>) is
        // for .cblite. This is only applicable for SQLite

        // Test for pre-built database test from CBL Android 1.0.4
        {
            // NOTE: in assets, "." -> "/"
            String[] attachments = {
                    "7f0e1bc2d59e1607f21b984ce6fbfe777e6f458e.blob",
                    "fcc1350f03cad8acfc7c13bf8e1cc70485825bda.blob"};
            validateReplaceDatabase("replacedb/android104/androiddb.cblite", 2,
                    "replacedb/android104/androiddb/attachments", attachments,
                    new ValidateDatabaseCallback() {
                        @Override
                        public void validate(Database replacedDatabase)
                                throws CouchbaseLiteException, IOException {
                            validateDatabaseContentFromAndroid(replacedDatabase);
                        }
                    });
        }

        // Test for pre-built database test from CBL Android 1.1.0
        {
            String[] attachments = {
                    "7F0E1BC2D59E1607F21B984CE6FBFE777E6F458E.blob",
                    "FCC1350F03CAD8ACFC7C13BF8E1CC70485825BDA.blob"};
            validateReplaceDatabase("replacedb/android110/androiddb.cblite", 2,
                    "replacedb/android110/androiddb attachments", attachments,
                    new ValidateDatabaseCallback() {
                        @Override
                        public void validate(Database replacedDatabase)
                                throws CouchbaseLiteException, IOException {
                            validateDatabaseContentFromAndroid(replacedDatabase);
                        }
                    });
        }

        // Test for pre-built database test from CBL iOS 1.0.4
        {
            String[] attachments = {
                    "3F58B9908FECA2CABBE39FFD04347B9048212A9B.blob",
                    "89379B9D06B399D0214411FAE32F44E89AB04A87.blob"};
            validateReplaceDatabase("replacedb/ios104/iosdb.cblite", 1,
                    "replacedb/ios104/iosdb attachments", attachments,
                    new ValidateDatabaseCallback() {
                        @Override
                        public void validate(Database replacedDatabase)
                                throws CouchbaseLiteException, IOException {
                            validateDatabaseContentFromIOS(replacedDatabase);
                        }
                    });
        }

        // Test for pre-built database test from CBL iOS 1.1.0
        {
            String[] attachments = {
                    "3F58B9908FECA2CABBE39FFD04347B9048212A9B.blob",
                    "89379B9D06B399D0214411FAE32F44E89AB04A87.blob"};
            validateReplaceDatabase("replacedb/ios110/iosdb.cblite", 1,
                    "replacedb/ios110/iosdb attachments", attachments,
                    new ValidateDatabaseCallback() {
                        @Override
                        public void validate(Database replacedDatabase)
                                throws CouchbaseLiteException, IOException {
                            validateDatabaseContentFromIOS(replacedDatabase);
                        }
                    });
        }

        // Test for pre-built database test from CBL .NET 1.0.4
        {
            String[] attachments = {"d6ea829121af9d025ec61d8157fcf8ea4b445129.blob"};
            validateReplaceDatabase("replacedb/net104/netdb.cblite", 2,
                    "replacedb/net104/netdb/attachments", attachments,
                    new ValidateDatabaseCallback() {
                        @Override
                        public void validate(Database replacedDatabase)
                                throws CouchbaseLiteException, IOException {
                            validateDatabaseContentfromNET1(replacedDatabase);
                        }
                    });
        }

        // Test for pre-built database test from CBL .NET 1.1.0
        {
            String[] attachments = {"d6ea829121af9d025ec61d8157fcf8ea4b445129.blob"};
            validateReplaceDatabase("replacedb/net110/netdb.cblite", 1,
                    "replacedb/net110/netdb attachments", attachments,
                    new ValidateDatabaseCallback() {
                        @Override
                        public void validate(Database replacedDatabase)
                                throws CouchbaseLiteException, IOException {
                            validateDatabaseContentNET2(replacedDatabase);
                        }
                    });
        }
    }

    interface ValidateDatabaseCallback {
        void validate(Database replacedDatabase) throws CouchbaseLiteException, IOException;
    }

    protected void validateReplaceDatabase(String databaseFilename,
                                           int numDocs,
                                           String attachmentDirectory,
                                           String[] attachmentNames,
                                           ValidateDatabaseCallback callback)
            throws CouchbaseLiteException, IOException {

        InputStream dbStream = getAsset(databaseFilename);
        assertNotNull(dbStream);
        Map<String, InputStream> attachmentStreams = new HashMap<String, InputStream>();
        for (String attachment : attachmentNames) {
            InputStream blobStream = getAsset(String.format("%s/%s", attachmentDirectory, attachment));
            assertNotNull(blobStream);
            attachmentStreams.put(attachment, blobStream);
        }
        manager.replaceDatabase("replaced_database", dbStream, attachmentStreams);
        Database replacedDatabase = manager.getDatabase("replaced_database");
        assertEquals(numDocs, replacedDatabase.getDocumentCount());

        if (callback != null)
            callback.validate(replacedDatabase);

        int counter = 0;
        // Create a view and register its map function:
        View v = replacedDatabase.getView("view_test");
        v.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("_id"), document);
            }
        }, "1");
        v.updateIndex();
        Query q = v.createQuery();
        QueryEnumerator r = q.run();
        for (Iterator<QueryRow> it = r; it.hasNext(); ) {
            QueryRow row = it.next();
            Log.w(Log.TAG, row.getDocument().getProperties().toString());
            counter++;
        }
        assertEquals(numDocs, counter);

        replacedDatabase.delete();
    }

    protected void validateDatabaseContentFromAndroid(Database replacedDatabase)
            throws CouchbaseLiteException, IOException {
        for (int i = 0; i < replacedDatabase.getDocumentCount(); i++) {
            Document doc = replacedDatabase.getDocument("doc" + String.valueOf(i));
            Map<String, Object> props = doc.getProperties();
            assertEquals(i, Integer.parseInt((String) props.get("key")));
            List<Attachment> attachments = doc.getCurrentRevision().getAttachments();
            assertEquals(1, attachments.size());
            Attachment attachment = attachments.get(0);
            assertEquals("file_" + String.valueOf(i) + ".txt", attachment.getName());
            BufferedReader br = new BufferedReader(new InputStreamReader(attachment.getContent()));
            String str = br.readLine();
            assertEquals("content " + String.valueOf(i), str);
            br.close();
        }
    }

    protected void validateDatabaseContentFromIOS(Database replacedDatabase)
            throws CouchbaseLiteException, IOException {
        for (int i = 1; i <= replacedDatabase.getDocumentCount(); i++) {
            Document doc = replacedDatabase.getDocument("doc" + String.valueOf(i));
            List<Attachment> attachments = doc.getCurrentRevision().getAttachments();
            assertEquals(2, attachments.size());
            // attachment order is not guaranteed
            Attachment attachment0 = attachments.get(0);
            Attachment attachment1 = attachments.get(1);
            assertFalse(attachment0.getName().equals(attachment1.getName()));
            assertTrue("attach1".equals(attachment0.getName()) || "attach2".equals(attachment0.getName()));
            assertTrue("attach1".equals(attachment1.getName()) || "attach2".equals(attachment1.getName()));
            BufferedReader br0 = new BufferedReader(new InputStreamReader(attachment0.getContent()));
            String str0 = br0.readLine();
            assertTrue(str0.length() > 0);
            br0.close();
            BufferedReader br1 = new BufferedReader(new InputStreamReader(attachment1.getContent()));
            String str1 = br1.readLine();
            assertTrue(str1.length() > 0);
            br1.close();
        }
    }

    protected void validateDatabaseContentfromNET1(Database replacedDatabase)
            throws CouchbaseLiteException, IOException {
        Document doc = replacedDatabase.getDocument("doc2");
        List<Attachment> attachments = doc.getCurrentRevision().getAttachments();
        assertEquals(1, attachments.size());
        Attachment attachment = attachments.get(0);
        assertEquals("image", attachment.getName());
        InputStream is = attachment.getContent();
        assertNotNull(is);
        is.close();
    }

    protected void validateDatabaseContentNET2(Database replacedDatabase)
            throws CouchbaseLiteException, IOException {
        Document doc = replacedDatabase.getDocument("doc1");
        List<Attachment> attachments = doc.getCurrentRevision().getAttachments();
        assertEquals(1, attachments.size());
        Attachment attachment = attachments.get(0);
        assertEquals("image", attachment.getName());
        InputStream is = attachment.getContent();
        assertNotNull(is);
        is.close();
    }

    public void testGetDatabaseConcurrently() throws Exception {
        final String DATABASE_NAME = "test";
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> callables = new ArrayList<Callable<Void>>(2);
            for (int i = 0; i < 2; i++) {
                callables.add(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        manager.getDatabase(DATABASE_NAME);
                        return null;
                    }
                });
            }

            List<Future<Void>> results = executorService.invokeAll(callables);
            for (Future<Void> future : results) {
                // Will throw an exception, thus failing the test, if anything went wrong.
                future.get();
            }
        } finally {
            // Cleanup
            Database a = manager.getExistingDatabase(DATABASE_NAME);
            if (a != null) {
                a.delete();
            }

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(executorService);
        }
    }

    /**
     * Error after close DB client
     * https://github.com/couchbase/couchbase-lite-java/issues/52
     */
    public void testClose() throws Exception {
        Log.d(Log.TAG, "START testClose()");
        MockWebServer server = new MockWebServer();
        try {
            MockDispatcher dispatcher = new MockDispatcher();
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.setDispatcher(dispatcher);
            server.play();

            // checkpoint PUT or GET response (sticky) (for both push and pull)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // create pull replication & start it
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);
            final CountDownLatch pullIdleState = new CountDownLatch(1);
            ReplicationIdleObserver pullIdleObserver = new ReplicationIdleObserver(pullIdleState);
            pull.addChangeListener(pullIdleObserver);
            pull.start();

            // create push replication & start it
            Replication push = database.createPullReplication(server.getUrl("/db"));
            push.setContinuous(true);
            final CountDownLatch pushIdleState = new CountDownLatch(1);
            ReplicationIdleObserver pushIdleObserver = new ReplicationIdleObserver(pushIdleState);
            push.addChangeListener(pushIdleObserver);
            push.start();

            // wait till both push and pull replicators become idle.
            assertTrue(pullIdleState.await(30, TimeUnit.SECONDS));
            assertTrue(pushIdleState.await(30, TimeUnit.SECONDS));
            pull.removeChangeListener(pullIdleObserver);
            push.removeChangeListener(pushIdleObserver);

            final CountDownLatch pullStoppedState = new CountDownLatch(1);
            ReplicationFinishedObserver pullStoppedObserver = new ReplicationFinishedObserver(pullStoppedState);
            pull.addChangeListener(pullStoppedObserver);
            final CountDownLatch pushStoppedState = new CountDownLatch(1);
            ReplicationFinishedObserver pushStoppedObserver = new ReplicationFinishedObserver(pushStoppedState);
            push.addChangeListener(pushStoppedObserver);

            // close Manager, which close database(s) and replicator(s)
            manager.close();

            // manager.close() should wait till replicators are closed.
            // However, notification from replicator is sent by replicator thread.
            // So it is not synchronized with main thread.
            // just give 10 sec.
            assertTrue(pullStoppedState.await(10, TimeUnit.SECONDS));
            assertTrue(pushStoppedState.await(10, TimeUnit.SECONDS));
            pull.removeChangeListener(pullStoppedObserver);
            push.removeChangeListener(pushStoppedObserver);

            // give 3 sec to clean thread status.
            try {
                Thread.sleep(3 * 1000);
            } catch (Exception e) {
            }

            // all threads for Executors should be terminated.
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread t : threadSet) {
                if (t.isAlive()) {
                    assertEquals(-1, t.getName().indexOf("CBLManagerWorkExecutor"));
                    assertEquals(-1, t.getName().indexOf("CBLRequestWorker"));
                }
            }
        } finally {
            // shutdown mock server
            server.shutdown();
        }
        Log.d(Log.TAG, "END testClose()");
    }

    // #pragma mark - REPLACE DATABASE:

    public void test23_ReplaceOldVersionDatabase() throws Exception {

        List<String[]> dbInfoList = new ArrayList<>();
        // Android 1.2.0 (SQLite)
        String[] android120sqlite = {"1", "Android 1.2.0 SQLite", "android120sqlite.cblite2", "replacedb/android120sqlite.cblite2.zip"};
        dbInfoList.add(android120sqlite);
        // Android 1.2.0 (ForestDB)
        String[] android120forest = {"1", "Android 1.2.0 ForestDB", "android120forest.cblite2", "replacedb/android120forest.cblite2.zip"};
        dbInfoList.add(android120sqlite);
        // iOS 1.2.0 (SQLite)
        String[] ios120sqlite = {"2", "iOS 1.2.0 SQLite", "ios120/iosdb.cblite2", "replacedb/ios120.zip"};
        dbInfoList.add(ios120sqlite);
        // iOS 1.2.0 (ForestDB)
        String[] ios120forest = {"2", "iOS 1.2.0 ForestDB", "ios120-forestdb/iosdb.cblite2", "replacedb/ios120-forestdb.zip"};
        dbInfoList.add(ios120forest);
        // .NET 1.2.0 (SQLite)
        String[] net120sqlite = {"3", ".NET 1.2.0 SQLite", "netdb.cblite2", "replacedb/net120-sqlite.zip"};
        dbInfoList.add(net120sqlite);
        // .NET 1.2.0 (ForestDB)
        String[] net120forest = {"3", ".NET 1.2.0 ForestDB", "netdb.cblite2", "replacedb/net120-forestdb.zip"};
        dbInfoList.add(net120forest);

        for(final String[] dbInfo : dbInfoList)
        {
            Log.i(TAG, "DB Type: " + dbInfo[1]);
            File srcDir = new File(manager.getContext().getFilesDir(), dbInfo[2]);
            FileDirUtils.deleteRecursive(srcDir);
            ZipUtils.unzip(getAsset(dbInfo[3]), manager.getContext().getFilesDir());

            testReplaceDatabaseWithCBLite2("replacedb", srcDir.getAbsolutePath(), new ReplaceDatabaseCallback() {
                @Override
                public void onComplete(Database db, QueryEnumerator e) throws CouchbaseLiteException, IOException {

                    // Check Stored Documents
                    assertEquals(2, e.getCount());
                    for (int i = 0; i < 2; i++) {
                        Document doc = e.getRow(i).getDocument();
                        assertNotNull(doc);
                        assertEquals("doc" + (i + 1), doc.getId());
                        assertEquals(2, doc.getRevisionHistory().size());
                        Map<String, Object> props = doc.getProperties();
                        if(dbInfo[0].equals("1"))//android
                            assertEquals(i+1, Integer.parseInt((String) props.get("key")));
                        else if(dbInfo[0].equals("2")) // ios
                            assertEquals("bar", (String)props.get("foo"));
                        assertEquals(1, doc.getCurrentRevision().getAttachments().size());
                        Attachment att = doc.getCurrentRevision().getAttachment("attach" + String.valueOf(i+1));
                        assertNotNull(att);
                        if(!dbInfo[0].equals("3")) {//!NET
                            BufferedReader br = new BufferedReader(new InputStreamReader(att.getContent()));
                            String str = br.readLine();
                            assertEquals("attach" + String.valueOf(i + 1), str);
                            br.close();
                        }
                    }

                    // Check Local Doc
                    Map<String, Object> local = db.getExistingLocalDocument("local1");
                    assertNotNull(local);
                    //assertEquals(3, local.size());
                    if(dbInfo[0].equals("1"))//android
                        assertEquals("local1", local.get("key"));
                    else if(dbInfo[0].equals("2")) // ios
                        assertEquals("bar", local.get("foo"));
                    assertEquals("1-local", local.get("_rev"));
                    assertEquals("_local/local1", local.get("_id"));
                }
            });
        }
    }

    interface ReplaceDatabaseCallback {
        void onComplete(Database db, QueryEnumerator e) throws CouchbaseLiteException, IOException;
    }

    void testReplaceDatabaseWithCBLite2(String name, String databaseDir, ReplaceDatabaseCallback callback)
            throws CouchbaseLiteException, IOException {
        assertTrue(manager.replaceDatabase(name, databaseDir));
        checkReplacedDatabase(name, callback);
        Database replacedb = manager.getDatabase(name);
        replacedb.delete();
    }

    void checkReplacedDatabase(String name, ReplaceDatabaseCallback callback)
            throws CouchbaseLiteException, IOException {
        Database replaceDb = this.manager.getExistingDatabase(name);
        assertNotNull(replaceDb);

        View view = replaceDb.getView("myview");
        assertNotNull(view);
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("_id"), null);
            }
        }, "1.0");

        Query query = view.createQuery();
        assertNotNull(query);
        query.setPrefetch(true);
        QueryEnumerator e = query.run();
        assertNotNull(e);

        if (callback != null)
            callback.onComplete(replaceDb, e);
    }

    public void testUpgradeDatabase() throws Exception {
        // Install a canned database:
        File srcDir = new File(manager.getContext().getFilesDir(), "ios120/iosdb.cblite2");
        FileDirUtils.deleteRecursive(srcDir);
        ZipUtils.unzip(getAsset("replacedb/ios120.zip"), manager.getContext().getFilesDir());
        manager.replaceDatabase("replacedb", srcDir.getAbsolutePath());

        // Open installed db with storageType set to this test's storage type:
        DatabaseOptions options = new DatabaseOptions();
        options.setStorageType(isSQLiteDB() ? Manager.SQLITE_STORAGE : Manager.FORESTDB_STORAGE);
        Database replacedb = manager.openDatabase("replacedb", options);
        assertNotNull(replacedb);

        // Verify storage type matchs what we requested:
        Class forestDBStoreClass = Class.forName("com.couchbase.lite.store.ForestDBStore");
        Class storeClass = isSQLiteDB() ? SQLiteStore.class : forestDBStoreClass;
        assertTrue(replacedb.getStore().getClass().equals(storeClass));

        // Test db contents:
        checkReplacedDatabase("replacedb", new ReplaceDatabaseCallback() {
            @Override
            public void onComplete(Database db, QueryEnumerator e) throws CouchbaseLiteException, IOException{
                // Check Stored Documents
                assertEquals(2, e.getCount());
                for (int i = 0; i < 2; i++) {
                    Document doc = e.getRow(i).getDocument();
                    assertNotNull(doc);
                    assertEquals("doc" + (i + 1), doc.getId());
                    assertEquals(2, doc.getRevisionHistory().size());
                    Map<String, Object> props = doc.getProperties();
                    assertEquals("bar", (String)props.get("foo"));
                    assertEquals(1, doc.getCurrentRevision().getAttachments().size());
                    Attachment att = doc.getCurrentRevision().getAttachment("attach" + String.valueOf(i+1));
                    assertNotNull(att);
                    BufferedReader br = new BufferedReader(new InputStreamReader(att.getContent()));
                    String str = br.readLine();
                    assertEquals("attach" + String.valueOf(i + 1), str);
                    br.close();
                }

                // NOTE: Upgrade does not support local doc??
                // check local doc
                Map<String, Object> local = db.getExistingLocalDocument("local1");
                assertNotNull(local);
                assertEquals("bar", local.get("foo"));
                assertEquals("1-local", local.get("_rev"));
                assertEquals("_local/local1", local.get("_id"));
            }
        });

        // Close and re-open the db using SQLite storage type. Should fail if it used to be ForestDB:
        assertTrue(replacedb.close());
        options.setStorageType(Manager.SQLITE_STORAGE);

        CouchbaseLiteException error = null;
        try {
            replacedb = null;
            replacedb = manager.openDatabase("replacedb", options);
        } catch (CouchbaseLiteException e) {
            error = e;
        }

        if (isSQLiteDB()) {
            assertNotNull(replacedb);
        } else {
            assertNull("Incorrectly re-opened ForestDB db as SQLite", replacedb);
            assertNotNull(error);
            assertEquals(Status.INVALID_STORAGE_TYPE, error.getCBLStatus().getCode());
            assertEquals(406, error.getCBLStatus().getHTTPCode());
        }
    }

    public void testGetUserAgent() {
        String userAgent = Manager.getUserAgent();
        assertTrue(userAgent.indexOf(Manager.PRODUCT_NAME + "/" + Version.SYNC_PROTOCOL_VERSION) != -1);
    }
}
