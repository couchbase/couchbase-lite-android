package com.couchbase.lite;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
