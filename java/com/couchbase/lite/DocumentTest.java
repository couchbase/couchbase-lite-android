package com.couchbase.lite;

import junit.framework.Assert;

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

}