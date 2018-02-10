package com.couchbase.lite;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ErrorCaseTest extends BaseTest {
    // -- DatabaseTest

    @Test
    public void testDeleteSameDocTwice() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // First time deletion:
        db.delete(doc);
        assertEquals(0, db.getCount());

        assertNull(db.getDocument(docID));

        // Second time deletion:
        // NOTE: doc is pointing to old revision. this cause conflict but this generate same revision
        db.delete(doc);

        assertNull(db.getDocument(docID));
    }

    // -- DatabaseTest
    @Test
    public void testDeleteUnsavedDocument() {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        assertFalse(doc.isDeleted());
        try {
            db.delete(doc);
            fail();
        } catch (IllegalArgumentException iae) {
            ; //expected
        } catch (CouchbaseLiteException e) {
            fail();
        }
        assertFalse(doc.isDeleted());
        assertEquals("Scott Tiger", doc.getValue("name"));
    }

    @Test
    public void testSaveSavedMutableDocument() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // following is error case
        doc.setValue("age", 20);
        try {
            saved = save(doc);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDeleteSavedMutableDocument() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // following is error case
        try {
            db.delete(doc);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDeleteDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // purge doc
        db.purge(saved);

        // no-op
        db.delete(saved);
    }

    @Test
    public void testDeleteDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // delete doc
        db.delete(saved);

        // delete doc -> conflict resolver -> no-op
        db.delete(saved);
    }

    @Test
    public void testPurgeDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // delete doc
        db.delete(saved);

        // purge doc
        db.purge(saved);
    }

    @Test
    public void testPurgeDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // purge doc
        db.purge(saved);

        // no-op
        db.purge(saved);
    }

    // -- ArrayTest

    static class CustomClass {
        public String text = "custom";
    }

    @Test
    public void testAddValueUnExpectedObject() {
        MutableArray mArray = new MutableArray();
        try {
            mArray.addValue(new CustomClass());
            fail();
        } catch (IllegalArgumentException ex) {
            // ok!!
        }
    }

    @Test
    public void testSetValueUnExpectedObject() {
        MutableArray mArray = new MutableArray();
        mArray.addValue(0);
        try {
            mArray.setValue(0, new CustomClass());
            fail();
        } catch (IllegalArgumentException ex) {
            // ok!!
        }
    }
}
