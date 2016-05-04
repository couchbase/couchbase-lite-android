package com.couchbase.lite;

import com.couchbase.lite.store.SQLiteStore;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;

import junit.framework.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by andrey on 12/3/13.
 */
public class ApiTest extends LiteTestCaseWithDB {

    private int changeCount = 0;

    //SERVER & DOCUMENTS

    // - (void) test00_Manager in Database_Tests.m
    public void testAPIManager() throws Exception {
        Manager manager = this.manager;
        Assert.assertTrue(manager != null);
        for (String dbName : manager.getAllDatabaseNames()) {
            Database db = manager.getDatabase(dbName);
            Log.i(TAG, "Database '" + dbName + "':" + db.getDocumentCount() + " documents");
        }
        ManagerOptions options = new ManagerOptions();
        options.setReadOnly(true);

        // We want to create two different managers reading from the same directory so we
        // create LiteTestContext(false) to make sure we don't delete the directory
        Manager roManager = new Manager(getDefaultTestContext(false), options);
        Assert.assertTrue(roManager != null);

        Database db = roManager.getDatabase("foo");
        Assert.assertNull(db);
        List<String> dbNames = manager.getAllDatabaseNames();
        Assert.assertFalse(dbNames.contains("foo"));
        Assert.assertTrue(dbNames.contains(DEFAULT_TEST_DB));
    }

    // - (void) test02_DeleteDatabase in Database_Tests.m
    public void testDeleteDatabase() throws Exception {

        Database deleteme = manager.getDatabase("deleteme");
        assertTrue(deleteme.exists());
        assertTrue(new File(deleteme.getPath()).exists());
        assertTrue(new File(deleteme.getAttachmentStorePath()).exists());
        if(isSQLiteDB())
            assertTrue(new File(deleteme.getPath(), SQLiteStore.kDBFilename).exists());
        else
            assertTrue(new File(deleteme.getPath(), "db.forest.0").exists()); // hard-coded instead of ForestDBStore.kDBFilename

        deleteme.delete();
        assertFalse(deleteme.exists());
        assertFalse(new File(deleteme.getPath()).exists());
        assertFalse(new File(deleteme.getAttachmentStorePath()).exists());
        assertFalse(new File(deleteme.getPath(), SQLiteStore.kDBFilename).exists());

        deleteme.delete();  // delete again, even though already deleted
        Database deletemeFetched = manager.getExistingDatabase("deleteme");
        assertNull(deletemeFetched);
    }

