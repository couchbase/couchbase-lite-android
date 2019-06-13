//
// ErrorCaseTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
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

    // -- DatabaseTest
    @Test
    public void testDeleteUnsavedDocument() {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        try {
            db.delete(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            if (e.getCode() == CBLError.Code.NOT_FOUND)
                ;// expected
            else
                fail();
        }
        assertEquals("Scott Tiger", doc.getValue("name"));
    }

    @Test
    public void testSaveSavedMutableDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);
        doc.setValue("age", 20);
        saved = save(doc);
        assertEquals(2, saved.generation());
        assertEquals(20, saved.getInt("age"));
        assertEquals("Scott Tiger", saved.getString("name"));
    }

    @Test
    public void testDeleteSavedMutableDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);
        db.delete(doc);
        assertNull(db.getDocument("doc1"));
    }

    @Test
    public void testDeleteDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // purge doc
        db.purge(saved);

        try {
            db.delete(saved);
            fail();
        } catch (CouchbaseLiteException e) {
            if (e.getCode() == CBLError.Code.NOT_FOUND)
                ;// expected
            else
                fail();
        }
    }

    @Test
    public void testDeleteDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // delete doc
        db.delete(saved);

        // delete doc -> conflict resolver -> no-op
        db.delete(saved);
    }

    @Test
    public void testPurgeDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // delete doc
        db.delete(saved);

        // purge doc
        db.purge(saved);
    }

    @Test
    public void testPurgeDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = save(doc);

        // purge doc
        db.purge(saved);

        try {
            db.purge(saved);
            fail();
        } catch (CouchbaseLiteException e) {
            if (e.getCode() == CBLError.Code.NOT_FOUND)
                ;// expected
            else
                fail();
        }
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
