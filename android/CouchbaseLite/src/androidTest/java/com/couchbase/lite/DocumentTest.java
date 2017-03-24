package com.couchbase.lite;

import com.couchbase.litecore.Constants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentTest extends BaseTest {
    private static final String TAG = DocumentTest.class.getName();

    @Before
    public void setUp() {
        super.setUp();
        // TODO: DB005 - ConflictResolver
    }

    @After
    public void tearDown() {
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

        doc.save();
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

        doc.save();
        assertTrue(doc.exists());
        assertFalse(doc.isDeleted());
        assertNull(doc.getProperties());
        assertEquals(doc, db.getDocument("doc1"));
    }

    @Test
    public void testIterator() {
        Document doc = db.getDocument("doc1");

        // Primitives:
        doc.set("bool", true);
        doc.set("double", 1.1);
        doc.set("integer", 2);

        // iterator
        Iterator<String> itr = doc.iterator();
        assertNotNull(itr);
        int i = 0;
        while (itr.hasNext()) {
            String key = itr.next();
            i++;
        }
        assertEquals(3, i);

        ////// Save Document
        doc.save();

        // iterator
        itr = doc.iterator();
        assertNotNull(itr);
        i = 0;
        while (itr.hasNext()) {
            String key = itr.next();
            i++;
        }
        assertEquals(3, i);

        ////// Reopen the database and get the document again:
        reopenDB();

        Document doc1 = db.getDocument("doc1");
        assertNotNull(doc1);

        // iterator
        Iterator<String> itr1 = doc1.iterator();
        assertNotNull(itr1);
        i = 0;
        while (itr1.hasNext()) {
            String key = itr1.next();
            i++;
        }
        assertEquals(3, i);
    }

    @Test
    public void testPropertyAccessors() {
        Document doc = db.getDocument("doc1");

        // Primitives:
        doc.set("bool", true);
        doc.set("double", 1.1);
        doc.set("integer", 2);

        // Objects:
        doc.set("string", "str");
        doc.set("boolObj", Boolean.TRUE);
        doc.set("number", Integer.valueOf(1));
        Map<String, String> dict = new HashMap<>();
        dict.put("foo", "bar");
        doc.set("dict", dict);
        List<String> list = Arrays.asList("1", "2");
        doc.set("array", list);

        // null
        doc.set(null, null);
        doc.set("nullarray", Arrays.asList(null, null));

        // Date:
        Date date = new Date();
        doc.set("date", date);

        // save doc
        doc.save();

        // Primitives:
        assertEquals(true, doc.getBoolean("bool"));
        assertEquals(1.1, doc.getDouble("double"), 0.0);
        assertEquals(2, doc.getInt("integer"));

        // Objects:
        assertEquals("str", doc.get("string"));
        assertEquals(Boolean.TRUE, doc.get("boolObj"));
        assertEquals(Integer.valueOf(1), doc.get("number"));
        assertEquals(dict, doc.get("dict"));
        assertEquals(list, doc.get("array"));

        // null
        assertEquals(null, doc.get(null));
        assertEquals(Arrays.asList(null, null), doc.get("nullarray"));

        // Date: once serialized, truncate less than a second
        assertTrue(Math.abs(date.getTime() - doc.getDate("date").getTime()) < 1000);

        ////// Reopen the database and get the document again:
        reopenDB();

        Document doc1 = db.getDocument("doc1");
        assertNotNull(doc1);

        // Primitives:
        assertEquals(true, doc1.getBoolean("bool"));
        assertEquals(1.1, doc1.getDouble("double"), 0.0);
        assertEquals(2, doc1.getInt("integer"));

        // Objects:
        assertEquals("str", doc1.getString("string"));
        assertEquals("str", doc1.get("string"));
        assertEquals(Boolean.TRUE, doc1.get("boolObj"));
        assertEquals(Integer.valueOf(1), doc1.get("number"));
        assertEquals(dict, doc1.get("dict"));
        assertEquals(list, doc1.get("array"));

        // null
        assertEquals(null, doc1.get(null));
        assertEquals(Arrays.asList(null, null), doc1.get("nullarray"));

        // Date: once serialized, truncate less than a second
        assertTrue(Math.abs(date.getTime() - doc.getDate("date").getTime()) < 1000);
    }

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
    public void testPropertiesDifferentType() {
        Document doc = db.getDocument("doc1");
        doc.set("string", "demo");
        doc.set("int", 1);

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(0, doc.getInt("string"));
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));

        // after save
        doc.save();

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));

        // re-obtain
        doc = db.getDocument("doc1");

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));

        // after reopen
        reopenDB();

        doc = db.getDocument("doc1");

        assertEquals("demo", doc.getString("string"));
        assertEquals(true, doc.getBoolean("string"));
        assertEquals(0.0F, doc.getFloat("string"), 0.0F);
        assertEquals(0.0, doc.getDouble("string"), 0.0);
        assertEquals(null, doc.getArray("string"));
        assertEquals(null, doc.getDate("string"));

        assertEquals(null, doc.getString("int"));
        assertEquals(true, doc.getBoolean("int"));
        assertEquals(1.0F, doc.getFloat("int"), 0.0F);
        assertEquals(1.0, doc.getDouble("int"), 0.0);
        assertEquals(1, doc.getInt("int"));
        assertEquals(null, doc.getArray("int"));
        assertEquals(null, doc.getDate("int"));
    }

    @Test
    public void testRemoveProperties() {
        Document doc = db.getDocument("doc1");
        Map<String, Object> props = new HashMap<>();
        props.put("type", "profile");
        props.put("name", "Jason");
        props.put("weight", 130.5);
        Map<String, Object> addresss = new HashMap<>();
        addresss.put("street", "1 milky way.");
        addresss.put("city", "galaxy city");
        addresss.put("zip", 12345);
        props.put("address", addresss);
        doc.setProperties(props);

        assertEquals(130.5, doc.getDouble("weight"), 0.0);
        assertEquals("galaxy city", ((Map<String, Object>) doc.get("address")).get("city"));

        doc.set("name", null);
        doc.set("weight", null);

        Map<String, Object> addressCopy = new HashMap<>((Map<String, Object>) doc.get("address"));
        addressCopy.put("city", null);
        doc.set("address", addressCopy);

        assertNull(doc.get("name"));
        assertNull(doc.get("weight"));
        assertEquals(0.0, doc.getDouble("weight"), 0.0);
        assertNull(((Map<String, Object>) doc.get("address")).get("city"));
    }

    @Test
    public void testContainsKey() {
        Document doc = db.getDocument("doc1");
        Map<String, Object> props = new HashMap<>();
        props.put("type", "profile");
        props.put("name", "Jason");
        Map<String, Object> addresss = new HashMap<>();
        addresss.put("street", "1 milky way.");
        props.put("address", addresss);
        doc.setProperties(props);

        assertTrue(doc.contains("type"));
        assertTrue(doc.contains("name"));
        assertTrue(doc.contains("address"));
        assertFalse(doc.contains("weight"));
    }

    @Test
    public void testDelete() {
        Document doc = db.getDocument("doc1");
        doc.set("type", "profile");
        doc.set("name", "Scott");
        assertFalse(doc.exists());
        assertFalse(doc.isDeleted());

        // Delete before save:
        try {
            doc.delete();
            fail("CouchbaseLiteException expected");
        } catch (CouchbaseLiteException e) {
            // should be com here...
            assertEquals(Constants.C4ErrorDomain.LiteCoreDomain, e.getDomain());
        }
        assertEquals("profile", doc.get("type"));
        assertEquals("Scott", doc.get("name"));

        // Save:
        doc.save();
        assertTrue(doc.exists());
        assertFalse(doc.isDeleted());

        // Delete:
        doc.delete();
        assertTrue(doc.exists());
        assertTrue(doc.isDeleted());
        assertNull(doc.getProperties());
    }

    @Test
    public void testPurge() {
        Document doc = db.getDocument("doc1");
        doc.set("type", "profile");
        doc.set("name", "Scott");
        assertFalse(doc.exists());
        assertFalse(doc.isDeleted());

        // Delete before save:
        try {
            doc.purge();
            fail("CouchbaseLiteException expected");
        } catch (CouchbaseLiteException e) {
            // should be com here...
        }
        assertEquals("profile", doc.get("type"));
        assertEquals("Scott", doc.get("name"));

        // Save:
        doc.save();
        assertTrue(doc.exists());
        assertFalse(doc.isDeleted());

        // Purge:
        doc.purge();
        assertFalse(doc.exists());
        assertFalse(doc.isDeleted());
    }

    @Test
    public void testRevert() {
        Document doc = db.getDocument("doc1");
        doc.set("type", "profile");
        doc.set("name", "Scott");

        // Revert before save:
        doc.revert();
        assertNull(doc.get("type"));
        assertNull(doc.get("name"));

        // Save:
        doc.set("type", "profile");
        doc.set("name", "Scott");
        doc.save();
        assertEquals("profile", doc.get("type"));
        assertEquals("Scott", doc.get("name"));

        // Make some changes:
        doc.set("type", "user");
        doc.set("name", "Scottie");

        // Revert:
        doc.revert();
        assertEquals("profile", doc.get("type"));
        assertEquals("Scott", doc.get("name"));
    }

    @Test
    public void testReopenDB() {
        Document doc = db.getDocument("doc1");
        doc.set("string", "str");
        Map<String, Object> expect = new HashMap<>();
        expect.put("string", "str");
        assertEquals(expect, doc.getProperties());
        doc.save();

        reopenDB();

        doc = db.getDocument("doc1");
        assertEquals(expect, doc.getProperties());
        assertEquals("str", doc.get("string"));
    }

    @Test
    public void testConflict() {
        // TODO: DB005
    }

    @Test
    public void testConflictResolverGivesUp() {
        // TODO: DB005
    }

    @Test
    public void testDeletionConflict() {
        // TODO: DB005
    }

    @Test
    public void testConflictMineIsDeeper() {
        // TODO: DB005
    }

    @Test
    public void testConflictTheirsIsDeeper() {
        // TODO: DB005
    }

    @Test
    public void testBlob() {
        // TODO: DB005
    }

    @Test
    public void testEmptyBlob() {
        // TODO: DB005
    }

    @Test
    public void testBlobWithStream() {
        // TODO: DB005
    }

    @Test
    public void testMultipleBlobRead() {
        // TODO: DB005
    }

    @Test
    public void testReadExistingBlob() {
        // TODO: DB005
    }
}
