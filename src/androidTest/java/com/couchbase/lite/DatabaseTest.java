package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.support.RevisionUtils;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseTest extends LiteTestCaseWithDB {
    /**
     * in DatabaseInternal_Tests.m
     * -(void) test26_ReAddAfterPurge
     */
    public void test26_ReAddAfterPurge() throws CouchbaseLiteException {
        String docId = "test26-ReAddAfterPurge";

        RevisionInternal rev = new RevisionInternal(docId, "1-1111", false);
        Map<String, Object> props = new HashMap<>();
        props.put("_id", rev.getDocID());
        props.put("_rev", rev.getRevID());
        props.put("testName", "test26_ReAddAfterPurge");
        rev.setProperties(props);
        database.forceInsert(rev, null, null);

        Document redoc = database.getExistingDocument(docId);
        assertNotNull(redoc);

        Log.i(TAG, "Before purge, lastSequence = %d", database.getLastSequenceNumber());

        Log.i(TAG, "PURGE");
        redoc.purge();
        Log.i(TAG, "After purge, lastSequence = %d", database.getLastSequenceNumber());

        assertNull(database.getExistingDocument(docId));

        reopenTestDB();

        Log.i(TAG, "After reopen, lastSequence = %d", database.getLastSequenceNumber());
        assertNull(database.getExistingDocument(docId));

        RevisionInternal revAfterPurge = new RevisionInternal(docId, "1-1111", false);
        Map<String, Object> props2 = new HashMap<>();
        props2.put("_id", revAfterPurge.getDocID());
        props2.put("_rev", revAfterPurge.getRevID());
        props2.put("testName", "test26_ReAddAfterPurge");
        revAfterPurge.setProperties(props2);
        database.forceInsert(revAfterPurge, null, null);
    }

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test27_ChangesSinceSequence
     */
    public void test27_ChangesSinceSequence() throws CouchbaseLiteException {
        // Create 10 docs:
        createDocuments(database, 10);

        // Create a new doc with a conflict:
        RevisionInternal rev = new RevisionInternal("MyDocID", "1-1111", false);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        properties.put("message", "hi");
        rev.setProperties(properties);
        List<String> history = Arrays.asList(rev.getRevID());
        database.forceInsert(rev, history, null);
        rev = new RevisionInternal("MyDocID", "1-ffff", false);
        properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        properties.put("message", "bye");
        rev.setProperties(properties);
        history = Arrays.asList(rev.getRevID());
        database.forceInsert(rev, history, null);

        // Create another doc with a merged conflict:
        rev = new RevisionInternal("MyDocID2", "1-1111", false);
        properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        properties.put("message", "hi");
        rev.setProperties(properties);
        history = Arrays.asList(rev.getRevID());
        database.forceInsert(rev, history, null);
        rev = new RevisionInternal("MyDocID2", "1-ffff", true);
        properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        rev.setProperties(properties);
        history = Arrays.asList(rev.getRevID());
        database.forceInsert(rev, history, null);

        // Get changes, testing all combinations of includeConflicts and includeDocs:
        for (int conflicts = 0; conflicts <= 1; conflicts++) {
            for (int bodies = 0; bodies <= 1; bodies++) {
                ChangesOptions options = new ChangesOptions();
                options.setIncludeConflicts(conflicts != 0);
                options.setIncludeDocs(bodies != 0);
                RevisionList changes = database.changesSince(0, options, null, null);
                assertEquals(12 + 2 * conflicts, changes.size());
                for (RevisionInternal change : changes) {
                    if (bodies != 0)
                        assertNotNull(change.getBody());
                    else
                        assertNull(change.getBody());
                }
            }
        }
    }

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test28_enableAutoCompact
     */
    public void test28_enableAutoCompact() throws CouchbaseLiteException {
        // Ensure that a database created without auto-compact (by CBL 1.1, or prior to 10/5/15) can
        // still be opened, since it has to be switched to auto-compact mode.
        Database.setAutoCompact(false);
        Database manualDB = manager.getDatabase("manualcompact");
        assertNotNull(manualDB);
        this.createDocWithProperties(new HashMap<String, Object>(), manualDB);
        manualDB.close();

        Database.setAutoCompact(true);
        manualDB = manager.getDatabase("manualcompact");
        assertNotNull(manualDB);
        manualDB.close();
    }

    public void testPruneRevsToMaxDepthViaCompact() throws Exception {

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testDatabaseCompaction");
        properties.put("tag", 1337);

        Document doc = createDocumentWithProperties(database, properties);
        SavedRevision rev = doc.getCurrentRevision();

        database.setMaxRevTreeDepth(2);
        for (int i = 0; i < 10; i++) {
            Map<String, Object> properties2 = new HashMap<String, Object>(properties);
            properties2.put("tag", i);
            rev = rev.createRevision(properties2);
        }

        database.compact();

        Document fetchedDoc = database.getDocument(doc.getId());
        List<SavedRevision> revisions = fetchedDoc.getRevisionHistory();
        assertEquals(2, revisions.size());
    }

    /**
     * When making inserts in a transaction, the change notifications should
     * be batched into a single change notification (rather than a change notification
     * for each insert)
     */
    public void testChangeListenerNotificationBatching() throws Exception {

        final int numDocs = 50;
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                atomicInteger.incrementAndGet();
            }
        });

        database.runInTransaction(new TransactionalTask() {
            @Override
            public boolean run() {
                createDocuments(database, numDocs);
                countDownLatch.countDown();
                return true;
            }
        });

        boolean success = countDownLatch.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        assertEquals(1, atomicInteger.get());
    }

    /**
     * When making inserts outside of a transaction, there should be a change notification
     * for each insert (no batching)
     */
    public void testChangeListenerNotification() throws Exception {
        final int numDocs = 50;
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                atomicInteger.incrementAndGet();
            }
        });
        createDocuments(database, numDocs);
        assertEquals(numDocs, atomicInteger.get());
    }

    /**
     * Change listeners should only be called once no matter how many times they're added.
     */
    public void testAddChangeListenerIsIdempotent() throws Exception {
        final AtomicInteger count = new AtomicInteger(0);
        Database.ChangeListener listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                count.incrementAndGet();
            }
        };
        database.addChangeListener(listener);
        database.addChangeListener(listener);
        createDocuments(database, 1);
        assertEquals(1, count.intValue());
    }

    public void testGetActiveReplications() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 0;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, 1);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        try {
            server.play();

            final Replication replication = database.createPullReplication(server.getUrl("/db"));

            assertEquals(0, database.getAllReplications().size());
            assertEquals(0, database.getActiveReplications().size());

            final CountDownLatch replicationRunning = new CountDownLatch(1);
            replication.addChangeListener(new ReplicationRunningObserver(replicationRunning));

            replication.start();

            boolean success = replicationRunning.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            assertEquals(1, database.getAllReplications().size());
            assertEquals(1, database.getActiveReplications().size());

            final CountDownLatch replicationDoneSignal = new CountDownLatch(1);
            replication.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));

            success = replicationDoneSignal.await(60, TimeUnit.SECONDS);
            assertTrue(success);

            // workaround race condition.  Since our replication change listener will get triggered
            // _before_ the internal change listener that updates the activeReplications map, we
            // need to pause briefly to let the internal change listener to update activeReplications.
            Thread.sleep(500);

            assertEquals(1, database.getAllReplications().size());
            assertEquals(0, database.getActiveReplications().size());
        }finally {
            server.shutdown();
        }
    }

    public void testGetDatabaseNameFromPath() throws Exception {

        assertEquals("baz", FileDirUtils.getDatabaseNameFromPath("foo/bar/baz.cblite"));

    }

    public void testEncodeDocumentJSON() throws Exception {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_local_seq", "");
        RevisionInternal revisionInternal = new RevisionInternal(props);
        byte[] encoded = RevisionUtils.asCanonicalJSON(revisionInternal);
        assertNotNull(encoded);
    }

    /**
     * in Database_Tests.m
     * - (void) test075_UpdateDocInTransaction
     */
    public void testUpdateDocInTransaction() throws InterruptedException {
        // Test for #256, "Conflict error when updating a document multiple times in transaction block"
        // https://github.com/couchbase/couchbase-lite-ios/issues/256

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testUpdateDocInTransaction");
        properties.put("count", 1);

        final Document doc = createDocumentWithProperties(database, properties);

        final CountDownLatch latch = new CountDownLatch(1);
        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                Log.i(TAG, "-- changed() --");
                latch.countDown();
            }
        });
        assertTrue(database.runInTransaction(new TransactionalTask() {
            @Override
            public boolean run() {
                // Update doc. The currentRevision should update, but no notification be posted (yet).
                Map<String, Object> props1 = new HashMap<String, Object>();
                props1.putAll(doc.getProperties());
                props1.put("count", 2);
                SavedRevision rev1 = null;
                try {
                    rev1 = doc.putProperties(props1);
                } catch (CouchbaseLiteException e) {
                    Log.e(Log.TAG_DATABASE, e.toString());
                    return false;
                }
                assertNotNull(rev1);
                assertEquals(doc.getCurrentRevision(), rev1);
                assertEquals(1, latch.getCount());

                // Update doc again; this should succeed, in the same manner.
                Map<String, Object> props2 = new HashMap<String, Object>();
                props2.putAll(doc.getProperties());
                props2.put("count", 3);
                SavedRevision rev2 = null;
                try {
                    rev2 = doc.putProperties(props2);
                } catch (CouchbaseLiteException e) {
                    Log.e(Log.TAG_DATABASE, e.toString());
                    return false;
                }
                assertNotNull(rev2);
                assertEquals(doc.getCurrentRevision(), rev2);
                assertEquals(1, latch.getCount());

                return true;
            }
        }));
        assertTrue(latch.await(0, TimeUnit.SECONDS));
    }

    public void testClose() throws Exception {
        // Get the database:
        Database db = manager.getDatabase(DEFAULT_TEST_DB);
        assertNotNull(db);
        // Test that the database is remembered by the manager:
        assertEquals(db, manager.getDatabase(DEFAULT_TEST_DB));
        assertTrue(manager.allOpenDatabases().contains(db));

        // Create a new document:
        Document doc = db.getDocument("doc1");
        assertNotNull(doc.putProperties(new HashMap<String, Object>()));
        // Test that the document is remebered by the database:
        assertEquals(doc, db.getCachedDocument("doc1"));

        // Close database:
        database.close();
        // The cache should be clear:
        assertNull(db.getCachedDocument("doc1"));
        // This that the database is forgotten:
        assertFalse(manager.allOpenDatabases().contains(db));
    }

    public void testAndroid2MLimit() throws Exception {
        char[] chars = new char[3 * 1024 * 1024];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        // Add a 2M+ document into the database:
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("content", content);
        Document doc = database.createDocument();
        assertNotNull(doc.putProperties(props));
        String docId = doc.getId();

        // Close and reopen the database:
        database.close();
        database = manager.getDatabase(DEFAULT_TEST_DB);

        // Try to read the document:
        doc = database.getDocument(docId);
        assertNotNull(doc);
        Map<String,Object> properties = doc.getProperties();
        assertNotNull(properties);
        assertEquals(content, properties.get("content"));
    }

    // Database_Tests.m : test18_Attachments
    public void testAttachments() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testAttachments");
        properties.put("count", 1);

        final Document doc = createDocumentWithProperties(database, properties);
        SavedRevision rev = doc.getCurrentRevision();
        assertEquals(0, rev.getAttachments().size());
        assertEquals(0, rev.getAttachmentNames().size());
        assertNull(rev.getAttachment("index.html"));

        String content = "This is a test attachments!";
        ByteArrayInputStream body = new ByteArrayInputStream(content.getBytes());
        UnsavedRevision rev2 = doc.createRevision();
        rev2.setAttachment("index.html", "text/plain; charset=utf-8", body);

        assertEquals(1, rev2.getAttachments().size());
        assertEquals(1, rev2.getAttachmentNames().size());
        assertEquals("index.html", rev2.getAttachmentNames().get(0));
        Attachment attach = rev2.getAttachment("index.html");
        assertNotNull(attach);
        assertNull(attach.getRevision()); // No revision set
        assertNull(attach.getDocument()); // No revision set
        assertEquals("index.html", attach.getName());
        assertEquals("text/plain; charset=utf-8", attach.getContentType());

        SavedRevision rev3 = rev2.save();
        assertNotNull(rev3);
        assertEquals(1, rev3.getAttachments().size());
        assertEquals(1, rev3.getAttachmentNames().size());
        assertEquals("index.html", rev3.getAttachmentNames().get(0));

        attach = rev3.getAttachment("index.html");
        assertNotNull(attach);
        assertNotNull(attach.getRevision());
        assertNotNull(attach.getDocument());
        assertEquals(doc, attach.getDocument());
        assertEquals("index.html", attach.getName());
        assertEquals("text/plain; charset=utf-8", attach.getContentType());
        assertTrue(Arrays.equals(content.getBytes(), TextUtils.read(attach.getContent())));

        // Look at the attachment's file:
        URL bodyURL = attach.getContentURL();
        if (isEncryptedAttachmentStore()) {
            assertNull(bodyURL);
        } else {
            assertNotNull(bodyURL);
            assertTrue(Arrays.equals(content.getBytes(),
                    TextUtils.read(new File(bodyURL.toURI())).getBytes()));
        }

        UnsavedRevision newRev = rev3.createRevision();
        newRev.removeAttachment(attach.getName());
        SavedRevision rev4 = newRev.save();
        assertNotNull(rev4);
        assertEquals(0, rev4.getAttachments().size());
        assertEquals(0, rev4.getAttachmentNames().size());
    }

    public void testAttachmentsWithEncryption() throws Exception {
        setEncryptedAttachmentStore(true);
        try {
            testAttachments();
        } finally {
            setEncryptedAttachmentStore(false);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/783
    public void testNonStringForTypeField() throws CouchbaseLiteException {
        // Non String as type
        List<Integer> type1 = new ArrayList();
        type1.add(0);
        type1.add(1);
        Map<String, Object> props1 = new HashMap<String, Object>();
        props1.put("key", "value");
        props1.put("type", type1);
        Document doc1 = database.createDocument();
        doc1.putProperties(props1);

        //  String as type
        String type2 = "STRING";
        Map<String, Object> props2 = new HashMap<String, Object>();
        props1.put("key", "value");
        props1.put("type", type2);
        Document doc2 = database.createDocument();
        doc2.putProperties(props1);
    }

    // ClassCastException when upgrading from 1.1 to 1.2
    // https://github.com/couchbase/couchbase-lite-android/issues/790
    public void testForceInsertWithNonStringForTypeField() throws CouchbaseLiteException {
        // Non String as type
        RevisionInternal rev = new RevisionInternal("MyDocID", "1-1111", false);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        List<Integer> type1 = Arrays.asList(0, 1);
        properties.put("type", type1);
        rev.setProperties(properties);
        List<String> history = Arrays.asList(rev.getRevID());
        database.forceInsert(rev, history, null);

        // String as type
        rev = new RevisionInternal("MyDocID", "1-ffff", false);
        properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        properties.put("type", "STRING");
        rev.setProperties(properties);
        history = Arrays.asList(rev.getRevID());
        database.forceInsert(rev, history, null);
    }

    /**
     * Missing changes in Database Change Notification
     * https://github.com/couchbase/couchbase-lite-java-core/issues/1147
     */
    public void testDatabaseChangeNotification() throws Exception {
        if (!this.isSQLiteDB())
            return;

        final int numDocs = 1000;
        final int batchSize = 20;
        final AtomicInteger totalChangesCount = new AtomicInteger(0);
        final CountDownLatch changesCountDownLatch = new CountDownLatch(1);
        final CountDownLatch createDocsCountDownLatch = new CountDownLatch(2);

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                synchronized (totalChangesCount) {
                    int total = totalChangesCount.addAndGet(event.getChanges().size());
                    Log.w(TAG, "Total changes : " + total + " > " + Thread.currentThread().getName());
                    if (total == numDocs * 2) {
                        changesCountDownLatch.countDown();
                    }
                }
            }
        });

        final Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                int numRounds = numDocs / batchSize;
                for (int i = 0; i < numRounds; i++) {
                    database.runInTransaction(new TransactionalTask() {
                        @Override
                        public boolean run() {
                            for (int j = 0; j < batchSize; j++) {
                                Document doc = database.createDocument();
                                Map<String, Object> props = new HashMap<String, Object>();
                                props.put("foo", "bar");
                                try {
                                    doc.putProperties(props);
                                } catch (CouchbaseLiteException e) {
                                    Log.e(TAG, "Error creating a document", e);
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
                }
                synchronized (createDocsCountDownLatch) {
                    createDocsCountDownLatch.countDown();
                }
            }
        }, "T1");
        t1.start();

        final Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                int numRounds = numDocs / batchSize;
                for (int i = 0; i < numRounds; i++) {
                    database.runInTransaction(new TransactionalTask() {
                        @Override
                        public boolean run() {
                            for (int j = 0; j < batchSize; j++) {
                                Document doc = database.createDocument();
                                Map<String, Object> props = new HashMap<String, Object>();
                                props.put("foo", "bar");
                                try {
                                    doc.putProperties(props);
                                } catch (CouchbaseLiteException e) {
                                    Log.e(TAG, "Error creating a document", e);
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
                }
                synchronized (createDocsCountDownLatch) {
                    createDocsCountDownLatch.countDown();
                }
            }
        }, "T2");
        t2.start();

        createDocsCountDownLatch.await();
        Log.i(TAG, "Both T1 and T2 are done creating docs : " + database.getDocumentCount());

        assertTrue(changesCountDownLatch.await(60, TimeUnit.SECONDS));
        assertEquals(numDocs * 2, totalChangesCount.get());

        // If not sleeping, sometimes not get all logging messages after the unit test got tear down.
        Thread.sleep(5000);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/742
    public void testDocumentUpdate() throws CouchbaseLiteException {

        final AtomicBoolean latch = new AtomicBoolean(false);

        // create initial document
        Document doc = database.getDocument("11111");
        Map<String, Object> dict = new HashMap<String, Object>();
        dict.put("X", "Y");
        doc.putProperties(dict);

        // set changeListener
        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                List<DocumentChange> changes = event.getChanges();
                assertEquals(1, changes.size());
                DocumentChange change = changes.get(0);
                assertNotNull(change);
                assertEquals("11111", change.getDocumentId());

                Document doc = database.getDocument("11111");
                Map<String, Object> props = doc.getUserProperties();
                assertEquals("Z", props.get("X"));

                synchronized (latch) {
                    latch.set("Z".equals(props.get("X")));
                    latch.notifyAll();
                }
            }
        });

        // update document
        dict = new HashMap<>(doc.getProperties());
        dict.put("X", "Z");
        doc.putProperties(dict);

        // wait till ChangeListener is called. timeout -> 10sec
        synchronized (latch) {
            try {
                latch.wait(10 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        assertTrue(latch.get());
    }

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test29_autoPruneOnPut
     * https://github.com/couchbase/couchbase-lite-ios/issues/1165
     */
    public void test29_autoPruneOnPut() throws CouchbaseLiteException {
        database.setMaxRevTreeDepth(5);

        RevisionInternal lastRev = null;
        List<RevisionInternal> revs = new ArrayList<RevisionInternal>();
        for (int gen = 1; gen <= 10; gen++) {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("_id", "foo");
            props.put("gen", gen);
            RevisionInternal newRev = new RevisionInternal(props);
            RevisionInternal rev = database.putRevision(newRev, lastRev == null ? null : lastRev.getRevID(), false);
            revs.add(rev);
            lastRev = rev;
        }

        // Verify that the first five revs are no longer available:
        for (int gen = 1; gen <= 10; gen++) {
            RevisionInternal rev = database.getDocument("foo", revs.get(gen - 1).getRevID(), true);
            if (gen <= 5)
                assertNull(rev);
            else
                assertNotNull(rev);
        }
    }

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test29_autoPruneOnForceInsert
     * https://github.com/couchbase/couchbase-lite-ios/issues/1165
     */
    public void test29_autoPruneOnForceInsert() throws CouchbaseLiteException {
        database.setMaxRevTreeDepth(5);

        List<RevisionInternal> revs = new ArrayList<RevisionInternal>();
        List<String> history = new ArrayList<String>();
        for (int gen = 1; gen <= 10; gen++) {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("_id", "foo");
            props.put("_rev", String.format("%d-cafebabe", gen));
            props.put("gen", gen);
            RevisionInternal rev = new RevisionInternal(props);
            database.forceInsert(rev, history, null);
            history.add(0, rev.getRevID());
            revs.add(rev);
        }

        // Verify that the first five revs are no longer available:
        for (int gen = 1; gen <= 10; gen++) {
            RevisionInternal rev = database.getDocument("foo", revs.get(gen - 1).getRevID(), true);
            if (gen <= 5)
                assertNull(rev);
            else
                assertNotNull(rev);
        }
    }

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test30_conflictAfterPrune
     * https://github.com/couchbase/couchbase-lite-ios/issues/1217
     */
    public void test30_conflictAfterPrune() throws CouchbaseLiteException {
        // Create a conflict where one branch is more than maxRevTreeDepth generations deeper than
        // the other: (#1217)
        database.setMaxRevTreeDepth(5);

        Status status = new Status();
        RevisionInternal base = database.put("robin", new HashMap<String, Object>(), null, false, null, status);
        assertNotNull(base);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put("branch", "short");
        RevisionInternal shortBranch = database.put("robin",props,base.getRevID(),false, null, status);
        assertNotNull(shortBranch);

        RevisionInternal longBranch = base;
        for(int i = 0; i < 8; i++){
            props = new HashMap<String, Object>();
            props.put("branch","long");
            longBranch = database.put("robin", props, longBranch.getRevID(), i == 0, null, status);
            assertNotNull(longBranch);
        }

        Log.i(TAG, "All revisions = %s", database.getStore().getAllRevisions("robin", false));

        List <SavedRevision> all = database.getDocument("robin").getConflictingRevisions();
        Log.i(TAG, "Conflicts = %s", all);

        assertEquals(2, all.size());
        List<String> allRevIDs = new ArrayList<String>();
        for(SavedRevision rev:all)
            allRevIDs.add(rev.getId());
        assertTrue(allRevIDs.contains(shortBranch.getRevID()));
        assertTrue(allRevIDs.contains(longBranch.getRevID()));

        RevisionInternal shortConflict = shortBranch;
        RevisionInternal longConflict = longBranch;

        // Resolve the conflict by adding to the long branch and deleting the short one:
        List<RevisionInternal> shortHistory = database.getRevisionHistory(shortBranch);
        List<RevisionInternal> longHistory = database.getRevisionHistory(longBranch);

        props = new HashMap<String, Object>();
        props.put("_deleted",true);
        shortBranch = database.put("robin", props, shortBranch.getRevID(), false, null, status);
        assertNotNull(shortBranch);
        props = new HashMap<String, Object>();
        props.put("branch", "merged");
        longBranch = database.put("robin", props, longBranch.getRevID(), false, null, status);
        assertNotNull(longBranch);

        Log.i(TAG, "After merge, all revisions = %s", database.getStore().getAllRevisions("robin", false));

        all = database.getDocument("robin").getConflictingRevisions();
        Log.i(TAG, "After merge, conflicts = %s", all);

        assertEquals(1, all.size());
        assertEquals(longBranch.getRevID(), all.get(0).getId());

        // Add the conflicting revisions back, as a pull replication might do:
        List<String> history = new ArrayList<String>();
        for(RevisionInternal rev:shortHistory)
            history.add(rev.getRevID());
        database.forceInsert(shortConflict, history, null);
        history.clear();
        for(RevisionInternal rev:longHistory)
            history.add(rev.getRevID());
        database.forceInsert(longConflict, history, null);

        // Make sure this doesn't re-create the conflict:
        all = database.getDocument("robin").getConflictingRevisions();
        Log.i(TAG, "After pull, conflicts = %s", all);
        assertEquals(1, all.size());
        assertEquals(longBranch.getRevID(), longBranch.getRevID());
    }

}
