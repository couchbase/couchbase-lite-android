package com.couchbase.cblite;

import com.couchbase.cblite.testapp.tests.CBLiteTestCase;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

public class DocumentTest extends CBLiteTestCase {

    public void testNewDocumentHasCurrentRevision() throws CBLiteException {

        CBLDocument document = database.createDocument();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "foo");
        properties.put("bar", Boolean.FALSE);
        document.putProperties(properties);
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
        CBLDocument fetchedDoc = database.getExistingDocument(docId);
        Assert.assertNull(fetchedDoc);

    }

}