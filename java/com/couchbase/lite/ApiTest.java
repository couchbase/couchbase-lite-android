package com.couchbase.lite;

import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
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


/**
 * Created by andrey on 12/3/13.
 */
public class ApiTest extends LiteTestCase {

    private int changeCount = 0;

    static void createDocumentsAsync(final Database db, final int n) {
        db.runAsync(new AsyncTask() {
            @Override
            public boolean run(Database database) {
                db.beginTransaction();
                createDocuments(db, n);
                db.endTransaction(true);
                return true;
            }
        });

    };

    static void createDocuments(final Database db, final int n) {
        //TODO should be changed to use db.runInTransaction
        for (int i=0; i<n; i++) {
            Map<String,Object> properties = new HashMap<String,Object>();
            properties.put("testName", "testDatabase");
            properties.put("sequence", i);
            createDocumentWithProperties(db, properties);
        }
    };

    //SERVER & DOCUMENTS

    public void testAPIManager() throws IOException {
        Manager manager = this.manager;
        Assert.assertTrue(manager != null);
        for(String dbName : manager.getAllDatabaseNames()){
            Database db = manager.getDatabase(dbName);
            Log.i(TAG, "Database '" + dbName + "':" + db.getDocumentCount() + " documents");
        }
        ManagerOptions options= new ManagerOptions();
        options.setReadOnly(true);

        Manager roManager=new Manager(new File(manager.getDirectory()), options);
        Assert.assertTrue(roManager!=null);

        Database db =roManager.getDatabase("foo");
        Assert.assertNull(db);
        List<String> dbNames=manager.getAllDatabaseNames();
        Assert.assertFalse(dbNames.contains("foo"));
        Assert.assertTrue(dbNames.contains(DEFAULT_TEST_DB));
    }

    public void testCreateDocument() {
        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testCreateDocument");
        properties.put("tag", 1337);

        Database db = startDatabase();
        Document doc=createDocumentWithProperties(db, properties);
        String docID=doc.getId();
        assertTrue("Invalid doc ID: " +docID , docID.length()>10);
        String currentRevisionID=doc.getCurrentRevisionId();
        assertTrue("Invalid doc revision: " +docID , currentRevisionID.length()>10);
        assertEquals(doc.getUserProperties(), properties);
        assertEquals(db.getDocument(docID), doc);

        db.clearDocumentCache();// so we can load fresh copies

        Document doc2 = db.getExistingDocument(docID);
        assertEquals(doc2.getId(), docID);
        assertEquals(doc2.getCurrentRevisionId(), currentRevisionID);

        assertNull(db.getExistingDocument("b0gus"));
    }


    public void testDeleteDatabase() {
        Database deleteme = manager.getDatabase("deleteme");
        assertTrue(deleteme.exists());
        boolean deleted = deleteme.delete();
        assertFalse(deleteme.exists());
        assertTrue(deleted);
        deleted = deleteme.delete();  // delete again, even though already deleted
        assertTrue(deleted);  // slightly counter-intuitive, but this is according to spec
        Database deletemeFetched = manager.getExistingDatabase("deleteme");
        assertNull(deletemeFetched);
    }

