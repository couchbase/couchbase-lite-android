package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;

import junit.framework.Assert;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DocumentTest extends LiteTestCase {

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
    public void failingTestPutDeletedDocument() throws CouchbaseLiteException {

        Document document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevision());
        String docId = document.getId();

        properties.put("_rev",document.getCurrentRevisionId());
        properties.put("_deleted", true);
        properties.put("mykey", "myval");
        SavedRevision newRev = document.putProperties(properties);
        newRev.loadProperties();
        assertTrue( newRev.getProperties().containsKey("mykey") );

        Assert.assertTrue(document.isDeleted());
        Document fetchedDoc = database.getExistingDocument(docId);
        Assert.assertNull(fetchedDoc);

        // query all docs and make sure we don't see that document
        database.getAllDocs(new QueryOptions());
        Query queryAllDocs = database.createAllDocumentsQuery();
        QueryEnumerator queryEnumerator = queryAllDocs.run();
        for (Iterator<QueryRow> it = queryEnumerator; it.hasNext();) {
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
        database.getAllDocs(new QueryOptions());
        Query queryAllDocs = database.createAllDocumentsQuery();
        QueryEnumerator queryEnumerator = queryAllDocs.run();
        for (Iterator<QueryRow> it = queryEnumerator; it.hasNext();) {
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
                deleted,
                database
        );
        EnumSet<Database.TDContentOptions> contentOptions = EnumSet.of(
                Database.TDContentOptions.TDIncludeAttachments,
                Database.TDContentOptions.TDBigAttachmentsFollow
        );
        database.loadRevisionBody(revisionInternal, contentOptions);

        // now lets purge the document, and then try to load the revision body again
        document.purge();

        boolean gotExpectedException = false;
        try {
            database.loadRevisionBody(revisionInternal, contentOptions);
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

}