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
        assertTrue(document.purge());

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

}