    public void testDatabaseCompaction() throws Exception{

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testDatabaseCompaction");
        properties.put("tag", 1337);


        Document doc=createDocumentWithProperties(database, properties);
        SavedRevision rev1 = doc.getCurrentRevision();

        Map<String,Object> properties2 = new HashMap<String,Object>(properties);
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

    public void testDocumentCache() throws Exception{

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

    public void testCreateRevisions() throws Exception{
        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        Database db = startDatabase();
        Document doc=createDocumentWithProperties(db, properties);

        SavedRevision rev1 = doc.getCurrentRevision();
        assertTrue(rev1.getId().startsWith("1-"));
        assertEquals(1, rev1.getSequence());
        assertEquals(0, rev1.getAttachments().size());

        // Test -createRevisionWithProperties:
        Map<String,Object> properties2 = new HashMap<String,Object>(properties);
        properties2.put("tag", 4567);

        SavedRevision rev2 = rev1.createRevision(properties2);
        assertNotNull("Put failed", rev2);

        assertTrue("Document revision ID is still " + doc.getCurrentRevisionId(), doc.getCurrentRevisionId().startsWith("2-"));


        assertEquals(rev2.getId(), doc.getCurrentRevisionId());
        assertNotNull(rev2.arePropertiesAvailable());
        assertEquals(rev2.getUserProperties(), properties2);
        assertEquals(rev2.getDocument(), doc);
        assertEquals(rev2.getProperty("_id"), doc.getId());
        assertEquals(rev2.getProperty("_rev"), rev2.getId());

        // Test -createRevision:
        UnsavedRevision newRev = rev2.createRevision();
        assertNull(newRev.getId());
        assertEquals(newRev.getParentRevision(), rev2);
        assertEquals(newRev.getParentRevisionId(), rev2.getId());
        List<SavedRevision> listRevs=new ArrayList<SavedRevision>();
        listRevs.add(rev1);
        listRevs.add(rev2);

        assertEquals(newRev.getRevisionHistory(), listRevs);
        assertEquals(newRev.getProperties(), rev2.getProperties());
        assertEquals(newRev.getUserProperties(), rev2.getUserProperties());

        Map<String,Object> userProperties=new HashMap<String, Object>();
        userProperties.put("because", "NoSQL");

        newRev.setUserProperties(userProperties);
        assertEquals(newRev.getUserProperties(), userProperties);

        Map<String,Object> expectProperties=new HashMap<String, Object>();
        expectProperties.put("because", "NoSQL");
        expectProperties.put("_id", doc.getId());
        expectProperties.put("_rev", rev2.getId());

        assertEquals(newRev.getProperties(),expectProperties);
        SavedRevision rev3 = newRev.save();
        assertNotNull(rev3);
        assertEquals(rev3.getUserProperties(), newRev.getUserProperties());
    }


    public void testCreateNewRevisions() throws Exception{
        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        Database db = startDatabase();
        Document doc=db.createDocument();
        UnsavedRevision newRev =doc.createRevision();

        Document newRevDocument = newRev.getDocument();
        assertEquals(doc, newRevDocument);
        assertEquals(db, newRev.getDatabase());
        assertNull(newRev.getParentRevisionId());
        assertNull(newRev.getParentRevision());

        Map<String,Object> expectProperties=new HashMap<String, Object>();
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
        assertNull(rev1.getParentRevisionId());
        assertNull(rev1.getParentRevision());

        newRev = rev1.createRevision();
        newRevDocument = newRev.getDocument();
        assertEquals(doc, newRevDocument);
        assertEquals(db, newRev.getDatabase());
        assertEquals(rev1.getId(), newRev.getParentRevisionId());
        assertEquals(rev1, newRev.getParentRevision());
        assertEquals(rev1.getProperties(), newRev.getProperties());
        assertEquals(rev1.getUserProperties(), newRev.getUserProperties());
        assertNotNull(!newRev.isDeletion());

        // we can't add/modify one property as on ios. need  to add separate method?
        // newRev[@"tag"] = @4567;
        properties.put("tag", 4567);
        newRev.setUserProperties(properties);
        SavedRevision rev2 = newRev.save();
        assertNotNull( "Save 2 failed", rev2);
        assertEquals(doc.getCurrentRevision(), rev2);
        assertNotNull(rev2.getId().startsWith("2-"));
        assertEquals(2, rev2.getSequence());
        assertEquals(rev1.getId(), rev2.getParentRevisionId());
        assertEquals(rev1, rev2.getParentRevision());

        assertNotNull("Document revision ID is still " + doc.getCurrentRevisionId(), doc.getCurrentRevisionId().startsWith("2-"));

        // Add a deletion/tombstone revision:
        newRev = doc.createRevision();
        assertEquals(rev2.getId(), newRev.getParentRevisionId());
        assertEquals(rev2, newRev.getParentRevision());
        newRev.setIsDeletion(true);
        SavedRevision rev3 = newRev.save();
        assertNotNull("Save 3 failed", rev3);
        assertEquals(doc.getCurrentRevision(), rev3);
        assertNotNull("Unexpected revID " + rev3.getId(), rev3.getId().startsWith("3-"));
        assertEquals(3, rev3.getSequence());
        assertTrue(rev3.isDeletion());

        assertTrue(doc.isDeleted());
        db.getDocumentCount();
        Document doc2 = db.getDocument(doc.getId());
        assertEquals(doc, doc2);
        assertNull(db.getExistingDocument(doc.getId()));

    }

    //API_SaveMultipleDocuments on IOS
    //API_SaveMultipleUnsavedDocuments on IOS
    //API_DeleteMultipleDocuments commented on IOS


    public void testDeleteDocument() throws Exception{
        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testDeleteDocument");

        Database db = startDatabase();
        Document doc=createDocumentWithProperties(db, properties);
        assertTrue(!doc.isDeleted());
        assertTrue(!doc.getCurrentRevision().isDeletion());
        assertTrue(doc.delete());
        assertTrue(doc.isDeleted());
        assertNotNull(doc.getCurrentRevision().isDeletion());
    }


    public void testPurgeDocument() throws Exception{
        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testPurgeDocument");

        Database db = startDatabase();
        Document doc=createDocumentWithProperties(db, properties);
        assertNotNull(doc);
        assertNotNull(doc.purge());

        Document redoc = db.getCachedDocument(doc.getId());
        assertNull(redoc);

    }



    public void testAllDocuments() throws Exception{
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
        for (Iterator<QueryRow> it = rows; it.hasNext();) {
            QueryRow row = it.next();
            Log.i(TAG, "    --> " + row);
            Document doc = row.getDocument();
            assertNotNull("Couldn't get doc from query", doc );
            assertNotNull("QueryRow should have preloaded revision contents", doc.getCurrentRevision().arePropertiesAvailable());
            Log.i(TAG, "        Properties =" + doc.getProperties());
            assertNotNull("Couldn't get doc properties", doc.getProperties());
            assertEquals(doc.getProperty("testName"), "testDatabase");
            n++;
        }
        assertEquals(n, kNDocs);
    }


    public void testLocalDocs() throws Exception{
        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");

        Database db = startDatabase();

        Map<String,Object> props = db.getExistingLocalDocument("dock");
        assertNull(props);
        assertNotNull("Couldn't put new local doc", db.putLocalDocument(properties, "dock"));
        props = db.getExistingLocalDocument("dock");
        assertEquals(props.get("foo"), "bar");


        Map<String,Object> newProperties = new HashMap<String, Object>();
        newProperties.put("FOOO", "BARRR");

        assertNotNull("Couldn't update local doc", db.putLocalDocument(newProperties, "dock"));
        props = db.getExistingLocalDocument("dock");
        assertNull(props.get("foo"));
        assertEquals(props.get("FOOO"), "BARRR");

        assertNotNull("Couldn't delete local doc", db.deleteLocalDocument("dock"));
        props = db.getExistingLocalDocument("dock");
        assertNull(props);

        assertNotNull("Second delete should have failed", !db.deleteLocalDocument("dock"));
        //TODO issue: deleteLocalDocument should return error.code( see ios)

    }


    // HISTORY

    public void testHistory() throws Exception{
        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("testName", "test06_History");
        properties.put("tag", 1);

        Database db = startDatabase();

        Document doc = createDocumentWithProperties(db, properties);
        String rev1ID = doc.getCurrentRevisionId();
        Log.i(TAG, "1st revision: "+ rev1ID);
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
        Map<String,Object> gotProperties = rev1.getProperties();
        assertEquals(1, gotProperties.get("tag"));

        SavedRevision rev2 = revisions.get(1);
        assertEquals(rev2.getId(), rev2ID);
        assertEquals(rev2, doc.getCurrentRevision());
        gotProperties = rev2.getProperties();
        assertEquals(2, gotProperties.get("tag"));

        List<SavedRevision> tmp =  new ArrayList<SavedRevision>();
        tmp.add(rev2);
        assertEquals(doc.getConflictingRevisions(), tmp);
        assertEquals(doc.getLeafRevisions(), tmp);

    }


    public void testConflict() throws Exception{
        Map<String,Object> prop = new HashMap<String, Object>();
        prop.put("foo", "bar");

        Database db = startDatabase();
        Document doc = createDocumentWithProperties(db, prop);
        SavedRevision rev1 = doc.getCurrentRevision();

        Map<String,Object> properties = new HashMap<String, Object>();
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

        List<SavedRevision> confRevs = new ArrayList<SavedRevision>();
        confRevs.add(rev2b);
        confRevs.add(rev2a);
        assertEquals(doc.getConflictingRevisions(), confRevs);

        assertEquals(doc.getLeafRevisions(), confRevs);

        SavedRevision defaultRev, otherRev;
        if (rev2a.getId().compareTo(rev2b.getId()) > 0) {
            defaultRev = rev2a; otherRev = rev2b;
        } else {
            defaultRev = rev2b; otherRev = rev2a;
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

    //ATTACHMENTS

    public void testAttachments() throws Exception, IOException {
        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testAttachments");

        Database db = startDatabase();

        Document doc = createDocumentWithProperties(db, properties);
        SavedRevision rev = doc.getCurrentRevision();

        assertEquals(rev.getAttachments().size(), 0);
        assertEquals(rev.getAttachmentNames().size(), 0);
        assertNull(rev.getAttachment("index.html"));

        String content  = "This is a test attachment!";
        ByteArrayInputStream body = new ByteArrayInputStream(content.getBytes());

        UnsavedRevision rev2 = doc.createRevision();
        rev2.setAttachment("index.html", "text/plain; charset=utf-8", body);

        SavedRevision rev3 = rev2.save();
        assertNotNull(rev3);
        assertEquals(rev3.getAttachments().size(), 1);
        assertEquals(rev3.getAttachmentNames().size(), 1);

        Attachment attach = rev3.getAttachment("index.html");
        assertNotNull(attach);
        assertEquals(doc, attach.getDocument());
        assertEquals("index.html", attach.getName());
        List<String> attNames = new ArrayList<String>();
        attNames.add("index.html");
        assertEquals(rev3.getAttachmentNames(), attNames);

        assertEquals("text/plain; charset=utf-8", attach.getContentType());
        assertEquals(IOUtils.toString(attach.getContent(), "UTF-8"), content);
        assertEquals(content.getBytes().length, attach.getLength());

        UnsavedRevision newRev = rev3.createRevision();
        newRev.removeAttachment(attach.getName());
        SavedRevision rev4 = newRev.save();
        assertNotNull(rev4);
        assertEquals(0, rev4.getAttachmentNames().size());

    }

    //CHANGE TRACKING

    public void testChangeTracking() throws Exception{

        final CountDownLatch doneSignal = new CountDownLatch(1);

        Database db = startDatabase();
        db.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                doneSignal.countDown();
            }
        });

        createDocumentsAsync(db, 5);
        // We expect that the changes reported by the server won't be notified, because those revisions
        // are already cached in memory.
        boolean success = doneSignal.await(300, TimeUnit.SECONDS);
        assertTrue(success);
        assertEquals(5, db.getLastSequenceNumber());

    }


    //VIEWS

    public void testCreateView() throws Exception{
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
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");

        assertNotNull(view.getMap() != null);

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
        for (Iterator<QueryRow> it = rows; it.hasNext();) {
            QueryRow row = it.next();
            assertEquals(expectedKey, row.getKey());
            assertEquals(expectedKey+1, row.getSequenceNumber());
            ++expectedKey;
        }
    }



    //API_RunSlowView commented on IOS


    public void testValidation() throws Exception{
        Database db = startDatabase();
        db.setValidation("uncool", new Validator() {
            @Override
            public void validate(Revision newRevision, ValidationContext context) {
                {
                    if (newRevision.getProperty("groovy") ==null) {
                        context.reject("uncool");
                    }

                }
            }
        });

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("groovy",  "right on");
        properties.put( "foo", "bar");

        Document doc = db.createDocument();
        assertNotNull(doc.putProperties(properties));

        properties = new HashMap<String,Object>();
        properties.put( "foo", "bar");
        doc = db.createDocument();
        try{
            assertNull(doc.putProperties(properties));
        } catch (CouchbaseLiteException e){
            //TODO
            assertEquals(e.getCBLStatus().getCode(), Status.FORBIDDEN);
//            assertEquals(e.getLocalizedMessage(), "forbidden: uncool"); //TODO: Not hooked up yet
        }

    }


    public void testViewWithLinkedDocs() throws Exception{
        Database db = startDatabase();
        int kNDocs = 50;
        Document[] docs = new Document[50];

        String lastDocID = "";
        for (int i=0; i<kNDocs; i++) {
            Map<String,Object> properties = new HashMap<String,Object>();
            properties.put("sequence", i);
            properties.put("prev", lastDocID);
            Document doc = createDocumentWithProperties(db, properties);
            docs[i]=doc;
            lastDocID = doc.getId();
        }

        Query query = db.slowQuery(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                emitter.emit(document.get("sequence"), new Object[]{"_id" , document.get("prev") });

            }
        });

