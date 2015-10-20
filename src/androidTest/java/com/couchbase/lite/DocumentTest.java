package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DocumentTest extends LiteTestCaseWithDB {

    public void testNewDocumentHasCurrentRevision() throws CouchbaseLiteException {

        Document document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevisionId());
        Assert.assertNotNull(document.getCurrentRevision());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/301
     */
    public void testPutDeletedDocument() throws CouchbaseLiteException {

        Document document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevision());
        String docId = document.getId();

        properties.put("_rev", document.getCurrentRevisionId());
        properties.put("_deleted", true);
        properties.put("mykey", "myval");
        SavedRevision newRev = document.putProperties(properties);
        newRev.loadProperties();
        assertTrue(newRev.getProperties().containsKey("mykey"));

        Assert.assertTrue(document.isDeleted());
        Document fetchedDoc = database.getExistingDocument(docId);
        Assert.assertNull(fetchedDoc);

        // query all docs and make sure we don't see that document
        database.getAllDocs(new QueryOptions());
        Query queryAllDocs = database.createAllDocumentsQuery();
        QueryEnumerator queryEnumerator = queryAllDocs.run();
        for (Iterator<QueryRow> it = queryEnumerator; it.hasNext(); ) {
            QueryRow row = it.next();
            Assert.assertFalse(row.getDocument().getId().equals(docId));
        }

    }

    public void testDeleteDocument() throws CouchbaseLiteException {

        Document document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevision());
        String docId = document.getId();
        document.delete();
        Assert.assertTrue(document.isDeleted());
        Document fetchedDoc = database.getExistingDocument(docId);
        Assert.assertNull(fetchedDoc);

        // query all docs and make sure we don't see that document
        //database.getAllDocs(new QueryOptions());
        Query queryAllDocs = database.createAllDocumentsQuery();
        QueryEnumerator queryEnumerator = queryAllDocs.run();
        for (Iterator<QueryRow> it = queryEnumerator; it.hasNext(); ) {
            QueryRow row = it.next();
            Assert.assertFalse(row.getDocument().getId().equals(docId));
        }

    }

    /**
     * Port test over from:
     * https://github.com/couchbase/couchbase-lite-ios/commit/e0469300672a2087feb46b84ca498facd49e0066
     */
    public void testGetNonExistentDocument() throws CouchbaseLiteException {
        assertNull(database.getExistingDocument("missing"));
        Document doc = database.getDocument("missing");
        assertNotNull(doc);
        assertNull(database.getExistingDocument("missing"));
    }

    // Reproduces issue #167
    // https://github.com/couchbase/couchbase-lite-android/issues/167
    public void testLoadRevisionBody() throws CouchbaseLiteException {

        Document document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevision());

        boolean deleted = false;
        RevisionInternal revisionInternal = new RevisionInternal(
                document.getId(),
                document.getCurrentRevisionId(),
                deleted
        );
        database.loadRevisionBody(revisionInternal);

        // now lets purge the document, and then try to load the revision body again
        document.purge();

        boolean gotExpectedException = false;
        RevisionInternal copyRev = revisionInternal.copyWithoutBody();
        try {
            database.loadRevisionBody(copyRev);
        } catch (CouchbaseLiteException e) {
            if (e.getCBLStatus().getCode() == Status.NOT_FOUND) {
                gotExpectedException = true;
            }
        }
        assertTrue(gotExpectedException);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/281
     */
    public void testDocumentWithRemovedProperty() {

        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "fakeid");
        props.put("_removed", true);
        props.put("foo", "bar");

        Document doc = createDocumentWithProperties(database, props);
        assertNotNull(doc);

        Document docFetched = database.getDocument(doc.getId());
        Map<String, Object> fetchedProps = docFetched.getCurrentRevision().getProperties();
        assertNotNull(fetchedProps.get("_removed"));
        assertTrue(docFetched.getCurrentRevision().isGone());
    }

    public void failingTestGetDocumentWithLargeJSON() {

        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "laaargeJSON");
        char[] chars = new char[2500000];//~5MB
        Arrays.fill(chars, 'a');
        props.put("foo", new String(chars));

        Document doc = createDocumentWithProperties(database, props);
        assertNotNull(doc);

        Document docFetched = database.getDocument(doc.getId());
        Map<String, Object> fetchedProps = docFetched.getCurrentRevision().getProperties();
        assertEquals(fetchedProps.get("foo"), new String(chars));
    }

    public void failingTestDocumentPropertiesAreImmutable() throws Exception {
        String jsonString = "{\n" +
                "    \"name\":\"praying mantis\",\n" +
                "    \"wikipedia\":{\n" +
                "        \"behavior\":{\n" +
                "            \"style\":\"predatory\",\n" +
                "            \"attack\":\"ambush\"\n" +
                "        },\n" +
                "        \"evolution\":{\n" +
                "            \"ancestor\":\"proto-roaches\",\n" +
                "            \"cousin\":\"termite\"\n" +
                "        }       \n" +
                "    }   \n" +
                "\n" +
                "}";
        Map map = (Map) Manager.getObjectMapper().readValue(jsonString, Object.class);
        Document doc = createDocumentWithProperties(database, map);

        boolean firstLevelImmutable = false;
        Map<String, Object> props = doc.getProperties();
        try {
            props.put("name", "bug");
        } catch (UnsupportedOperationException e) {
            firstLevelImmutable = true;
        }
        assertTrue(firstLevelImmutable);

        boolean secondLevelImmutable = false;
        Map wikiProps = (Map) props.get("wikipedia");
        try {
            wikiProps.put("behavior", "unknown");
        } catch (UnsupportedOperationException e) {
            secondLevelImmutable = true;
        }
        assertTrue(secondLevelImmutable);

        boolean thirdLevelImmutable = false;
        Map evolutionProps = (Map) wikiProps.get("behavior");
        try {
            evolutionProps.put("movement", "flight");
        } catch (UnsupportedOperationException e) {
            thirdLevelImmutable = true;
        }
        assertTrue(thirdLevelImmutable);
    }

    public void failingTestProvidedMapChangesAreSafe() throws Exception {
        Map<String, Object> originalProps = new HashMap<String, Object>();
        Document doc = createDocumentWithProperties(database, originalProps);

        Map<String, Object> nestedProps = new HashMap<String, Object>();
        nestedProps.put("version", "original");
        UnsavedRevision rev = doc.createRevision();
        rev.getProperties().put("nested", nestedProps);
        rev.save();

        nestedProps.put("version", "changed");
        assertEquals("original", ((Map) doc.getProperty("nested")).get("version"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Foo {
        private String bar;

        public Foo() {
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }
    }

    /**
     * Assert that if you add a
     *
     * @throws Exception
     */
    public void testNonPrimitiveTypesInDocument() throws Exception {

        Object fooProperty;
        Map<String, Object> props = new HashMap<String, Object>();

        Foo foo = new Foo();
        foo.setBar("basic");

        props.put("foo", foo);
        Document doc = createDocumentWithProperties(database, props);
        fooProperty = doc.getProperties().get("foo");
        assertTrue(fooProperty instanceof Map);
        assertFalse(fooProperty instanceof Foo);

        Document fetched = database.getDocument(doc.getId());
        fooProperty = fetched.getProperties().get("foo");
        assertTrue(fooProperty instanceof Map);
        assertFalse(fooProperty instanceof Foo);

        ObjectMapper mapper = new ObjectMapper();
        Foo fooResult = mapper.convertValue(fooProperty, Foo.class);
        assertEquals(foo.bar, fooResult.bar);

    }

    public void testDocCustomID() throws Exception {

        Document document = database.getDocument("my_custom_id");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");
        document.putProperties(properties);

        Document documentFetched = database.getDocument("my_custom_id");
        assertEquals("my_custom_id", documentFetched.getId());
        assertEquals("bar", documentFetched.getProperties().get("foo"));

    }

    public void testGetPropertiesFromDocNotYetSaved() {
        Document doc = database.createDocument();
        Map<String, Object> properties = doc.getProperties();
        assertNull(properties);
    }

    /**
     * Document.update() - simple successful scenario
     */
    public void testUpdate() throws Exception {
        Document document = database.getDocument("testUpdate");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("title", "testUpdate");
        document.putProperties(properties);

        final String title = "testUpdate - 2";
        final String notes = "notes - 2";

        document.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision newRevision) {
                Map<String, Object> properties = newRevision.getUserProperties();
                properties.put("title", title);
                properties.put("notes", notes);
                newRevision.setUserProperties(properties);
                return true;
            }
        });
        Document document2 = database.getDocument("testUpdate");
        assertEquals(title, document2.getProperties().get("title"));
        assertEquals(notes, document2.getProperties().get("notes"));
    }

    /**
     * Document.update() - simple scenario with failure
     */
    public void testUpdateFalse() throws Exception {
        Document document = database.getDocument("testUpdateFalse");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("title", "testUpdate");
        document.putProperties(properties);

        final String title = "testUpdate - 2";
        final String notes = "notes - 2";

        document.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision newRevision) {
                Map<String, Object> properties = newRevision.getUserProperties();
                properties.put("title", title);
                properties.put("notes", notes);
                newRevision.setUserProperties(properties);
                return false;
            }
        });
        Document document2 = database.getDocument("testUpdateFalse");
        assertEquals("testUpdate", document2.getProperties().get("title"));
        assertNull(document2.getProperties().get("notes"));
    }

    /**
     * Unit Test for https://github.com/couchbase/couchbase-lite-java-core/issues/472
     * <p/>
     * Tries to reproduce the scenario which is described in following comment.
     * https://github.com/couchbase/couchbase-lite-net/issues/388#issuecomment-77637583
     */
    public void testUpdateConflict() throws Exception {
        Document document = database.getDocument("testUpdateConflict");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("title", "testUpdateConflict");
        document.putProperties(properties);

        final String title1 = "testUpdateConflict - 1";
        final String text1 = "notes - 1";

        final String title2 = "testUpdateConflict - 2";
        final String notes2 = "notes - 2";

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        // Another thread to update document
        // This thread pretends to be Pull replicator update logic
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "Thread.run() start");

                // wait till main thread finishes to create newRevision
                Log.w(TAG, "Thread.run() latch1.await()");
                try {
                    latch1.await();
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                Log.w(TAG, "Thread.run() exit from latch1.await()");

                Document document1 = database.getDocument("testUpdateConflict");
                Map<String, Object> properties1 = new HashMap<String, Object>();
                properties1.putAll(document1.getProperties());
                properties1.put("title", title1);
                properties1.put("text", text1);
                try {
                    document1.putProperties(properties1);
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "[Thread.run()] " + e.getMessage());
                }

                Log.w(TAG, "Thread.run() latch2.countDown()");
                latch2.countDown();

                Log.w(TAG, "Thread.run() end");
            }
        });
        thread.start();

        // main thread to update document
        document.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision newRevision) {

                Log.w(TAG, "DocumentUpdater.update() start");

                // after created newRevision wait till other thread to update document.
                Log.w(TAG, "DocumentUpdater.update() latch1.countDown()");
                latch1.countDown();

                Log.w(TAG, "DocumentUpdater.update() latch2.await()");
                try {
                    latch2.await();
                } catch (InterruptedException e) {
                    Log.e(TAG, "[DocumentUpdater.update()]" + e.getMessage());
                }

                Map<String, Object> properties2 = newRevision.getUserProperties();
                properties2.put("title", title2);
                properties2.put("notes", notes2);
                newRevision.setUserProperties(properties2);

                Log.w(TAG, "DocumentUpdater.update() end");
                return true;
            }
        });

        Document document4 = database.getDocument("testUpdateConflict");
        Log.w(TAG, "" + document4.getProperties());
        assertEquals(title2, document4.getProperties().get("title"));
        assertEquals(notes2, document4.getProperties().get("notes"));
        assertEquals(text1, document4.getProperties().get("text"));
    }

    /**
     * Nonsensical CouchbaseLiteException (Conflict) exception thrown on UnsavedRevision.save() #479
     * https://github.com/couchbase/couchbase-lite-java-core/issues/479
     * <p/>
     * Note: this test fails with 1.0.4 or earlier. This test takes time, as default, test is disabled.
     */
    public void disabledTestNonsensicalConflictExceptionOnUnsavedRevision()
            throws CouchbaseLiteException {

        View testNonsensicalConflict = database.getView("testNonsensicalConflict");
        testNonsensicalConflict.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(null, null);
            }
        }, "");
        Query query = testNonsensicalConflict.createQuery();
        LiveQuery liveQuery = query.toLiveQuery();

        liveQuery.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                QueryEnumerator rows = event.getRows();
                while (rows.hasNext()) {
                    QueryRow next = rows.next();
                    next.getDocument();
                }
            }
        });
        liveQuery.run();

        // try 100 times to reproduce the issue
        for (int i = 0; i < 1000; i++) {
            Document document = database.createDocument();
            String documentID = document.getId();

            document.putProperties(Collections.<String, Object>singletonMap("test", "1"));
            document = document.getCurrentRevision().getDocument();
            assertEquals(documentID, document.getProperty("_id"));
            assertTrue(((String) document.getProperty("_rev")).startsWith("1-"));

            SavedRevision savedRevision = document.getCurrentRevision();
            savedRevision.createRevision(Collections.<String, Object>singletonMap("test", "2"));
            assertEquals(documentID, document.getProperty("_id"));
            assertTrue(((String) document.getProperty("_rev")).startsWith("2-"));

            document = document.getCurrentRevision().getDocument();

            UnsavedRevision unsavedRevision = document.createRevision();
            unsavedRevision.setProperties(Collections.<String, Object>singletonMap("test", "3"));
            unsavedRevision.save(); //Nonsensical conflict thrown here
            assertEquals(documentID, document.getProperty("_id"));
            assertTrue(((String) document.getProperty("_rev")).startsWith("3-"));

            document = document.getCurrentRevision().getDocument();

            unsavedRevision = document.createRevision();
            unsavedRevision.setProperties(Collections.<String, Object>singletonMap("test", "4"));
            unsavedRevision.save(); //Or here
            assertEquals(documentID, document.getProperty("_id"));
            assertTrue(((String) document.getProperty("_rev")).startsWith("4-"));
        }
    }

    public void testAddChangeListener() throws CouchbaseLiteException, InterruptedException {
        final CountDownLatch documentChanged = new CountDownLatch(1);
        Document doc = database.createDocument();
        doc.addChangeListener(new Document.ChangeListener() {
            @Override
            public void changed(Document.ChangeEvent event) {
                DocumentChange docChange = event.getChange();
                String msg = "New revision added: %s. Conflict: %s";
                msg = String.format(msg,
                        docChange.getAddedRevision(), docChange.isConflict());
                Log.d(TAG, msg);
                documentChanged.countDown();
            }
        });
        doc.createRevision().save();
        boolean success = documentChanged.await(30, TimeUnit.SECONDS);
        assertTrue(success);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/563
     * Updating a document in a transaction block twice using Document.DocumentUpdater results in
     * an infinite loop
     * <p/>
     * NOTE: Use Document.update()
     */
    public void testMultipleUpdatesInTransactionWithUpdate() throws CouchbaseLiteException {
        final Document doc = database.createDocument();
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("key", "value1");
        doc.putProperties(properties);
        database.runInTransaction(
                new TransactionalTask() {
                    @Override
                    public boolean run() {
                        try {
                            doc.update(new Document.DocumentUpdater() {
                                @Override
                                public boolean update(UnsavedRevision newRevision) {
                                    Log.i(TAG, "Trying to update key to value 2");
                                    Map<String, Object> properties = newRevision.getUserProperties();
                                    properties.put("key", "value2");
                                    newRevision.setUserProperties(properties);
                                    return true;
                                }
                            });
                            doc.update(new Document.DocumentUpdater() {
                                @Override
                                public boolean update(UnsavedRevision newRevision) {
                                    Log.i(TAG, "Trying to update key to value 3");
                                    Map<String, Object> properties = newRevision.getUserProperties();
                                    properties.put("key", "value3");
                                    newRevision.setUserProperties(properties);
                                    return true;
                                }
                            });
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, "Trying to update key to value 2 or 3");
                            fail("Trying to update key to value 2 or 3, but failed.");
                            return false;
                        }
                        return true;
                    }
                });
        Map<String, Object> properties4 = doc.getProperties();
        Log.i(TAG, "properties4 = " + properties4);
    }

    /**
     * NOTE: This is variation of testMultipleUpdatesInTransactionWithUpdate() test
     * with using Document.putProperties()
     */
    public void testMultipleUpdatesInTransactionWithPutProperties() throws CouchbaseLiteException {
        final Document doc = database.createDocument();
        HashMap<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("key", "value1");
        doc.putProperties(properties1);
        assertTrue(database.runInTransaction(
                new TransactionalTask() {
                    @Override
                    public boolean run() {
                        try {
                            Map<String, Object> properties2 = new HashMap<String, Object>(doc.getProperties());
                            properties2.put("key", "value2");
                            doc.putProperties(properties2);
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, "Trying to update key to value 2");
                            fail("Trying to update key to value 2, but failed.");
                            return false;
                        }
                        try {
                            Map<String, Object> properties3 = new HashMap<String, Object>(doc.getProperties());
                            properties3.put("key", "value3");
                            doc.putProperties(properties3);
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, "Trying to update key to value 3");
                            fail("Trying to update key to value 3, but failed.");
                            return false;
                        }
                        return true;
                    }
                }));
        Map<String, Object> properties4 = doc.getProperties();
        Log.i(TAG, "properties4 = " + properties4);
        assertEquals("value3", properties4.get("key"));
    }

    public static SavedRevision createRevisionWithProps(SavedRevision createRevFrom,
                                                        Map<String, Object> properties,
                                                        boolean allowConflict)
            throws Exception {
        UnsavedRevision unsavedRevision = createRevFrom.createRevision();
        unsavedRevision.setUserProperties(properties);
        return unsavedRevision.save(allowConflict);
    }

    public void testResolveConflict() throws CouchbaseLiteException, Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testResolveConflict");
        properties.put("key", "1");

        final Document doc = database.getDocument("testResolveConflict");

        UnsavedRevision newRev1 = doc.createRevision();
        newRev1.setUserProperties(properties);
        SavedRevision rev1 = newRev1.save();

        Map<String, Object> props1 = new HashMap<String, Object>();
        props1.put("key", "2a");
        SavedRevision rev2a = createRevisionWithProps(rev1, props1, false);
        Map<String, Object> props2 = new HashMap<String, Object>();
        props2.put("key", "2b");
        SavedRevision rev2b = createRevisionWithProps(rev1, props2, true);

        final List<SavedRevision> conflicts = doc.getConflictingRevisions();
        if (conflicts.size() > 1) {
            // There is more than one current revision, thus a conflict!
            assertTrue(database.runInTransaction(new TransactionalTask() {
                @Override
                public boolean run() {
                    try {
                        // Come up with a merged/resolved document in some way that's
                        // appropriate for the app. You could even just pick the body of
                        // one of the revisions.
                        Map<String, Object> mergedProps = new HashMap<String, Object>(
                                conflicts.get(0).getUserProperties());
                        mergedProps.put("key", "3");

                        // Delete the conflicting revisions to get rid of the conflict:
                        SavedRevision current = doc.getCurrentRevision();
                        for (SavedRevision rev : conflicts) {
                            UnsavedRevision newRev = rev.createRevision();
                            if (rev.getId().equals(current.getId())) {
                                newRev.setProperties(mergedProps);
                            } else {
                                newRev.setIsDeletion(true);
                            }
                            // saveAllowingConflict allows 'rev' to be updated even if it
                            // is not the document's current revision.
                            newRev.save(true);
                        }
                    } catch (CouchbaseLiteException e) {
                        return false;
                    }
                    return true;
                }
            }));
        }
        assertEquals(1, doc.getConflictingRevisions().size());
        assertEquals("3", doc.getProperties().get("key"));
    }
}