    // - (void) test03_CreateDocument in Database_Tests.m
    public void testCreateDocument() throws CouchbaseLiteException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testCreateDocument");
        properties.put("tag", 1337);

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, properties);
        String docID = doc.getId();
        assertTrue("Invalid doc ID: " + docID, docID.length() > 10);
        String currentRevisionID = doc.getCurrentRevisionId();
        assertTrue("Invalid doc revision: " + docID, currentRevisionID.length() > 10);
        assertEquals(doc.getUserProperties(), properties);
        assertEquals(db.getDocument(docID), doc);

        db.clearDocumentCache();// so we can load fresh copies

        Document doc2 = db.getExistingDocument(docID);
        assertEquals(doc2.getId(), docID);
        assertEquals(doc2.getCurrentRevisionId(), currentRevisionID);

        assertNull(db.getExistingDocument("b0gus"));
    }

    public void testDatabaseCompaction() throws Exception {

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testDatabaseCompaction");
        properties.put("tag", 1337);


        Document doc = createDocumentWithProperties(database, properties);
        SavedRevision rev1 = doc.getCurrentRevision();

        Map<String, Object> properties2 = new HashMap<String, Object>(properties);
        properties2.put("tag", 4567);

        SavedRevision rev2 = rev1.createRevision(properties2);
        database.compact();

        Document fetchedDoc = database.getDocument(doc.getId());
        List<SavedRevision> revisions = fetchedDoc.getRevisionHistory();
        for (SavedRevision revision : revisions) {
            if (revision.getId().equals(rev1)) {
                assertFalse(revision.arePropertiesAvailable());
            }
            if (revision.getId().equals(rev2)) {
                assertTrue(revision.arePropertiesAvailable());
            }
        }
    }

    public void testDocumentCache() throws Exception {

        Database db = startDatabase();
        Document doc = db.createDocument();
        UnsavedRevision rev1 = doc.createRevision();
        Map<String, Object> rev1Properties = new HashMap<String, Object>();
        rev1Properties.put("foo", "bar");
        rev1.setUserProperties(rev1Properties);
        SavedRevision savedRev1 = rev1.save();
        String documentId = savedRev1.getDocument().getId();

        // getting the document puts it in cache
        Document docRev1 = db.getExistingDocument(documentId);

        UnsavedRevision rev2 = docRev1.createRevision();
        Map<String, Object> rev2Properties = rev2.getProperties();
        rev2Properties.put("foo", "baz");
        rev2.setUserProperties(rev2Properties);
        rev2.save();

        Document docRev2 = db.getExistingDocument(documentId);
        assertEquals("baz", docRev2.getProperty("foo"));
    }

    // - (void) test05_CreateRevisions in Database_Tests.m
    public void testCreateRevisions() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, properties);
        assertFalse(doc.isDeleted());
        SavedRevision rev1 = doc.getCurrentRevision();
        assertTrue(rev1.getId().startsWith("1-"));
        assertEquals(1, rev1.getSequence());
        assertEquals(0, rev1.getAttachments().size());

        // Test -createRevisionWithProperties:
        Map<String, Object> properties2 = new HashMap<String, Object>(properties);
        properties2.put("tag", 4567);

        SavedRevision rev2 = rev1.createRevision(properties2);
        assertNotNull("Put failed", rev2);

        assertTrue("Document revision ID is still " + doc.getCurrentRevisionId(),
                doc.getCurrentRevisionId().startsWith("2-"));

        assertEquals(rev2.getId(), doc.getCurrentRevisionId());
        assertNotNull(rev2.arePropertiesAvailable());
        assertEquals(rev2.getUserProperties(), properties2);
        assertEquals(rev2.getDocument(), doc);
        assertEquals(rev2.getProperty("_id"), doc.getId());
        assertEquals(rev2.getProperty("_rev"), rev2.getId());

        // Test -createRevision:
        UnsavedRevision newRev = rev2.createRevision();
        assertNull(newRev.getId());
        assertEquals(newRev.getParent(), rev2);
        assertEquals(newRev.getParentId(), rev2.getId());
        assertEquals(doc.getCurrentRevision(), rev2);
        assertFalse(doc.isDeleted());
        List<SavedRevision> listRevs = new ArrayList<SavedRevision>();
        listRevs.add(rev1);
        listRevs.add(rev2);

        assertEquals(newRev.getRevisionHistory(), listRevs);
        assertEquals(newRev.getProperties(), rev2.getProperties());
        assertEquals(newRev.getUserProperties(), rev2.getUserProperties());

        Map<String, Object> userProperties = new HashMap<String, Object>();
        userProperties.put("because", "NoSQL");

        newRev.setUserProperties(userProperties);
        assertEquals(newRev.getUserProperties(), userProperties);

        Map<String, Object> expectProperties = new HashMap<String, Object>();
        expectProperties.put("because", "NoSQL");
        expectProperties.put("_id", doc.getId());
        expectProperties.put("_rev", rev2.getId());

        assertEquals(newRev.getProperties(), expectProperties);
        SavedRevision rev3 = newRev.save();
        assertNotNull(rev3);
        assertEquals(rev3.getUserProperties(), newRev.getUserProperties());
    }

    // - (void) test07_CreateNewRevisions in Database_Tests.m
    public void testCreateNewRevisions() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        Database db = startDatabase();
        Document doc = db.createDocument();
        UnsavedRevision newRev = doc.createRevision();

        Document newRevDocument = newRev.getDocument();
        assertEquals(doc, newRevDocument);
        assertEquals(db, newRev.getDatabase());
        assertNull(newRev.getParentId());
        assertNull(newRev.getParent());

        Map<String, Object> expectProperties = new HashMap<String, Object>();
        expectProperties.put("_id", doc.getId());
        assertEquals(expectProperties, newRev.getProperties());
        assertTrue(!newRev.isDeletion());
        assertEquals(newRev.getSequence(), 0);

        //ios support another approach to set properties::
        //newRev.([@"testName"] = @"testCreateRevisions";
        //newRev[@"tag"] = @1337;
        newRev.setUserProperties(properties);
        assertEquals(newRev.getUserProperties(), properties);

        SavedRevision rev1 = newRev.save();
        assertNotNull("Save 1 failed", rev1);
        assertEquals(doc.getCurrentRevision(), rev1);
        assertNotNull(rev1.getId().startsWith("1-"));
        assertEquals(1, rev1.getSequence());
        assertNull(rev1.getParentId());
        assertNull(rev1.getParent());

        newRev = rev1.createRevision();
        newRevDocument = newRev.getDocument();
        assertEquals(doc, newRevDocument);
        assertEquals(db, newRev.getDatabase());
        assertEquals(rev1.getId(), newRev.getParentId());
        assertEquals(rev1, newRev.getParent());
        assertEquals(rev1.getProperties(), newRev.getProperties());
        assertEquals(rev1.getUserProperties(), newRev.getUserProperties());
        assertNotNull(!newRev.isDeletion());

        // we can't add/modify one property as on ios. need  to add separate method?
        // newRev[@"tag"] = @4567;
        properties.put("tag", 4567);
        newRev.setUserProperties(properties);
        SavedRevision rev2 = newRev.save();
        assertNotNull("Save 2 failed", rev2);
        assertEquals(doc.getCurrentRevision(), rev2);
        assertNotNull(rev2.getId().startsWith("2-"));
        assertEquals(2, rev2.getSequence());
        assertEquals(rev1.getId(), rev2.getParentId());
        assertEquals(rev1, rev2.getParent());

        assertNotNull("Document revision ID is still " + doc.getCurrentRevisionId(),
                doc.getCurrentRevisionId().startsWith("2-"));

        // Add a deletion/tombstone revision:
        newRev = doc.createRevision();
        assertEquals(rev2.getId(), newRev.getParentId());
        assertEquals(rev2, newRev.getParent());
        newRev.setIsDeletion(true);
        SavedRevision rev3 = newRev.save();
        assertNotNull("Save 3 failed", rev3);
        assertNotNull("Unexpected revID " + rev3.getId(), rev3.getId().startsWith("3-"));
        assertEquals(3, rev3.getSequence());
        assertTrue(rev3.isDeletion());

        assertTrue(doc.isDeleted());
        assertNull(doc.getCurrentRevision());
        List<SavedRevision> leafRevs = new ArrayList<SavedRevision>();
        leafRevs.add(rev3);
        assertEquals(doc.getLeafRevisions(), leafRevs);
        db.getDocumentCount();
        Document doc2 = db.getDocument(doc.getId());
        assertEquals(doc, doc2);
        assertNull(db.getExistingDocument(doc.getId()));

    }

    //API_SaveMultipleDocuments on IOS
    //API_SaveMultipleUnsavedDocuments on IOS
    //API_DeleteMultipleDocuments commented on IOS

    // - (void) test09_DeleteDocument in Database_Tests.m
    public void testDeleteDocument() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testDeleteDocument");

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, properties);
        assertTrue(!doc.isDeleted());
        assertTrue(!doc.getCurrentRevision().isDeletion());
        assertTrue(doc.delete());
        assertTrue(doc.isDeleted());
        assertNull(doc.getCurrentRevision());
    }

    // - (void) test10_PurgeDocument in Database_Tests.m
    public void testPurgeDocument() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testPurgeDocument");

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, properties);
        assertNotNull(doc);
        doc.purge();

        Document redoc = db.getCachedDocument(doc.getId());
        assertNull(redoc);
    }

    public void testDeleteDocumentViaTombstoneRevision() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testDeleteDocument");

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, properties);
        assertTrue(!doc.isDeleted());
        assertTrue(!doc.getCurrentRevision().isDeletion());


        Map<String, Object> props = new HashMap<String, Object>(doc.getProperties());
        props.put("_deleted", true);
        SavedRevision deletedRevision = doc.putProperties(props);

        assertTrue(doc.isDeleted());
        assertTrue(deletedRevision.isDeletion());
        assertNull(doc.getCurrentRevision());

    }

    // - (void) test12_AllDocuments in Database_Tests.m
    public void testAllDocuments() throws Exception {
        Database db = startDatabase();
        int kNDocs = 5;
        createDocuments(db, kNDocs);

        // clear the cache so all documents/revisions will be re-fetched:
        db.clearDocumentCache();
        Log.i(TAG, "----- all documents -----");

        Query query = db.createAllDocumentsQuery();
        //query.prefetch = YES;
        Log.i(TAG, "Getting all documents: " + query);

        QueryEnumerator rows = query.run();
        assertEquals(rows.getCount(), kNDocs);
        int n = 0;
        for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
            QueryRow row = it.next();
            Log.i(TAG, "    --> " + row);
            Document doc = row.getDocument();
            assertNotNull("Couldn't get doc from query", doc);
            assertNotNull("QueryRow should have preloaded revision contents",
                    doc.getCurrentRevision().arePropertiesAvailable());
            Log.i(TAG, "        Properties =" + doc.getProperties());
            assertNotNull("Couldn't get doc properties", doc.getProperties());
            assertEquals(doc.getProperty("testName"), "testDatabase");
            n++;
        }
        assertEquals(n, kNDocs);
    }

    // - (void) test12_AllDocumentsPrefixMatchLevel in Database_Tests.m

    // - (void) test12_AllDocumentsBySequence in Database_Tests.m
    public void test12_AllDocumentsBySequence() throws Exception {
        Database db = startDatabase();
        int kNDocs = 10;
        createDocuments(db, kNDocs);

        // clear the cache so all documents/revisions will be re-fetched:
        db.clearDocumentCache();

        Query query = db.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.BY_SEQUENCE);
        QueryEnumerator rows = query.run();
        long n = 0;
        for (QueryRow row : rows) {
            n++;
            Document doc = row.getDocument();
            assertNotNull(doc);
            assertEquals(n, doc.getCurrentRevision().getSequence());
        }
        assertEquals(n, kNDocs);

        query = db.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.BY_SEQUENCE);
        query.setStartKey(3);
        rows = query.run();
        n = 2;
        for (QueryRow row : rows) {
            n++;
            Document doc = row.getDocument();
            assertNotNull(doc);
            assertEquals(n, doc.getCurrentRevision().getSequence());
        }
        assertEquals(kNDocs, n);

        query = db.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.BY_SEQUENCE);
        query.setEndKey(6);
        rows = query.run();
        n = 0;
        for (QueryRow row : rows) {
            n++;
            Document doc = row.getDocument();
            assertNotNull(doc);
            assertEquals(n, doc.getCurrentRevision().getSequence());
        }
        assertEquals(6, n);

        query = db.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.BY_SEQUENCE);
        query.setStartKey(3);
        query.setEndKey(6);
        query.setInclusiveStart(false);
        query.setInclusiveEnd(false);
        rows = query.run();
        n = 3;
        for (QueryRow row : rows) {
            n++;
            Document doc = row.getDocument();
            assertNotNull(doc);
            assertEquals(n, doc.getCurrentRevision().getSequence());
        }
        assertEquals(5, n);
    }

    // - (void) test13_LocalDocs in Database_Tests.m
    public void testLocalDocs() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");

        Database db = startDatabase();

        Map<String, Object> props = db.getExistingLocalDocument("dock");
        assertNull(props);
        assertNotNull("Couldn't put new local doc", db.putLocalDocument("dock", properties));
        props = db.getExistingLocalDocument("dock");
        assertEquals(props.get("foo"), "bar");


        Map<String, Object> newProperties = new HashMap<String, Object>();
        newProperties.put("FOOO", "BARRR");

        assertNotNull("Couldn't update local doc", db.putLocalDocument("dock", newProperties));
        props = db.getExistingLocalDocument("dock");
        assertNull(props.get("foo"));
        assertEquals(props.get("FOOO"), "BARRR");

        assertNotNull("Couldn't delete local doc", db.deleteLocalDocument("dock"));
        props = db.getExistingLocalDocument("dock");
        assertNull(props);

        try{
            db.deleteLocalDocument("dock");
            fail("Should throw Exception");
        }catch(CouchbaseLiteException ex){
            assertEquals(Status.NOT_FOUND, ex.getCBLStatus().getCode());
        }
    }

    // HISTORY

    public void testHistory() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "test06_History");
        properties.put("tag", 1);

        Database db = startDatabase();

        Document doc = createDocumentWithProperties(db, properties);
        String rev1ID = doc.getCurrentRevisionId();
        Log.i(TAG, "1st revision: " + rev1ID);
        assertNotNull("1st revision looks wrong: " + rev1ID, rev1ID.startsWith("1-"));
        assertEquals(doc.getUserProperties(), properties);
        properties = new HashMap<String, Object>();
        properties.putAll(doc.getProperties());
        properties.put("tag", 2);
        assertNotNull(!properties.equals(doc.getProperties()));
        assertNotNull(doc.putProperties(properties));
        String rev2ID = doc.getCurrentRevisionId();
        Log.i(TAG, "rev2ID" + rev2ID);
        assertNotNull("2nd revision looks wrong:" + rev2ID, rev2ID.startsWith("2-"));

        List<SavedRevision> revisions = doc.getRevisionHistory();
        Log.i(TAG, "Revisions = " + revisions);
        assertEquals(revisions.size(), 2);

        SavedRevision rev1 = revisions.get(0);
        assertEquals(rev1.getId(), rev1ID);
        Map<String, Object> gotProperties = rev1.getProperties();
        assertEquals(1, gotProperties.get("tag"));

        SavedRevision rev2 = revisions.get(1);
        assertEquals(rev2.getId(), rev2ID);
        assertEquals(rev2, doc.getCurrentRevision());
        gotProperties = rev2.getProperties();
        assertEquals(2, gotProperties.get("tag"));

        List<SavedRevision> tmp = new ArrayList<SavedRevision>();
        tmp.add(rev2);
        assertEquals(doc.getConflictingRevisions(), tmp);
        assertEquals(doc.getLeafRevisions(), tmp);

    }

    public void testHistoryAfterDocDeletion() throws Exception {

        Map<String, Object> properties = new HashMap<String, Object>();
        String docId = "testHistoryAfterDocDeletion";
        properties.put("tag", 1);

        Database db = startDatabase();
        Document doc = db.getDocument(docId);
        assertEquals(docId, doc.getId());
        doc.putProperties(properties);

        String revID = doc.getCurrentRevisionId();
        for (int i = 2; i < 6; i++) {
            properties.put("tag", i);
            properties.put("_rev", revID);
            doc.putProperties(properties);
            revID = doc.getCurrentRevisionId();
            Log.i(TAG, i + " revision: " + revID);
            assertTrue("revision is not correct:" + revID + ", should be with prefix " + i + "-",
                    revID.startsWith(String.valueOf(i) + "-"));
            assertEquals("Doc Id is not correct ", docId, doc.getId());
        }

        // now delete the doc and clear it from the cache so we
        // make sure we are reading a fresh copy
        doc.delete();
        database.clearDocumentCache();

        // get doc from db with same ID as before, and the current rev should be null since the
        // last update was a deletion
        Document docPostDelete = db.getDocument(docId);
        assertNull(docPostDelete.getCurrentRevision());

        // save a new revision
        properties = new HashMap<String, Object>();
        properties.put("tag", 6);
        UnsavedRevision newRevision = docPostDelete.createRevision();
        newRevision.setProperties(properties);
        SavedRevision newSavedRevision = newRevision.save();

        // make sure the current revision of doc matches the rev we just saved
        assertEquals(newSavedRevision, docPostDelete.getCurrentRevision());

        // make sure the rev id is 7-
        assertTrue(docPostDelete.getCurrentRevisionId().startsWith("7-"));
    }

    /**
     * in Database_Tests.m
     * - (void) test14_Conflict
     */
    public void testConflict() throws Exception {
        Map<String, Object> prop = new HashMap<String, Object>();
        prop.put("foo", "bar");

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, prop);
        SavedRevision rev1 = doc.getCurrentRevision();

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(doc.getProperties());
        properties.put("tag", 2);
        SavedRevision rev2a = doc.putProperties(properties);

        properties = new HashMap<String, Object>();
        properties.putAll(rev1.getProperties());
        properties.put("tag", 3);
        UnsavedRevision newRev = rev1.createRevision();
        newRev.setProperties(properties);
        boolean allowConflict = true;
        SavedRevision rev2b = newRev.save(allowConflict);
        assertNotNull("Failed to create a a conflict", rev2b);

        // NOTE: getConflictingRevisions() and getLeafRevisions() => order is not guaranteed
        List<SavedRevision> expectedConfRevs1 = new ArrayList<SavedRevision>();
        expectedConfRevs1.add(rev2a);
        expectedConfRevs1.add(rev2b);

        List<SavedRevision> expectedConfRevs2 = new ArrayList<SavedRevision>();
        expectedConfRevs2.add(rev2b);
        expectedConfRevs2.add(rev2a);

        List<SavedRevision> actualConfRevs = doc.getConflictingRevisions();
        assertTrue(expectedConfRevs1.equals(actualConfRevs) || expectedConfRevs2.equals(actualConfRevs));
        List<SavedRevision> actualLeafRevs = doc.getLeafRevisions();
        assertTrue(expectedConfRevs1.equals(actualLeafRevs) || expectedConfRevs2.equals(actualLeafRevs));

        SavedRevision defaultRev, otherRev;
        if (rev2a.getId().compareTo(rev2b.getId()) > 0) {
            defaultRev = rev2a;
            otherRev = rev2b;
        } else {
            defaultRev = rev2b;
            otherRev = rev2a;
        }
        assertEquals(doc.getCurrentRevision(), defaultRev);

        Query query = db.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.SHOW_CONFLICTS);
        QueryEnumerator rows = query.run();
        assertEquals(rows.getCount(), 1);
        QueryRow row = rows.getRow(0);

        List<SavedRevision> revs = row.getConflictingRevisions();
        assertEquals(revs.size(), 2);
        assertEquals(revs.get(0), defaultRev);
        assertEquals(revs.get(1), otherRev);

    }

    public void testCreateIdenticalParentContentRevisions() throws Exception {
        Document doc = database.createDocument();
        SavedRevision rev = doc.createRevision().save();

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");

        UnsavedRevision unsavedRev1 = rev.createRevision();
        unsavedRev1.setProperties(properties);
        SavedRevision savedRev1 = unsavedRev1.save(true);
        assertNotNull(savedRev1);

        UnsavedRevision unsavedRev2 = rev.createRevision();
        unsavedRev2.setProperties(properties);
        SavedRevision savedRev2 = unsavedRev2.save(true);
        assertNotNull(savedRev2);

        assertEquals(savedRev1.getId(), savedRev2.getId());

        List<SavedRevision> conflicts = doc.getConflictingRevisions();
        assertEquals(1, conflicts.size());

        Query query = database.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.ONLY_CONFLICTS);
        QueryEnumerator result = query.run();
        assertNotNull(result);
        assertEquals(0, result.getCount());
    }

    //ATTACHMENTS

    public void testAttachments() throws Exception, IOException {

        String attachmentName = "index.html";
        String content = "This is a test attachment!";

        Document doc = createDocWithAttachment(database, attachmentName, content);

        UnsavedRevision newRev = doc.getCurrentRevision().createRevision();
        newRev.removeAttachment(attachmentName);
        SavedRevision rev4 = newRev.save();
        assertNotNull(rev4);
        assertEquals(0, rev4.getAttachmentNames().size());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/132
     */
    public void testUpdateDocWithAttachments() throws Exception, IOException {

        String attachmentName = "index.html";
        String content = "This is a test attachment!";

        Document doc = createDocWithAttachment(database, attachmentName, content);
        SavedRevision latestRevision = doc.getCurrentRevision();

        Map<String, Object> propertiesUpdated = new HashMap<String, Object>();
        propertiesUpdated.put("propertiesUpdated", "testUpdateDocWithAttachments");
        UnsavedRevision newUnsavedRevision = latestRevision.createRevision();
        newUnsavedRevision.setUserProperties(propertiesUpdated);
        SavedRevision newSavedRevision = newUnsavedRevision.save();
        assertNotNull(newSavedRevision);
        assertEquals(1, newSavedRevision.getAttachmentNames().size());

        Attachment fetched = doc.getCurrentRevision().getAttachment(attachmentName);
        InputStream is = fetched.getContent();
        byte[] attachmentBytes = TextUtils.read(is);
        is.close();
        assertEquals(content, new String(attachmentBytes));
        assertNotNull(fetched);
    }

    //CHANGE TRACKING

    public void testChangeTracking() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(1);

        Database db = startDatabase();
        db.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                doneSignal.countDown();
            }
        });

        createDocumentsAsync(db, 5);
        // We expect that the changes reported by the server won't be notified, because those
        // revisions are already cached in memory.
        boolean success = doneSignal.await(300, TimeUnit.SECONDS);
        assertTrue(success);
        assertEquals(5, db.getLastSequenceNumber());

    }

    //VIEWS

    public void testCreateView() throws Exception {
        Database db = startDatabase();
        View view = db.getView("vu");
        assertNotNull(view);
        assertEquals(db, view.getDatabase());
        assertEquals("vu", view.getName());
        assertNull(view.getMap());
        assertNull(view.getReduce());

        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Log.w(TAG, "[testCreateView().map()] START");
                Log.w(TAG, "[testCreateView().map()] key=" + document.get("sequence").toString());
                emitter.emit(document.get("sequence"), null);
                //emitter.emit(document.get("sequence").toString(), null);
                Log.w(TAG, "[testCreateView().map()] END");
            }
        }, "1");

        //assertNotNull(view.getMap() != null);
        assertNotNull(view.getMap());

        int kNDocs = 50;
        createDocuments(db, kNDocs);

        Query query = view.createQuery();
        assertEquals(db, query.getDatabase());
        query.setStartKey(23);
        query.setEndKey(33);
        QueryEnumerator rows = query.run();
        assertNotNull(rows);
        assertEquals(11, rows.getCount());

        int expectedKey = 23;
        for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
            QueryRow row = it.next();
            Object key = row.getKey();
            if(key instanceof Double)
                key = ((Double)key).intValue();
            assertEquals(expectedKey, key);
            assertEquals(expectedKey + 1, row.getSequenceNumber());
            ++expectedKey;
        }
    }

    //API_RunSlowView commented on IOS

    public void testValidation() throws Exception {
        Database db = startDatabase();
        db.setValidation("uncool", new Validator() {
            @Override
            public void validate(Revision newRevision, ValidationContext context) {
                {
                    if (newRevision.getProperty("groovy") == null) {
                        context.reject("uncool");
                    }
                }
            }
        });

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("groovy", "right on");
        properties.put("foo", "bar");

        Document doc = db.createDocument();
        assertNotNull(doc.putProperties(properties));

        properties = new HashMap<String, Object>();
        properties.put("foo", "bar");
        doc = db.createDocument();
        try {
            assertNull(doc.putProperties(properties));
        } catch (CouchbaseLiteException e) {
            //TODO
            assertEquals(e.getCBLStatus().getCode(), Status.FORBIDDEN);
//            assertEquals(e.getLocalizedMessage(), "forbidden: uncool"); //TODO: Not hooked up yet
        }

    }


    public void testViewWithLinkedDocs() throws Exception {
        Database db = startDatabase();
        int kNDocs = 50;
        Document[] docs = new Document[50];

        String lastDocID = "";
        for (int i = 0; i < kNDocs; i++) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("sequence", i);
            properties.put("prev", lastDocID);
            Document doc = createDocumentWithProperties(db, properties);
            docs[i] = doc;
            lastDocID = doc.getId();
        }

        Query query = db.slowQuery(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                emitter.emit(document.get("sequence"), new Object[]{"_id", document.get("prev")});

            }
        });

        query.setStartKey(23);
        query.setEndKey(33);
        query.setPrefetch(true);
        QueryEnumerator rows = query.run();
        assertNotNull(rows);
        assertEquals(rows.getCount(), 11);

        int rowNumber = 23;


        for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
            QueryRow row = it.next();
            assertEquals(rowNumber, ((Number)row.getKey()).intValue());
            Document prevDoc = docs[rowNumber];
            assertEquals(row.getDocumentId(), prevDoc.getId());
            assertEquals(row.getDocument(), prevDoc);
            ++rowNumber;
        }
    }


    public void testLiveQueryRun() throws Exception {
        runLiveQuery("run");
    }

    public void testLiveQueryStart() throws Exception {
        runLiveQuery("start");
    }

    public void testLiveQueryStartWaitForRows() throws Exception {
        runLiveQuery("startWaitForRows");
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/84
     */
    public void testLiveQueryStop() throws Exception {

        final int kNDocs = 50;

        final CountDownLatch doneSignal = new CountDownLatch(1);

        final Database db = startDatabase();

        // run a live query
        View view = db.getView("vu");
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");
        final LiveQuery query = view.createQuery().toLiveQuery();

        final AtomicInteger atomicInteger = new AtomicInteger(0);

        // install a change listener which decrements countdown latch when it sees a new
        // key from the list of expected keys
        final LiveQuery.ChangeListener changeListener = new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                Log.d(TAG, "changed called, atomicInteger.incrementAndGet");
                atomicInteger.incrementAndGet();
                assertNull(event.getError());
                if (event.getRows().getCount() == kNDocs) {
                    doneSignal.countDown();
                }
            }
        };
        query.addChangeListener(changeListener);

        // create the docs that will cause the above change listener to decrement countdown latch
        Log.d(Database.TAG, "testLiveQueryStop: createDocumentsAsync()");

        createDocumentsAsync(db, kNDocs);

        Log.d(Database.TAG, "testLiveQueryStop: calling query.start()");
        query.start();

        // wait until the livequery is called back with kNDocs docs
        Log.d(Database.TAG, "testLiveQueryStop: waiting for doneSignal");
        boolean success = doneSignal.await(45, TimeUnit.SECONDS);
        assertTrue(success);

        Log.d(Database.TAG, "testLiveQueryStop: waiting for query.stop()");
        query.stop();

        // after stopping the query, we should not get any more livequery callbacks, even
        // if we add more docs to the database and pause (to give time for potential callbacks)

        int numTimesCallbackCalled = atomicInteger.get();
        Log.d(Database.TAG, "testLiveQueryStop: numTimesCallbackCalled is: " +
                numTimesCallbackCalled + ".  Now adding docs");

        for (int i = 0; i < 10; i++) {
            createDocuments(db, 1);
            Log.d(Database.TAG, "testLiveQueryStop: add a document.  atomicInteger.get(): " +
                    atomicInteger.get());
            assertEquals(numTimesCallbackCalled, atomicInteger.get());
            Thread.sleep(100);
        }
        assertEquals(numTimesCallbackCalled, atomicInteger.get());
    }

    public void testLiveQueryRestart() throws Exception {

        // kick something off that will s

    }

    public void runLiveQuery(String methodNameToCall) throws Exception {

        final Database db = startDatabase();
        final CountDownLatch doneSignal = new CountDownLatch(11);
        // 11 corresponds to startKey=23; endKey=33

        // run a live query
        View view = db.getView("vu");
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");
        final LiveQuery query = view.createQuery().toLiveQuery();
        query.setStartKey(23);
        query.setEndKey(33);
        Log.i(TAG, "Created  " + query);

        // these are the keys that we expect to see in the livequery change listener callback
        final Set<Integer> expectedKeys = new HashSet<Integer>();
        for (int i = 23; i < 34; i++) {
            expectedKeys.add(i);
        }

        // install a change listener which decrements countdown latch when it sees a new
        // key from the list of expected keys
        final LiveQuery.ChangeListener changeListener = new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                QueryEnumerator rows = event.getRows();
                for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
                    QueryRow row = it.next();
                    if (expectedKeys.contains(((Number)row.getKey()).intValue())) {
                        expectedKeys.remove(((Number)row.getKey()).intValue());
                        doneSignal.countDown();
                    }

                }
            }
        };
        query.addChangeListener(changeListener);

        // create the docs that will cause the above change listener to decrement countdown latch
        int kNDocs = 50;
        createDocumentsAsync(db, kNDocs);

        if (methodNameToCall.equals("start")) {
            // start the livequery running asynchronously
            query.start();
        } else if (methodNameToCall.equals("startWaitForRows")) {
            query.start();
            query.waitForRows();
        } else {
            assertNull(query.getRows());
            query.run();  // this will block until the query completes
            assertNotNull(query.getRows());
        }

        // wait for the doneSignal to be finished
        boolean success = doneSignal.await(300, TimeUnit.SECONDS);
        assertTrue("Done signal timed out, live query never ran", success);

        // stop the livequery since we are done with it
        query.removeChangeListener(changeListener);
        query.stop();

        // Workaround for https://github.com/couchbase/couchbase-lite-android/issues/613
        try {
            Thread.sleep(1 * 1000);
        } catch (Exception e) {
        }
    }

    public void testAsyncViewQuery() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(1);
        final Database db = startDatabase();
        View view = db.getView("vu");
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");

        int kNDocs = 50;
        createDocuments(db, kNDocs);

        Query query = view.createQuery();
        query.setStartKey(23);
        query.setEndKey(33);

        query.runAsync(new Query.QueryCompleteListener() {
            @Override
            public void completed(QueryEnumerator rows, Throwable error) {
                Log.i(TAG, "Async query finished!");
                assertNotNull(rows);
                assertNull(error);
                assertEquals(rows.getCount(), 11);

                int expectedKey = 23;
                for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
                    QueryRow row = it.next();
                    assertEquals(row.getDocument().getDatabase(), db);
                    Object key = row.getKey();
                    if (key instanceof Double)
                        key = ((Double) key).intValue();
                    assertEquals(expectedKey, key);
                    ++expectedKey;
                }
                doneSignal.countDown();

            }
        });

        Log.i(TAG, "Waiting for async query to finish...");
        boolean success = doneSignal.await(120, TimeUnit.SECONDS);
        assertTrue("Done signal timed out, async query never ran", success);

    }

    /**
     * Make sure that a database's map/reduce functions are shared with the shadow database instance
     * running in the background server.
     */
    public void testSharedMapBlocks() throws Exception {
        Manager mgr = createManager(getTestContext("API_SharedMapBlocks", true));
        Database db = mgr.getDatabase("db");
        db.open();
        db.setFilter("phil", new ReplicationFilter() {
            @Override
            public boolean filter(SavedRevision revision, Map<String, Object> params) {
                return true;
            }
        });

        db.setValidation("val", new Validator() {
            @Override
            public void validate(Revision newRevision, ValidationContext context) {
            }
        });

        View view = db.getView("view");
        boolean ok = view.setMapReduce(new Mapper() {
                                           @Override
                                           public void map(Map<String, Object> document,
                                                           Emitter emitter) {

                                           }
                                       }, new Reducer() {
                                           @Override
                                           public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                                               return null;
                                           }
                                       }, "1"
        );

        assertNotNull("Couldn't set map/reduce", ok);

        final Mapper map = view.getMap();
        final Reducer reduce = view.getReduce();
        final ReplicationFilter filter = db.getFilter("phil");
        final Validator validation = db.getValidation("val");

        Future result = mgr.runAsync("db", new AsyncTask() {
            @Override
            public void run(Database database) {
                assertNotNull(database);
                View serverView = database.getExistingView("view");
                assertNotNull(serverView);
                assertEquals(database.getFilter("phil"), filter);
                assertEquals(database.getValidation("val"), validation);
                assertEquals(serverView.getMap(), map);
                assertEquals(serverView.getReduce(), reduce);
            }
        });
        result.get();  // blocks until async task has run
        db.close();
        mgr.close();
    }

    public void testChangeUUID() throws Exception {
        Manager mgr = createManager(getTestContext("ChangeUUID", true));
        Database db = mgr.getDatabase("db");
        db.open();
        String pub = db.publicUUID();
        String priv = db.privateUUID();
        assertTrue(pub.length() > 10);
        assertTrue(priv.length() > 10);

        assertTrue("replaceUUIDs failed", db.replaceUUIDs());
        assertFalse(pub.equals(db.publicUUID()));
        assertFalse(priv.equals(db.privateUUID()));
        mgr.close();
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/220
     */
    public void testMultiDocumentUpdate() throws Exception {

        final int numberOfDocuments = 10;
        final int numberOfUpdates = 10;
        final Document[] docs = new Document[numberOfDocuments];

        for (int j = 0; j < numberOfDocuments; j++) {

            Map<String, Object> prop = new HashMap<String, Object>();
            prop.put("foo", "bar");
            prop.put("toogle", true);
            Document document = createDocumentWithProperties(database, prop);
            docs[j] = document;
        }

        final AtomicInteger numDocsUpdated = new AtomicInteger(0);
        final AtomicInteger numExceptions = new AtomicInteger(0);

        for (int j = 0; j < numberOfDocuments; j++) {

            Document doc = docs[j];

            for (int k = 0; k < numberOfUpdates; k++) {
                Map<String, Object> contents = new HashMap(doc.getProperties());

                Boolean wasChecked = (Boolean) contents.get("toogle");

                //toggle value of check property
                contents.put("toogle", !wasChecked);

                try {
                    doc.putProperties(contents);
                    numDocsUpdated.incrementAndGet();
                } catch (CouchbaseLiteException cblex) {
                    Log.e(TAG, "Document update failed", cblex);
                    numExceptions.incrementAndGet();
                }
            }
        }

        assertEquals(numberOfDocuments * numberOfUpdates, numDocsUpdated.get());
        assertEquals(0, numExceptions.get());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/220
     */
    public void failingTestMultiDocumentUpdateInTransaction() throws Exception {

        final int numberOfDocuments = 10;
        final int numberOfUpdates = 10;
        final Document[] docs = new Document[numberOfDocuments];

        database.runInTransaction(new TransactionalTask() {
            @Override
            public boolean run() {
                for (int j = 0; j < numberOfDocuments; j++) {

                    Map<String, Object> prop = new HashMap<String, Object>();
                    prop.put("foo", "bar");
                    prop.put("toogle", true);
                    Document document = createDocumentWithProperties(database, prop);
                    docs[j] = document;
                }
                return true;
            }
        });

        final AtomicInteger numDocsUpdated = new AtomicInteger(0);
        final AtomicInteger numExceptions = new AtomicInteger(0);

        database.runInTransaction(new TransactionalTask() {
            @Override
            public boolean run() {
                for (int j = 0; j < numberOfDocuments; j++) {

                    Document doc = docs[j];
                    SavedRevision lastSavedRevision = null;

                    for (int k = 0; k < numberOfUpdates; k++) {

                        if (lastSavedRevision != null) {
                            assertEquals(lastSavedRevision.getId(), doc.getCurrentRevisionId());
                        }

                        Map<String, Object> contents = new HashMap(doc.getProperties());
                        Document docLatest = database.getDocument(doc.getId());

                        Boolean wasChecked = (Boolean) contents.get("toogle");

                        //toggle value of check property
                        contents.put("toogle", !wasChecked);

                        try {
                            lastSavedRevision = doc.putProperties(contents);
                            numDocsUpdated.incrementAndGet();

                        } catch (CouchbaseLiteException cblex) {
                            Log.e(TAG, "Document update failed", cblex);
                            numExceptions.incrementAndGet();
                        }
                    }
                }
                return true;
            }
        });

        assertEquals(numberOfDocuments * numberOfUpdates, numDocsUpdated.get());
        assertEquals(0, numExceptions.get());
    }


    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/685#issuecomment-158917064
     */
    public void testRevPruning() throws IOException, CouchbaseLiteException {
        final int DOC_COUNT = 10;
        final int REV_COUNT = 20;
        final int MAX_REV_TREE_DEPTH = 10;

        database.setMaxRevTreeDepth(MAX_REV_TREE_DEPTH);

        for (int j = 0; j < DOC_COUNT; j++) {
            final Document document = database.createDocument();
            for (int i = 0; i < REV_COUNT; i++) {
                final String value = "rev " + i;
                document.update(new Document.DocumentUpdater() {
                    @Override
                    public boolean update(final UnsavedRevision newRevision) {
                        newRevision.getProperties().put("key", value);
                        return true;
                    }
                });
            }
            assertEquals(MAX_REV_TREE_DEPTH, document.getRevisionHistory().size());
        }

        database.compact();

        Query query = database.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
        QueryEnumerator rowEnum = query.run();
        while (rowEnum.hasNext()) {
            QueryRow row = rowEnum.next();
            assertEquals(MAX_REV_TREE_DEPTH, row.getDocument().getRevisionHistory().size());
        }
    }
}