        query.setStartKey(23);
        query.setEndKey(33);
        query.setPrefetch(true);
        QueryEnumerator rows = query.run();
        assertNotNull(rows);
        assertEquals(rows.getCount(), 11);

        int rowNumber = 23;


        for (Iterator<QueryRow> it = rows; it.hasNext();) {
            QueryRow row = it.next();
            assertEquals(row.getKey(), rowNumber);
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

    public void runLiveQuery(String methodNameToCall) throws Exception {

        final Database db = startDatabase();
        final CountDownLatch doneSignal = new CountDownLatch(11);  // 11 corresponds to startKey=23; endKey=33

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
        for (int i=23; i<34; i++) {
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
                    if (expectedKeys.contains(row.getKey())) {
                        expectedKeys.remove(row.getKey());
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
                    assertEquals(row.getKey(), expectedKey);
                    ++expectedKey;
                }
                doneSignal.countDown();

            }
        });

        Log.i(TAG, "Waiting for async query to finish...");
        boolean success = doneSignal.await(300, TimeUnit.SECONDS);
        assertTrue("Done signal timed out, async query never ran", success);

    }

    /**
     * Make sure that a database's map/reduce functions are shared with the shadow database instance
     * running in the background server.
     */
    public void testSharedMapBlocks() throws Exception {
        Manager mgr = new Manager(new File(getRootDirectory(), "API_SharedMapBlocks"), Manager.DEFAULT_OPTIONS);
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
        boolean ok = view.setMapAndReduce(new Mapper() {
                                              @Override
                                              public void map(Map<String, Object> document, Emitter emitter) {

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
            public boolean run(Database database) {
                assertNotNull(database);
                View serverView = database.getExistingView("view");
                assertNotNull(serverView);
                assertEquals(database.getFilter("phil"), filter);
                assertEquals(database.getValidation("val"), validation);
                assertEquals(serverView.getMap(), map);
                assertEquals(serverView.getReduce(), reduce);
                return true;

            }
        });
        result.get();  // blocks until async task has run
        db.close();
        mgr.close();

    }


    public void testChangeUUID() throws Exception{
        Manager mgr = new Manager(new File(getRootDirectory(), "ChangeUUID"), Manager.DEFAULT_OPTIONS);
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


}
