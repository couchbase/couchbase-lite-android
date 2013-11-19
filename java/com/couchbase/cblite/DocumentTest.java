package com.couchbase.cblite;

import com.couchbase.cblite.testapp.tests.CBLiteTestCase;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DocumentTest extends CBLiteTestCase {

    public void testNewDocumentHasCurrentRevision() throws CBLiteException {

        CBLDocument document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevisionId());
        Assert.assertNotNull(document.getCurrentRevision());

    }

    public void testDeleteDocument() throws CBLiteException {

        CBLDocument document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
        Assert.assertNotNull(document.getCurrentRevision());
        String docId = document.getId();
        document.delete();
        Assert.assertTrue(document.isDeleted());
        CBLDocument fetchedDoc = database.getExistingDocument(docId);
        Assert.assertNull(fetchedDoc);

        // query all docs and make sure we don't see that document
        database.getAllDocs(new CBLQueryOptions());
        CBLQuery queryAllDocs = database.createAllDocumentsQuery();
        CBLQueryEnumerator queryEnumerator = queryAllDocs.getRows();
        for (Iterator<CBLQueryRow> it = queryEnumerator; it.hasNext();) {
            CBLQueryRow row = it.next();
            Assert.assertFalse(row.getDocument().getId().equals(docId));
        }

    }

}