package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DocumentTest extends BaseTest {
    private static final String TAG = DocumentTest.class.getName();

    @Before
    public void setUp() {
        super.setUp();
        Log.e(TAG, "setUp");
        // TODO: Resolver
    }

    @After
    public void tearDown() {
        Log.e(TAG, "tearDown");
        super.tearDown();
    }

    @Test
    public void testNewDoc() {
        Document doc = db.getDocument();
        assertNotNull(doc);
        assertNotNull(doc.getID());
        assertTrue(doc.getID().length() > 0);
        assertEquals(db, doc.getDatabase());
        assertFalse(doc.exists());
        assertFalse(doc.isDeleted());
        assertNull(doc.getProperties());
        assertNull(doc.get("prop"));
        assertFalse(doc.getBoolean("prop"));
        assertEquals(0, doc.getInt("prop"));
        assertEquals(0.0, doc.getDouble("prop"), 0.0);
        assertNull(doc.getDate("prop"));
        assertNull(doc.getString("prop"));

        doc.save(); // check if CouchbaseLiteException is thrown
        assertTrue(doc.exists());
        assertFalse(doc.isDeleted());
        assertNull(doc.getProperties());
    }

    @Test
    public void testNewDocWithId() {
        Document doc = db.getDocument("doc1");
        assertNotNull(doc);
        assertNotNull(doc.getID());
        assertEquals("doc1", doc.getID());
        assertEquals(db, doc.getDatabase());
        assertFalse(doc.exists());
        assertFalse(doc.isDeleted());
        assertNull(doc.getProperties());
        assertNull(doc.get("prop"));
        assertFalse(doc.getBoolean("prop"));
        assertEquals(0, doc.getInt("prop"));
        assertEquals(0.0, doc.getDouble("prop"), 0.0);
        assertNull(doc.getDate("prop"));
        assertNull(doc.getString("prop"));

        doc.save(); // check if CouchbaseLiteException is thrown
        assertTrue(doc.exists());
        assertFalse(doc.isDeleted());
        assertNull(doc.getProperties());
        assertEquals(doc, db.getDocument("doc1"));
    }
    @Test
    public void testPropertyPrimitiveAccessors() {
        Document doc = db.getDocument("doc1");

        // Primitives:
        doc.set("bool", true);
        doc.set("double", 1.1);
        doc.set("integer", 2);
        doc.set("string", "str");
        doc.set(null, null);

        // save doc
        doc.save();

        // Primitives:
        assertEquals(true, doc.getBoolean("bool"));
        assertEquals(1.1, doc.getDouble("double"), 0.0);
        assertEquals(2, doc.getInt("integer"));
        assertEquals("str", doc.getString("string"));
        assertEquals(null, doc.get(null));

        ////// Reopen the database and get the document again:
        reopenDB();

        Document doc1 = db.getDocument("doc1");
        assertNotNull(doc1);

        // Primitives:
        assertEquals(true, doc1.getBoolean("bool"));
        assertEquals(1.1, doc1.getDouble("double"), 0.0);
        assertEquals(2, doc1.getInt("integer"));
        assertEquals("str", doc1.getString("string"));
        assertEquals(null, doc1.get(null));
    }

//    @Test
//    public void testPropertyAccessors() {
//        Document doc = db.getDocument("doc1");
//
//        // Primitives:
//        doc.set("bool", true);
//        doc.set("double", 1.1);
//        doc.set("integer", 2);
//
//        // Objects:
//        doc.set("string", "str");
//        doc.set("boolObj", Boolean.TRUE);
//        doc.set("number", Integer.valueOf(1));
//        Map<String, String> dict = new HashMap<>();
//        dict.put("foo","bar");
//        doc.set("dict", dict);
//        List<String> list = Arrays.asList("1", "2");
//        doc.set("array", list);
//
//        // null
//        doc.set(null, null);
//        doc.set("nullarray", Arrays.asList(null, null));
//
//        // Date:
//        // TODO:
//
//        // save doc
//        doc.save();
//
//        // Primitives:
//        assertEquals(true, doc.getBoolean("bool"));
//        assertEquals(1.1, doc.getDouble("double"), 0.0);
//        assertEquals(2, doc.getInt("integer"));
//
//        // Objects:
//        assertEquals("str", doc.get("string"));
//        assertEquals(Boolean.TRUE, doc.get("boolObj"));
//        assertEquals(Integer.valueOf(1), doc.get("number"));
//        assertEquals(dict, doc.get("dict"));
//        assertEquals(list, doc.get("array"));
//
//        // null
//        assertEquals(null, doc.get(null));
//        assertEquals(Arrays.asList(null, null), doc.get("nullarray"));
//
//        // Date:
//        // TODO:
//
//        ////// Reopen the database and get the document again:
//        reopenDB();
//
//        Document doc1 = db.getDocument("doc1");
//        assertNotNull(doc1);
//
//        // Primitives:
//        assertEquals(true, doc1.getBoolean("bool"));
//        assertEquals(1.1, doc1.getDouble("double"), 0.0);
//        assertEquals(2, doc1.getInt("integer"));
//
//        // Objects:
//        assertEquals("str", doc1.get("string"));
//        assertEquals(Boolean.TRUE, doc1.get("boolObj"));
//        assertEquals(Integer.valueOf(1), doc1.get("number"));
//        assertEquals(dict, doc1.get("dict"));
//        assertEquals(list, doc1.get("array"));
//
//        // null
//        assertEquals(null, doc1.get(null));
//        assertEquals(Arrays.asList(null, null), doc1.get("nullarray"));
//
//        // Date:
//        // TODO:
//    }

    @Test
    public void testProperties() {
        Document doc = db.getDocument("doc1");
        doc.set("type", "demo");
        doc.set("weight", 12.5);
        doc.set("tags", Arrays.asList("useless", "temporary"));

        assertEquals("demo", doc.get("type"));
        assertEquals(12.5, doc.getDouble("weight"), 0.0000001);
        assertEquals(Arrays.asList("useless", "temporary"), doc.getArray("tags"));
        Map<String, Object> expect = new HashMap<>();
        expect.put("type", "demo");
        expect.put("weight", 12.5);
        expect.put("tags", Arrays.asList("useless", "temporary"));
        assertEquals(expect, doc.getProperties());
    }

    @Test
    public void testRemoveKeys() {
    }

    @Test
    public void testContainsKey() {
    }

    @Test
    public void testDelete() {
    }

    @Test
    public void testPurge() {
    }

    @Test
    public void testRevert() {
    }

    @Test
    public void testReopenDB() {
    }

    @Test
    public void testConflict() {
    }

    @Test
    public void testConflictResolverGivesUp() {
    }

    @Test
    public void testDeletionConflict() {
    }

    @Test
    public void testConflictMineIsDeeper() {
    }

    @Test
    public void testConflictTheirsIsDeeper() {
    }

    @Test
    public void testBlob() {
    }

    @Test
    public void testEmptyBlob() {
    }

    @Test
    public void testBlobWithStream() {
    }

    @Test
    public void testMultipleBlobRead() {
    }

    @Test
    public void testReadExistingBlob() {
    }
}
