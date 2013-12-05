package com.couchbase.lite;

import com.couchbase.lite.testapp.tests.CBLiteTestCase;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DocumentTest extends CBLiteTestCase {

    public void testNewDocumentHasCurrentRevision() throws CBLiteException {

        Document document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevisionId());
        Assert.assertNotNull(document.getCurrentRevision());

    }

    public void testDeleteDocument() throws CBLiteException {

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
        database.getAllDocs(new CBLQueryOptions());
        CBLQuery queryAllDocs = database.createAllDocumentsQuery();
        CBLQueryEnumerator queryEnumerator = queryAllDocs.run();
        for (Iterator<CBLQueryRow> it = queryEnumerator; it.hasNext();) {
            CBLQueryRow row = it.next();
            Assert.assertFalse(row.getDocument().getId().equals(docId));
        }

    }

}