//
// DocumentTest.java
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.utils.DateUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class DocumentTest extends BaseTest {
    private static final String TEST_DATE = "2017-01-01T00:00:00.000Z";
    private static final String TEST_BLOB = "i'm blob";

    // used from other package's tests
    public static void populateData(MutableDocument doc) {
        doc.setValue("true", true);
        doc.setValue("false", false);
        doc.setValue("string", "string");
        doc.setValue("zero", 0);
        doc.setValue("one", 1);
        doc.setValue("minus_one", -1);
        doc.setValue("one_dot_one", 1.1);
        doc.setValue("date", DateUtils.fromJson(TEST_DATE));
        doc.setValue("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setValue("street", "1 Main street");
        dict.setValue("city", "Mountain View");
        dict.setValue("state", "CA");
        doc.setValue("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);

        // Blob:
        byte[] content = TEST_BLOB.getBytes();
        Blob blob = new Blob("text/plain", content);
        doc.setValue("blob", blob);
    }

    // used from other package's tests
    public static void populateDataByTypedSetter(MutableDocument doc) {
        doc.setBoolean("true", true);
        doc.setBoolean("false", false);
        doc.setString("string", "string");
        doc.setNumber("zero", 0);
        doc.setInt("one", 1);
        doc.setLong("minus_one", -1);
        doc.setDouble("one_dot_one", 1.1);
        doc.setDate("date", DateUtils.fromJson(TEST_DATE));
        doc.setString("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setString("street", "1 Main street");
        dict.setString("city", "Mountain View");
        dict.setString("state", "CA");
        doc.setDictionary("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addString("650-123-0001");
        array.addString("650-123-0002");
        doc.setArray("array", array);

        // Blob:
        byte[] content = TEST_BLOB.getBytes();
        Blob blob = new Blob("text/plain", content);
        doc.setBlob("blob", blob);
    }

    @Before
    public void setUp() throws Exception { super.setUp(); }

    @After
    public void tearDown() { super.tearDown(); }

    @Test
    public void testCreateDoc() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument();
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithID() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        assertNotNull(doc1a);
        assertEquals("doc1", doc1a.getId());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument("doc1");
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithEmptyStringID() {
        MutableDocument doc1a = new MutableDocument("");
        assertNotNull(doc1a);
        try {
            save(doc1a);
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(CBLError.Domain.CBLITE, e.getDomain());
            assertEquals(CBLError.Code.BAD_DOC_ID, e.getCode());
        }
    }

    @Test
    public void testCreateDocWithNilID() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument((String) null);
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithDict() throws CouchbaseLiteException {
        final Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30L);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        final MutableDocument doc1a = new MutableDocument(dict);
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertEquals(dict, doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertEquals(doc1a.getId(), doc1b.getId());
        assertEquals(dict, doc1b.toMap());
    }

    @Test
    public void testCreateDocWithIDAndDict() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30L);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        MutableDocument doc1a = new MutableDocument("doc1", dict);
        assertNotNull(doc1a);
        assertEquals("doc1", doc1a.getId());
        assertEquals(dict, doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument("doc1");
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertEquals(doc1a.getId(), doc1b.getId());
        assertEquals(dict, doc1b.toMap());
    }

    @Test
    public void testSetDictionaryContent() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30L);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        MutableDocument doc = new MutableDocument("doc1");
        doc.setData(dict);
        assertEquals(dict, doc.toMap());

        Document savedDoc = save(doc);
        //doc = db.getDocument("doc1");
        assertEquals(dict, savedDoc.toMap());

        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("name", "Danial Tiger");
        nuDict.put("age", 32L);

        Map<String, Object> nuAddress = new HashMap<>();
        nuAddress.put("street", "2 Main street");
        nuAddress.put("city", "Palo Alto");
        nuAddress.put("state", "CA");
        nuDict.put("address", nuAddress);

        nuDict.put("phones", Arrays.asList("650-234-0001", "650-234-0002"));

        doc = savedDoc.toMutable();
        doc.setData(nuDict);
        assertEquals(nuDict, doc.toMap());

        savedDoc = save(doc);
        assertEquals(nuDict, savedDoc.toMap());
    }

    @Test
    public final void testMutateEmptyDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc");
        db.save(doc);

        doc = db.getDocument("doc").toMutable();
        doc.setString("foo", "bar");
        db.save(doc);
    }

    @Test
    public void testGetValueFromDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        save(doc, d -> {
            assertEquals(0, d.getInt("key"));
            assertEquals(0.0f, d.getFloat("key"), 0.0f);
            assertEquals(0.0, d.getDouble("key"), 0.0);
            assertEquals(false, d.getBoolean("key"));
            assertNull(d.getBlob("key"));
            assertNull(d.getDate("key"));
            assertNull(d.getNumber("key"));
            assertNull(d.getValue("key"));
            assertNull(d.getString("key"));
            assertNull(d.getArray("key"));
            assertNull(d.getDictionary("key"));
            assertEquals(new HashMap<>(), d.toMap());
        });
    }

    @Test
    public void testSaveThenGetFromAnotherDB() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setValue("name", "Scott Tiger");
        save(doc1a);

        Database anotherDb = db.copy();
        Document doc1b = anotherDb.getDocument("doc1");
        assertTrue(doc1a != doc1b);
        assertEquals(doc1a.getId(), doc1b.getId());
        assertEquals(doc1a.toMap(), doc1b.toMap());
        anotherDb.close();
    }

    @Test
    public void testNoCacheNoLive() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setValue("name", "Scott Tiger");

        save(doc1a);

        Document doc1b = db.getDocument("doc1");
        Document doc1c = db.getDocument("doc1");

        Database anotherDb = db.copy();
        Document doc1d = anotherDb.getDocument("doc1");

        assertTrue(doc1a != doc1b);
        assertTrue(doc1a != doc1c);
        assertTrue(doc1a != doc1d);
        assertTrue(doc1b != doc1c);
        assertTrue(doc1b != doc1d);
        assertTrue(doc1c != doc1d);

        assertEquals(doc1a.toMap(), doc1b.toMap());
        assertEquals(doc1a.toMap(), doc1c.toMap());
        assertEquals(doc1a.toMap(), doc1d.toMap());

        MutableDocument mDoc1b = doc1b.toMutable();
        mDoc1b.setValue("name", "Daniel Tiger");
        doc1b = save(mDoc1b);

        Assert.assertNotEquals(doc1b.toMap(), doc1a.toMap());
        Assert.assertNotEquals(doc1b.toMap(), doc1c.toMap());
        Assert.assertNotEquals(doc1b.toMap(), doc1d.toMap());

        anotherDb.close();
    }

    @Test
    public void testSetString() throws CouchbaseLiteException {
        Validator<Document> validator4Save = d -> {
            assertEquals("", d.getValue("string1"));
            assertEquals("string", d.getValue("string2"));
        };

        Validator<Document> validator4SUpdate = d -> {
            assertEquals("string", d.getValue("string1"));
            assertEquals("", d.getValue("string2"));
        };

        // -- setValue
        // save
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("string1", "");
        mDoc.setValue("string2", "string");
        Document doc = save(mDoc, validator4Save);
        // update
        mDoc = doc.toMutable();
        mDoc.setValue("string1", "string");
        mDoc.setValue("string2", "");
        save(mDoc, validator4SUpdate);

        // -- setString
        // save
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("string1", "");
        mDoc2.setString("string2", "string");
        Document doc2 = save(mDoc2, validator4Save);

        // update
        mDoc2 = doc2.toMutable();
        mDoc2.setString("string1", "string");
        mDoc2.setString("string2", "");
        save(mDoc2, validator4SUpdate);
    }

    @Test
    public void testGetString() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertNull(d.getString("null"));
                assertNull(d.getString("true"));
                assertNull(d.getString("false"));
                assertEquals("string", d.getString("string"));
                assertNull(d.getString("zero"));
                assertNull(d.getString("one"));
                assertNull(d.getString("minus_one"));
                assertNull(d.getString("one_dot_one"));
                assertEquals(TEST_DATE, d.getString("date"));
                assertNull(d.getString("dict"));
                assertNull(d.getString("array"));
                assertNull(d.getString("blob"));
                assertNull(d.getString("non_existing_key"));
            });
        }
    }

    @Test
    public void testSetNumber() throws CouchbaseLiteException {
        Validator<Document> validator4Save = d -> {
            assertEquals(1, ((Number) d.getValue("number1")).intValue());
            assertEquals(0, ((Number) d.getValue("number2")).intValue());
            assertEquals(-1, ((Number) d.getValue("number3")).intValue());
            assertEquals(-10, ((Number) d.getValue("number4")).intValue());
        };

        Validator<Document> validator4SUpdate = d -> {
            assertEquals(0, ((Number) d.getValue("number1")).intValue());
            assertEquals(1, ((Number) d.getValue("number2")).intValue());
            assertEquals(-10, ((Number) d.getValue("number3")).intValue());
            assertEquals(-1, ((Number) d.getValue("number4")).intValue());
        };

        // -- setValue
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("number1", 1);
        mDoc.setValue("number2", 0);
        mDoc.setValue("number3", -1);
        mDoc.setValue("number4", -10);
        Document doc = save(mDoc, validator4Save);

        // Update:
        mDoc = doc.toMutable();
        mDoc.setValue("number1", 0);
        mDoc.setValue("number2", 1);
        mDoc.setValue("number3", -10);
        mDoc.setValue("number4", -1);
        save(mDoc, validator4SUpdate);

        // -- setNumber
        // save
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setNumber("number1", 1);
        mDoc2.setNumber("number2", 0);
        mDoc2.setNumber("number3", -1);
        mDoc2.setNumber("number4", -10);
        Document doc2 = save(mDoc2, validator4Save);

        // Update:
        mDoc2 = doc2.toMutable();
        mDoc2.setNumber("number1", 0);
        mDoc2.setNumber("number2", 1);
        mDoc2.setNumber("number3", -10);
        mDoc2.setNumber("number4", -1);
        save(mDoc2, validator4SUpdate);

        // -- setInt
        // save
        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setInt("number1", 1);
        mDoc3.setInt("number2", 0);
        mDoc3.setInt("number3", -1);
        mDoc3.setInt("number4", -10);
        Document doc3 = save(mDoc3, validator4Save);

        // Update:
        mDoc3 = doc3.toMutable();
        mDoc3.setInt("number1", 0);
        mDoc3.setInt("number2", 1);
        mDoc3.setInt("number3", -10);
        mDoc3.setInt("number4", -1);
        save(mDoc3, validator4SUpdate);

        // -- setLong
        // save
        MutableDocument mDoc4 = new MutableDocument("doc4");
        mDoc4.setLong("number1", 1);
        mDoc4.setLong("number2", 0);
        mDoc4.setLong("number3", -1);
        mDoc4.setLong("number4", -10);
        Document doc4 = save(mDoc4, validator4Save);

        // Update:
        mDoc4 = doc4.toMutable();
        mDoc4.setLong("number1", 0);
        mDoc4.setLong("number2", 1);
        mDoc4.setLong("number3", -10);
        mDoc4.setLong("number4", -1);
        save(mDoc4, validator4SUpdate);

        // -- setFloat
        // save
        MutableDocument mDoc5 = new MutableDocument("doc5");
        mDoc5.setFloat("number1", 1);
        mDoc5.setFloat("number2", 0);
        mDoc5.setFloat("number3", -1);
        mDoc5.setFloat("number4", -10);
        Document doc5 = save(mDoc5, validator4Save);

        // Update:
        mDoc5 = doc5.toMutable();
        mDoc5.setFloat("number1", 0);
        mDoc5.setFloat("number2", 1);
        mDoc5.setFloat("number3", -10);
        mDoc5.setFloat("number4", -1);
        save(mDoc5, validator4SUpdate);

        // -- setDouble
        // save
        MutableDocument mDoc6 = new MutableDocument("doc6");
        mDoc6.setDouble("number1", 1);
        mDoc6.setDouble("number2", 0);
        mDoc6.setDouble("number3", -1);
        mDoc6.setDouble("number4", -10);
        Document doc6 = save(mDoc6, validator4Save);

        // Update:
        mDoc6 = doc6.toMutable();
        mDoc6.setDouble("number1", 0);
        mDoc6.setDouble("number2", 1);
        mDoc6.setDouble("number3", -10);
        mDoc6.setDouble("number4", -1);
        save(mDoc6, validator4SUpdate);
    }

    @Test
    public void testGetNumber() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertNull(d.getNumber("null"));
                assertEquals(1, d.getNumber("true").intValue());
                assertEquals(0, d.getNumber("false").intValue());
                assertNull(d.getNumber("string"));
                assertEquals(0, d.getNumber("zero").intValue());
                assertEquals(1, d.getNumber("one").intValue());
                assertEquals(-1, d.getNumber("minus_one").intValue());
                assertEquals(1.1, d.getNumber("one_dot_one"));
                assertNull(d.getNumber("date"));
                assertNull(d.getNumber("dict"));
                assertNull(d.getNumber("array"));
                assertNull(d.getNumber("blob"));
                assertNull(d.getNumber("non_existing_key"));
            });
        }
    }

    @Test
    public void testGetInteger() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertEquals(0, d.getInt("null"));
                assertEquals(1, d.getInt("true"));
                assertEquals(0, d.getInt("false"));
                assertEquals(0, d.getInt("string"));
                assertEquals(0, d.getInt("zero"));
                assertEquals(1, d.getInt("one"));
                assertEquals(-1, d.getInt("minus_one"));
                assertEquals(1, d.getInt("one_dot_one"));
                assertEquals(0, d.getInt("date"));
                assertEquals(0, d.getInt("dict"));
                assertEquals(0, d.getInt("array"));
                assertEquals(0, d.getInt("blob"));
                assertEquals(0, d.getInt("non_existing_key"));
            });
        }
    }

    @Test
    public void testGetLong() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertEquals(0, d.getLong("null"));
                assertEquals(1, d.getLong("true"));
                assertEquals(0, d.getLong("false"));
                assertEquals(0, d.getLong("string"));
                assertEquals(0, d.getLong("zero"));
                assertEquals(1, d.getLong("one"));
                assertEquals(-1, d.getLong("minus_one"));
                assertEquals(1, d.getLong("one_dot_one"));
                assertEquals(0, d.getLong("date"));
                assertEquals(0, d.getLong("dict"));
                assertEquals(0, d.getLong("array"));
                assertEquals(0, d.getLong("blob"));
                assertEquals(0, d.getLong("non_existing_key"));
            });
        }
    }

    @Test
    public void testGetFloat() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertEquals(0.0f, d.getFloat("null"), 0.0f);
                assertEquals(1.0f, d.getFloat("true"), 0.0f);
                assertEquals(0.0f, d.getFloat("false"), 0.0f);
                assertEquals(0.0f, d.getFloat("string"), 0.0f);
                assertEquals(0.0f, d.getFloat("zero"), 0.0f);
                assertEquals(1.0f, d.getFloat("one"), 0.0f);
                assertEquals(-1.0f, d.getFloat("minus_one"), 0.0f);
                assertEquals(1.1f, d.getFloat("one_dot_one"), 0.0f);
                assertEquals(0.0f, d.getFloat("date"), 0.0f);
                assertEquals(0.0f, d.getFloat("dict"), 0.0f);
                assertEquals(0.0f, d.getFloat("array"), 0.0f);
                assertEquals(0.0f, d.getFloat("blob"), 0.0f);
                assertEquals(0.0f, d.getFloat("non_existing_key"), 0.0f);
            });
        }
    }

    @Test
    public void testGetDouble() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertEquals(0.0, d.getDouble("null"), 0.0);
                assertEquals(1.0, d.getDouble("true"), 0.0);
                assertEquals(0.0, d.getDouble("false"), 0.0);
                assertEquals(0.0, d.getDouble("string"), 0.0);
                assertEquals(0.0, d.getDouble("zero"), 0.0);
                assertEquals(1.0, d.getDouble("one"), 0.0);
                assertEquals(-1.0, d.getDouble("minus_one"), 0.0);
                assertEquals(1.1, d.getDouble("one_dot_one"), 0.0);
                assertEquals(0.0, d.getDouble("date"), 0.0);
                assertEquals(0.0, d.getDouble("dict"), 0.0);
                assertEquals(0.0, d.getDouble("array"), 0.0);
                assertEquals(0.0, d.getDouble("blob"), 0.0);
                assertEquals(0.0, d.getDouble("non_existing_key"), 0.0);
            });
        }
    }

    @Test
    public void testSetGetMinMaxNumbers() throws CouchbaseLiteException {
        Validator<Document> validator = doc -> {
            assertEquals(Integer.MIN_VALUE, doc.getNumber("min_int").intValue());
            assertEquals(Integer.MAX_VALUE, doc.getNumber("max_int").intValue());
            assertEquals(Integer.MIN_VALUE, ((Number) doc.getValue("min_int")).intValue());
            assertEquals(Integer.MAX_VALUE, ((Number) doc.getValue("max_int")).intValue());
            assertEquals(Integer.MIN_VALUE, doc.getInt("min_int"));
            assertEquals(Integer.MAX_VALUE, doc.getInt("max_int"));

            assertEquals(Long.MIN_VALUE, doc.getNumber("min_long"));
            assertEquals(Long.MAX_VALUE, doc.getNumber("max_long"));
            assertEquals(Long.MIN_VALUE, doc.getValue("min_long"));
            assertEquals(Long.MAX_VALUE, doc.getValue("max_long"));
            assertEquals(Long.MIN_VALUE, doc.getLong("min_long"));
            assertEquals(Long.MAX_VALUE, doc.getLong("max_long"));

            assertEquals(Float.MIN_VALUE, doc.getNumber("min_float"));
            assertEquals(Float.MAX_VALUE, doc.getNumber("max_float"));
            assertEquals(Float.MIN_VALUE, doc.getValue("min_float"));
            assertEquals(Float.MAX_VALUE, doc.getValue("max_float"));
            assertEquals(Float.MIN_VALUE, doc.getFloat("min_float"), 0.0f);
            assertEquals(Float.MAX_VALUE, doc.getFloat("max_float"), 0.0f);

            assertEquals(Double.MIN_VALUE, doc.getNumber("min_double"));
            assertEquals(Double.MAX_VALUE, doc.getNumber("max_double"));
            assertEquals(Double.MIN_VALUE, doc.getValue("min_double"));
            assertEquals(Double.MAX_VALUE, doc.getValue("max_double"));
            assertEquals(Double.MIN_VALUE, doc.getDouble("min_double"), 0.0);
            assertEquals(Double.MAX_VALUE, doc.getDouble("max_double"), 0.0);
        };

        // -- setValue
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("min_int", Integer.MIN_VALUE);
        doc.setValue("max_int", Integer.MAX_VALUE);
        doc.setValue("min_long", Long.MIN_VALUE);
        doc.setValue("max_long", Long.MAX_VALUE);
        doc.setValue("min_float", Float.MIN_VALUE);
        doc.setValue("max_float", Float.MAX_VALUE);
        doc.setValue("min_double", Double.MIN_VALUE);
        doc.setValue("max_double", Double.MAX_VALUE);
        save(doc, validator);

        // -- setInt, setLong, setFloat, setDouble
        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setInt("min_int", Integer.MIN_VALUE);
        doc2.setInt("max_int", Integer.MAX_VALUE);
        doc2.setLong("min_long", Long.MIN_VALUE);
        doc2.setLong("max_long", Long.MAX_VALUE);
        doc2.setFloat("min_float", Float.MIN_VALUE);
        doc2.setFloat("max_float", Float.MAX_VALUE);
        doc2.setDouble("min_double", Double.MIN_VALUE);
        doc2.setDouble("max_double", Double.MAX_VALUE);
        save(doc2, validator);
    }


    @Test
    public void testSetGetFloatNumbers() throws CouchbaseLiteException {
        Validator<Document> validator = doc -> {
            assertEquals(1.00, ((Number) doc.getValue("number1")).doubleValue(), 0.00001);
            assertEquals(1.00, doc.getNumber("number1").doubleValue(), 0.00001);
            assertEquals(1, doc.getInt("number1"));
            assertEquals(1L, doc.getLong("number1"));
            assertEquals(1.00F, doc.getFloat("number1"), 0.00001F);
            assertEquals(1.00, doc.getDouble("number1"), 0.00001);

            assertEquals(1.49, ((Number) doc.getValue("number2")).doubleValue(), 0.00001);
            assertEquals(1.49, doc.getNumber("number2").doubleValue(), 0.00001);
            assertEquals(1, doc.getInt("number2"));
            assertEquals(1L, doc.getLong("number2"));
            assertEquals(1.49F, doc.getFloat("number2"), 0.00001F);
            assertEquals(1.49, doc.getDouble("number2"), 0.00001);

            assertEquals(1.50, ((Number) doc.getValue("number3")).doubleValue(), 0.00001);
            assertEquals(1.50, doc.getNumber("number3").doubleValue(), 0.00001);
            assertEquals(1, doc.getInt("number3"));
            assertEquals(1L, doc.getLong("number3"));
            assertEquals(1.50F, doc.getFloat("number3"), 0.00001F);
            assertEquals(1.50, doc.getDouble("number3"), 0.00001);

            assertEquals(1.51, ((Number) doc.getValue("number4")).doubleValue(), 0.00001);
            assertEquals(1.51, doc.getNumber("number4").doubleValue(), 0.00001);
            assertEquals(1, doc.getInt("number4"));
            assertEquals(1L, doc.getLong("number4"));
            assertEquals(1.51F, doc.getFloat("number4"), 0.00001F);
            assertEquals(1.51, doc.getDouble("number4"), 0.00001);

            assertEquals(1.99, ((Number) doc.getValue("number5")).doubleValue(), 0.00001);// return 1
            assertEquals(1.99, doc.getNumber("number5").doubleValue(), 0.00001);  // return 1
            assertEquals(1, doc.getInt("number5"));
            assertEquals(1L, doc.getLong("number5"));
            assertEquals(1.99F, doc.getFloat("number5"), 0.00001F);
            assertEquals(1.99, doc.getDouble("number5"), 0.00001);
        };


        // -- setValue
        MutableDocument doc = new MutableDocument("doc1");

        doc.setValue("number1", 1.00);
        doc.setValue("number2", 1.49);
        doc.setValue("number3", 1.50);
        doc.setValue("number4", 1.51);
        doc.setValue("number5", 1.99);
        save(doc, validator);

        // -- setFloat
        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setFloat("number1", 1.00f);
        doc2.setFloat("number2", 1.49f);
        doc2.setFloat("number3", 1.50f);
        doc2.setFloat("number4", 1.51f);
        doc2.setFloat("number5", 1.99f);
        save(doc2, validator);

        // -- setDouble
        MutableDocument doc3 = new MutableDocument("doc3");
        doc3.setDouble("number1", 1.00);
        doc3.setDouble("number2", 1.49);
        doc3.setDouble("number3", 1.50);
        doc3.setDouble("number4", 1.51);
        doc3.setDouble("number5", 1.99);
        save(doc3, validator);
    }

    @Test
    public void testSetBoolean() throws CouchbaseLiteException {
        Validator<Document> validator4Save = d -> {
            assertEquals(true, d.getValue("boolean1"));
            assertEquals(false, d.getValue("boolean2"));
            assertEquals(true, d.getBoolean("boolean1"));
            assertEquals(false, d.getBoolean("boolean2"));
        };
        Validator<Document> validator4Update = d -> {
            assertEquals(false, d.getValue("boolean1"));
            assertEquals(true, d.getValue("boolean2"));
            assertEquals(false, d.getBoolean("boolean1"));
            assertEquals(true, d.getBoolean("boolean2"));
        };

        // -- setValue
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("boolean1", true);
        mDoc.setValue("boolean2", false);
        Document doc = save(mDoc, validator4Save);

        // Update:
        mDoc = doc.toMutable();
        mDoc.setValue("boolean1", false);
        mDoc.setValue("boolean2", true);
        save(mDoc, validator4Update);

        // -- setBoolean
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setValue("boolean1", true);
        mDoc2.setValue("boolean2", false);
        Document doc2 = save(mDoc2, validator4Save);

        // Update:
        mDoc2 = doc2.toMutable();
        mDoc2.setValue("boolean1", false);
        mDoc2.setValue("boolean2", true);
        save(mDoc2, validator4Update);
    }

    @Test
    public void testGetBoolean() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertEquals(false, d.getBoolean("null"));
                assertEquals(true, d.getBoolean("true"));
                assertEquals(false, d.getBoolean("false"));
                assertEquals(true, d.getBoolean("string"));
                assertEquals(false, d.getBoolean("zero"));
                assertEquals(true, d.getBoolean("one"));
                assertEquals(true, d.getBoolean("minus_one"));
                assertEquals(true, d.getBoolean("one_dot_one"));
                assertEquals(true, d.getBoolean("date"));
                assertEquals(true, d.getBoolean("dict"));
                assertEquals(true, d.getBoolean("array"));
                assertEquals(true, d.getBoolean("blob"));
                assertEquals(false, d.getBoolean("non_existing_key"));
            });
        }
    }

    @Test
    public void testSetDate() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("doc1");

        Date date = new Date();
        final String dateStr = DateUtils.toJson(date);
        assertTrue(dateStr.length() > 0);
        mDoc.setValue("date", date);

        Document doc = save(mDoc, d -> {
            assertEquals(dateStr, d.getValue("date"));
            assertEquals(dateStr, d.getString("date"));
            assertEquals(dateStr, DateUtils.toJson(d.getDate("date")));
        });

        // Update:
        mDoc = doc.toMutable();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, 60);
        Date nuDate = cal.getTime();
        final String nuDateStr = DateUtils.toJson(nuDate);
        mDoc.setValue("date", nuDate);

        save(mDoc, d -> {
            assertEquals(nuDateStr, d.getValue("date"));
            assertEquals(nuDateStr, d.getString("date"));
            assertEquals(nuDateStr, DateUtils.toJson(d.getDate("date")));
        });
    }

    @Test
    public void testGetDate() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertNull(d.getDate("null"));
                assertNull(d.getDate("true"));
                assertNull(d.getDate("false"));
                assertNull(d.getDate("string"));
                assertNull(d.getDate("zero"));
                assertNull(d.getDate("one"));
                assertNull(d.getDate("minus_one"));
                assertNull(d.getDate("one_dot_one"));
                assertEquals(TEST_DATE, DateUtils.toJson(d.getDate("date")));
                assertNull(d.getDate("dict"));
                assertNull(d.getDate("array"));
                assertNull(d.getDate("blob"));
                assertNull(d.getDate("non_existing_key"));
            });
        }
    }

    @Test
    public void testSetBlob() throws CouchbaseLiteException {
        final Blob blob = new Blob("text/plain", TEST_BLOB.getBytes());
        final Blob nuBlob = new Blob("text/plain", "1234567890".getBytes());

        Validator<Document> validator4Save = d -> {
            assertEquals(blob.getProperties().get("length"), d.getBlob("blob").getProperties().get("length"));
            assertEquals(
                blob.getProperties().get("content-type"),
                d.getBlob("blob").getProperties().get("content-type"));
            assertEquals(blob.getProperties().get("digest"), d.getBlob("blob").getProperties().get("digest"));
            assertEquals(
                blob.getProperties().get("length"),
                ((Blob) d.getValue("blob")).getProperties().get("length"));
            assertEquals(
                blob.getProperties().get("content-type"),
                ((Blob) d.getValue("blob")).getProperties().get("content-type"));
            assertEquals(
                blob.getProperties().get("digest"),
                ((Blob) d.getValue("blob")).getProperties().get("digest"));
            assertEquals(TEST_BLOB, new String(d.getBlob("blob").getContent()));
            assertTrue(Arrays.equals(TEST_BLOB.getBytes(), d.getBlob("blob").getContent()));
        };

        Validator<Document> validator4Update = d -> {
            assertEquals(nuBlob.getProperties().get("length"), d.getBlob("blob").getProperties().get("length"));
            assertEquals(
                nuBlob.getProperties().get("content-type"),
                d.getBlob("blob").getProperties().get("content-type"));
            assertEquals(nuBlob.getProperties().get("digest"), d.getBlob("blob").getProperties().get("digest"));
            assertEquals(
                nuBlob.getProperties().get("length"),
                ((Blob) d.getValue("blob")).getProperties().get("length"));
            assertEquals(
                nuBlob.getProperties().get("content-type"),
                ((Blob) d.getValue("blob")).getProperties().get("content-type"));
            assertEquals(
                nuBlob.getProperties().get("digest"),
                ((Blob) d.getValue("blob")).getProperties().get("digest"));
            assertEquals("1234567890", new String(d.getBlob("blob").getContent()));
            assertTrue(Arrays.equals("1234567890".getBytes(), d.getBlob("blob").getContent()));
        };

        // --setValue
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("blob", blob);
        Document doc = save(mDoc, validator4Save);

        // Update:
        mDoc = doc.toMutable();
        mDoc.setValue("blob", nuBlob);
        save(mDoc, validator4Update);

        // --setBlob
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setBlob("blob", blob);
        Document doc2 = save(mDoc2, validator4Save);

        // Update:
        mDoc2 = doc2.toMutable();
        mDoc2.setBlob("blob", nuBlob);
        save(mDoc2, validator4Update);
    }

    @Test
    public void testGetBlob() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertNull(d.getBlob("null"));
                assertNull(d.getBlob("true"));
                assertNull(d.getBlob("false"));
                assertNull(d.getBlob("string"));
                assertNull(d.getBlob("zero"));
                assertNull(d.getBlob("one"));
                assertNull(d.getBlob("minus_one"));
                assertNull(d.getBlob("one_dot_one"));
                assertNull(d.getBlob("date"));
                assertNull(d.getBlob("dict"));
                assertNull(d.getBlob("array"));
                assertEquals(TEST_BLOB, new String(d.getBlob("blob").getContent()));
                assertTrue(Arrays.equals(
                    TEST_BLOB.getBytes(),
                    d.getBlob("blob").getContent()));
                assertNull(d.getBlob("non_existing_key"));
            });
        }
    }

    @Test
    public void testSetDictionary() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            // -- setValue
            MutableDocument mDoc = new MutableDocument(docID);
            MutableDictionary mDict = new MutableDictionary();
            mDict.setValue("street", "1 Main street");
            if (i % 2 == 1) { mDoc.setValue("dict", mDict); }
            else { mDoc.setDictionary("dict", mDict); }
            assertEquals(mDict, mDoc.getValue("dict"));
            assertEquals(mDict.toMap(), ((MutableDictionary) mDoc.getValue("dict")).toMap());

            Document doc = save(mDoc);

            assertTrue(mDict != doc.getValue("dict"));
            assertEquals(doc.getValue("dict"), doc.getDictionary("dict"));

            Dictionary dict = (Dictionary) doc.getValue("dict");
            dict = dict instanceof MutableDictionary ? dict : dict.toMutable();
            assertEquals(mDict.toMap(), dict.toMap());

            // Update:
            mDoc = doc.toMutable();
            mDict = mDoc.getDictionary("dict");
            mDict.setValue("city", "Mountain View");
            assertEquals(doc.getValue("dict"), doc.getDictionary("dict"));
            Map<String, Object> map = new HashMap<>();
            map.put("street", "1 Main street");
            map.put("city", "Mountain View");
            assertEquals(map, mDoc.getDictionary("dict").toMap());

            doc = save(mDoc);

            assertTrue(mDict != doc.getValue("dict"));
            assertEquals(doc.getValue("dict"), doc.getDictionary("dict"));
            assertEquals(map, doc.getDictionary("dict").toMap());
        }
    }

    @Test
    public void testGetDictionary() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertNull(d.getDictionary("null"));
                assertNull(d.getDictionary("true"));
                assertNull(d.getDictionary("false"));
                assertNull(d.getDictionary("string"));
                assertNull(d.getDictionary("zero"));
                assertNull(d.getDictionary("one"));
                assertNull(d.getDictionary("minus_one"));
                assertNull(d.getDictionary("one_dot_one"));
                assertNull(d.getDictionary("date"));
                assertNotNull(d.getDictionary("dict"));
                Map<String, Object> dict = new HashMap<>();
                dict.put("street", "1 Main street");
                dict.put("city", "Mountain View");
                dict.put("state", "CA");
                assertEquals(dict, d.getDictionary("dict").toMap());
                assertNull(d.getDictionary("array"));
                assertNull(d.getDictionary("blob"));
                assertNull(d.getDictionary("non_existing_key"));
            });
        }
    }

    @Test
    public void testSetArray() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument mDoc = new MutableDocument(docID);
            MutableArray array = new MutableArray();
            array.addValue("item1");
            array.addValue("item2");
            array.addValue("item3");
            if (i % 2 == 1) { mDoc.setValue("array", array); }
            else { mDoc.setArray("array", array); }
            assertEquals(array, mDoc.getValue("array"));
            assertEquals(array.toList(), ((MutableArray) mDoc.getValue("array")).toList());

            Document doc = save(mDoc);
            assertTrue(array != doc.getValue("array"));
            assertEquals(doc.getValue("array"), doc.getArray("array"));

            Array mArray = (Array) doc.getValue("array");
            mArray = array instanceof MutableArray ? array : array.toMutable();
            assertEquals(array.toList(), mArray.toList());

            // Update:
            mDoc = doc.toMutable();
            array = mDoc.getArray("array");
            array.addValue("item4");
            array.addValue("item5");
            doc = save(mDoc);
            assertTrue(array != doc.getValue("array"));
            assertEquals(doc.getValue("array"), doc.getArray("array"));
            List<String> list = Arrays.asList("item1", "item2", "item3", "item4", "item5");
            assertEquals(list, doc.getArray("array").toList());
        }
    }

    @Test
    public void testGetArray() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            save(doc, d -> {
                assertNull(d.getArray("null"));
                assertNull(d.getArray("true"));
                assertNull(d.getArray("false"));
                assertNull(d.getArray("string"));
                assertNull(d.getArray("zero"));
                assertNull(d.getArray("one"));
                assertNull(d.getArray("minus_one"));
                assertNull(d.getArray("one_dot_one"));
                assertNull(d.getArray("date"));
                assertNull(d.getArray("dict"));
                assertNotNull(d.getArray("array"));
                List<Object> list = Arrays.asList("650-123-0001", "650-123-0002");
                assertEquals(list, d.getArray("array").toList());
                assertNull(d.getArray("blob"));
                assertNull(d.getArray("non_existing_key"));
            });
        }
    }

    @Test
    public void testSetNull() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("obj-null", null);
        mDoc.setString("string-null", null);
        mDoc.setNumber("number-null", null);
        mDoc.setDate("date-null", null);
        mDoc.setArray("array-null", null);
        mDoc.setDictionary("dict-null", null);
        // TODO: NOTE: Current implementation follows iOS way. So set null remove it!!
        save(mDoc, d -> {
            assertEquals(6, d.count());
            assertTrue(d.contains("obj-null"));
            assertTrue(d.contains("string-null"));
            assertTrue(d.contains("number-null"));
            assertTrue(d.contains("date-null"));
            assertTrue(d.contains("array-null"));
            assertTrue(d.contains("dict-null"));
            assertNull(d.getValue("obj-null"));
            assertNull(d.getValue("string-null"));
            assertNull(d.getValue("number-null"));
            assertNull(d.getValue("date-null"));
            assertNull(d.getValue("array-null"));
            assertNull(d.getValue("dict-null"));
        });
    }

    @Test
    public void testSetMap() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("address", dict);

        MutableDictionary address = doc.getDictionary("address");
        assertNotNull(address);
        assertEquals(address, doc.getValue("address"));
        assertEquals("1 Main street", address.getString("street"));
        assertEquals("Mountain View", address.getString("city"));
        assertEquals("CA", address.getString("state"));
        assertEquals(dict, address.toMap());

        // Update with a new dictionary:
        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("street", "1 Second street");
        nuDict.put("city", "Palo Alto");
        nuDict.put("state", "CA");
        doc.setValue("address", nuDict);

        // Check whether the old address dictionary is still accessible:
        assertTrue(address != doc.getDictionary("address"));
        assertEquals("1 Main street", address.getString("street"));
        assertEquals("Mountain View", address.getString("city"));
        assertEquals("CA", address.getString("state"));
        assertEquals(dict, address.toMap());

        // The old address dictionary should be detached:
        MutableDictionary nuAddress = doc.getDictionary("address");
        assertTrue(address != nuAddress);

        // Update nuAddress:
        nuAddress.setValue("zip", "94302");
        assertEquals("94302", nuAddress.getString("zip"));
        assertNull(address.getString("zip"));

        // Save:
        Document savedDoc = save(doc);
        //doc = db.getDocument(doc.getId());

        nuDict.put("zip", "94302");
        Map<String, Object> expected = new HashMap<>();
        expected.put("address", nuDict);
        assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testSetList() throws CouchbaseLiteException {
        List<String> array = Arrays.asList("a", "b", "c");

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("members", array);

        MutableArray members = doc.getArray("members");
        assertNotNull(members);
        assertEquals(members, doc.getValue("members"));

        assertEquals(3, members.count());
        assertEquals("a", members.getValue(0));
        assertEquals("b", members.getValue(1));
        assertEquals("c", members.getValue(2));
        assertEquals(array, members.toList());

        // Update with a new array:
        List<String> nuArray = Arrays.asList("d", "e", "f");
        doc.setValue("members", nuArray);

        // Check whether the old members array is still accessible:
        assertEquals(3, members.count());
        assertEquals("a", members.getValue(0));
        assertEquals("b", members.getValue(1));
        assertEquals("c", members.getValue(2));
        assertEquals(array, members.toList());

        // The old members array should be detached:
        MutableArray nuMembers = doc.getArray("members");
        assertTrue(members != nuMembers);

        // Update nuMembers:
        nuMembers.addValue("g");
        assertEquals(4, nuMembers.count());
        assertEquals("g", nuMembers.getValue(3));
        assertEquals(3, members.count());

        // Save
        Document savedDoc = save(doc);

        Map<String, Object> expected = new HashMap<>();
        expected.put("members", Arrays.asList("d", "e", "f", "g"));
        assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testUpdateNestedDictionary() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        MutableDictionary addresses = new MutableDictionary();
        doc.setValue("addresses", addresses);

        MutableDictionary shipping = new MutableDictionary();
        shipping.setValue("street", "1 Main street");
        shipping.setValue("city", "Mountain View");
        shipping.setValue("state", "CA");
        addresses.setValue("shipping", shipping);

        doc = save(doc).toMutable();

        shipping = doc.getDictionary("addresses").getDictionary("shipping");
        shipping.setValue("zip", "94042");

        doc = save(doc).toMutable();

        Map<String, Object> mapShipping = new HashMap<>();
        mapShipping.put("street", "1 Main street");
        mapShipping.put("city", "Mountain View");
        mapShipping.put("state", "CA");
        mapShipping.put("zip", "94042");
        Map<String, Object> mapAddresses = new HashMap<>();
        mapAddresses.put("shipping", mapShipping);
        Map<String, Object> expected = new HashMap<>();
        expected.put("addresses", mapAddresses);

        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateDictionaryInArray() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray addresses = new MutableArray();
        doc.setValue("addresses", addresses);

        MutableDictionary address1 = new MutableDictionary();
        address1.setValue("street", "1 Main street");
        address1.setValue("city", "Mountain View");
        address1.setValue("state", "CA");
        addresses.addValue(address1);

        MutableDictionary address2 = new MutableDictionary();
        address2.setValue("street", "1 Second street");
        address2.setValue("city", "Palo Alto");
        address2.setValue("state", "CA");
        addresses.addValue(address2);

        doc = save(doc).toMutable();

        address1 = doc.getArray("addresses").getDictionary(0);
        address1.setValue("street", "2 Main street");
        address1.setValue("zip", "94042");

        address2 = doc.getArray("addresses").getDictionary(1);
        address2.setValue("street", "2 Second street");
        address2.setValue("zip", "94302");

        doc = save(doc).toMutable();

        Map<String, Object> mapAddress1 = new HashMap<>();
        mapAddress1.put("street", "2 Main street");
        mapAddress1.put("city", "Mountain View");
        mapAddress1.put("state", "CA");
        mapAddress1.put("zip", "94042");

        Map<String, Object> mapAddress2 = new HashMap<>();
        mapAddress2.put("street", "2 Second street");
        mapAddress2.put("city", "Palo Alto");
        mapAddress2.put("state", "CA");
        mapAddress2.put("zip", "94302");

        Map<String, Object> expected = new HashMap<>();
        expected.put("addresses", Arrays.asList(mapAddress1, mapAddress2));

        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateNestedArray() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray groups = new MutableArray();
        doc.setValue("groups", groups);

        MutableArray group1 = new MutableArray();
        group1.addValue("a");
        group1.addValue("b");
        group1.addValue("c");
        groups.addValue(group1);

        MutableArray group2 = new MutableArray();
        group2.addValue(1);
        group2.addValue(2);
        group2.addValue(3);
        groups.addValue(group2);

        doc = save(doc).toMutable();

        group1 = doc.getArray("groups").getArray(0);
        group1.setValue(0, "d");
        group1.setValue(1, "e");
        group1.setValue(2, "f");

        group2 = doc.getArray("groups").getArray(1);
        group2.setValue(0, 4);
        group2.setValue(1, 5);
        group2.setValue(2, 6);

        doc = save(doc).toMutable();

        Map<String, Object> expected = new HashMap<>();
        expected.put("groups", Arrays.asList(
            Arrays.asList("d", "e", "f"),
            Arrays.asList(4L, 5L, 6L)));
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateArrayInDictionary() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary group1 = new MutableDictionary();
        MutableArray member1 = new MutableArray();
        member1.addValue("a");
        member1.addValue("b");
        member1.addValue("c");
        group1.setValue("member", member1);
        doc.setValue("group1", group1);

        MutableDictionary group2 = new MutableDictionary();
        MutableArray member2 = new MutableArray();
        member2.addValue(1);
        member2.addValue(2);
        member2.addValue(3);
        group2.setValue("member", member2);
        doc.setValue("group2", group2);

        doc = save(doc).toMutable();

        member1 = doc.getDictionary("group1").getArray("member");
        member1.setValue(0, "d");
        member1.setValue(1, "e");
        member1.setValue(2, "f");

        member2 = doc.getDictionary("group2").getArray("member");
        member2.setValue(0, 4);
        member2.setValue(1, 5);
        member2.setValue(2, 6);

        doc = save(doc).toMutable();

        Map<String, Object> expected = new HashMap<>();
        Map<String, Object> mapGroup1 = new HashMap<>();
        mapGroup1.put("member", Arrays.asList("d", "e", "f"));
        Map<String, Object> mapGroup2 = new HashMap<>();
        mapGroup2.put("member", Arrays.asList(4L, 5L, 6L));
        expected.put("group1", mapGroup1);
        expected.put("group2", mapGroup2);
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testSetDictionaryToMultipleKeys() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary address = new MutableDictionary();
        address.setValue("street", "1 Main street");
        address.setValue("city", "Mountain View");
        address.setValue("state", "CA");
        doc.setValue("shipping", address);
        doc.setValue("billing", address);

        // Update address: both shipping and billing should get the update.
        address.setValue("zip", "94042");
        assertEquals("94042", doc.getDictionary("shipping").getString("zip"));
        assertEquals("94042", doc.getDictionary("billing").getString("zip"));

        doc = save(doc).toMutable();

        MutableDictionary shipping = doc.getDictionary("shipping");
        MutableDictionary billing = doc.getDictionary("billing");

        // After save: both shipping and billing address are now independent to each other
        assertTrue(shipping != address);
        assertTrue(billing != address);
        assertTrue(shipping != billing);

        shipping.setValue("street", "2 Main street");
        billing.setValue("street", "3 Main street");

        // Save update:
        doc = save(doc).toMutable();
        assertEquals("2 Main street", doc.getDictionary("shipping").getString("street"));
        assertEquals("3 Main street", doc.getDictionary("billing").getString("street"));
    }

    @Test
    public void testSetArrayToMultipleKeys() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");

        MutableArray phones = new MutableArray();
        phones.addValue("650-000-0001");
        phones.addValue("650-000-0002");

        doc.setValue("mobile", phones);
        doc.setValue("home", phones);

        assertEquals(phones, doc.getValue("mobile"));
        assertEquals(phones, doc.getValue("home"));

        // Update phones: both mobile and home should get the update
        phones.addValue("650-000-0003");

        assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003"),
            doc.getArray("mobile").toList());
        assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003"),
            doc.getArray("home").toList());

        doc = save(doc).toMutable();

        // After save: both mobile and home are not independent to each other
        MutableArray mobile = doc.getArray("mobile");
        MutableArray home = doc.getArray("home");
        assertTrue(mobile != phones);
        assertTrue(home != phones);
        assertTrue(mobile != home);

        // Update mobile and home:
        mobile.addValue("650-000-1234");
        home.addValue("650-000-5678");

        // Save update:
        doc = save(doc).toMutable();

        assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003", "650-000-1234"),
            doc.getArray("mobile").toList());
        assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003", "650-000-5678"),
            doc.getArray("home").toList());
    }

    @Test
    public void testToDictionary() {
        MutableDocument doc1 = new MutableDocument("doc1");
        populateData(doc1);

        Map<String, Object> expected = new HashMap<>();
        expected.put("true", true);
        expected.put("false", false);
        expected.put("string", "string");
        expected.put("zero", 0);
        expected.put("one", 1);
        expected.put("minus_one", -1);
        expected.put("one_dot_one", 1.1);
        // TODO: Should be Date or String?
        //expected.put("date", DateUtils.fromJson(TEST_DATE));
        expected.put("date", TEST_DATE);
        expected.put("null", null);

        // Dictionary:
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");
        expected.put("dict", dict);

        // Array:
        List<Object> array = new ArrayList<>();
        array.add("650-123-0001");
        array.add("650-123-0002");
        expected.put("array", array);

        // Blob:
        byte[] content = TEST_BLOB.getBytes();
        Blob blob = new Blob("text/plain", content);
        expected.put("blob", blob);

        assertEquals(expected, doc1.toMap());
    }

    @Test
    public void testCount() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }

            assertEquals(12, doc.count());
            assertEquals(12, doc.toMap().size());

            doc = save(doc).toMutable();

            assertEquals(12, doc.count());
            assertEquals(12, doc.toMap().size());
        }
    }

    @Test
    public void testRemoveKeys() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        Map<String, Object> mapAddress = new HashMap<>();
        mapAddress.put("street", "1 milky way.");
        mapAddress.put("city", "galaxy city");
        mapAddress.put("zip", 12345);
        Map<String, Object> profile = new HashMap<>();
        profile.put("type", "profile");
        profile.put("name", "Jason");
        profile.put("weight", 130.5);
        profile.put("active", true);
        profile.put("age", 30);
        profile.put("address", mapAddress);
        doc.setData(profile);

        save(doc);

        doc.remove("name");
        doc.remove("weight");
        doc.remove("age");
        doc.remove("active");
        doc.getDictionary("address").remove("city");

        assertNull(doc.getString("name"));
        assertEquals(0.0F, doc.getFloat("weight"), 0.0F);
        assertEquals(0.0, doc.getDouble("weight"), 0.0);
        assertEquals(0, doc.getInt("age"));
        assertFalse(doc.getBoolean("active"));

        assertNull(doc.getValue("name"));
        assertNull(doc.getValue("weight"));
        assertNull(doc.getValue("age"));
        assertNull(doc.getValue("active"));
        assertNull(doc.getDictionary("address").getValue("city"));

        MutableDictionary address = doc.getDictionary("address");
        Map<String, Object> addr = new HashMap<>();
        addr.put("street", "1 milky way.");
        addr.put("zip", 12345);
        assertEquals(addr, address.toMap());
        Map<String, Object> expected = new HashMap<>();
        expected.put("type", "profile");
        expected.put("address", addr);
        assertEquals(expected, doc.toMap());

        doc.remove("type");
        doc.remove("address");
        assertNull(doc.getValue("type"));
        assertNull(doc.getValue("address"));
        assertEquals(new HashMap<>(), doc.toMap());
    }

    @Test
    public void testRemoveKeysBySettingDictionary() throws CouchbaseLiteException {
        Map<String, Object> props = new HashMap<>();
        props.put("PropName1", "Val1");
        props.put("PropName2", 42);

        MutableDocument mDoc = new MutableDocument("docName", props);
        save(mDoc);

        Map<String, Object> newProps = new HashMap<>();
        props.put("PropName3", "Val3");
        props.put("PropName4", 84);

        MutableDocument existingDoc = db.getDocument("docName").toMutable();
        existingDoc.setData(newProps);
        save(existingDoc);

        assertEquals(newProps, existingDoc.toMap());
    }

    @Test
    public void testContainsKey() {
        MutableDocument doc = new MutableDocument("doc1");
        Map<String, Object> mapAddress = new HashMap<>();
        mapAddress.put("street", "1 milky way.");
        Map<String, Object> profile = new HashMap<>();
        profile.put("type", "profile");
        profile.put("name", "Jason");
        profile.put("age", 30);
        profile.put("address", mapAddress);
        doc.setData(profile);

        assertTrue(doc.contains("type"));
        assertTrue(doc.contains("name"));
        assertTrue(doc.contains("age"));
        assertTrue(doc.contains("address"));
        assertFalse(doc.contains("weight"));
    }

    @Test
    public void testDeleteNewDocument() {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setString("name", "Scott Tiger");
        try {
            db.delete(mDoc);
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
        assertEquals("Scott Tiger", mDoc.getString("name"));
    }

    @Test
    public void testDeleteDocument() throws CouchbaseLiteException {
        String docID = "doc1";
        MutableDocument mDoc = new MutableDocument(docID);
        mDoc.setValue("name", "Scott Tiger");

        // Save:
        Document doc = save(mDoc);

        // Delete:
        db.delete(doc);

        assertNull(db.getDocument(docID));

        // NOTE: doc is reserved.
        Object v = doc.getValue("name");
        assertEquals("Scott Tiger", v);
        Map<String, Object> expected = new HashMap<>();
        expected.put("name", "Scott Tiger");
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testDictionaryAfterDeleteDocument() throws CouchbaseLiteException {
        Map<String, Object> addr = new HashMap<>();
        addr.put("street", "1 Main street");
        addr.put("city", "Mountain View");
        addr.put("state", "CA");
        Map<String, Object> dict = new HashMap<>();
        dict.put("address", addr);

        MutableDocument mDoc = new MutableDocument("doc1", dict);
        Document doc = save(mDoc);

        Dictionary address = doc.getDictionary("address");
        assertEquals("1 Main street", address.getValue("street"));
        assertEquals("Mountain View", address.getValue("city"));
        assertEquals("CA", address.getValue("state"));

        db.delete(doc);

        // The dictionary still has data but is detached:
        assertEquals("1 Main street", address.getValue("street"));
        assertEquals("Mountain View", address.getValue("city"));
        assertEquals("CA", address.getValue("state"));
    }

    @Test
    public void testArrayAfterDeleteDocument() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("members", Arrays.asList("a", "b", "c"));

        MutableDocument mDoc = new MutableDocument("doc1", dict);
        Document doc = save(mDoc);

        Array members = doc.getArray("members");
        assertEquals(3, members.count());
        assertEquals("a", members.getValue(0));
        assertEquals("b", members.getValue(1));
        assertEquals("c", members.getValue(2));

        db.delete(doc);

        // The array still has data but is detached:
        assertEquals(3, members.count());
        assertEquals("a", members.getValue(0));
        assertEquals("b", members.getValue(1));
        assertEquals("c", members.getValue(2));
    }

    @Test
    public void testDocumentChangeOnDocumentPurged()
        throws Exception {
        Date dto1 = new Date(System.currentTimeMillis() + 1000L);
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("theanswer", 18);
        db.save(doc1);

        // purge doc
        final CountDownLatch latch = new CountDownLatch(1);
        DocumentChangeListener changeListener = change -> {
            assertNotNull(change);
            assertEquals("doc1", change.getDocumentID());
            assertEquals(1, latch.getCount());
            latch.countDown();
        };
        ListenerToken token = db.addDocumentChangeListener("doc1", changeListener);
        db.setDocumentExpiration("doc1", dto1);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        db.removeChangeListener(token);
    }

    @Test
    public void testPurgeDocument() throws CouchbaseLiteException {
        String docID = "doc1";
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("type", "profile");
        doc.setValue("name", "Scott");

        // Purge before save:
        try {
            db.purge(doc);
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
        assertEquals("profile", doc.getValue("type"));
        assertEquals("Scott", doc.getValue("name"));

        //Save
        Document savedDoc = save(doc);

        // purge
        db.purge(savedDoc);
        assertNull(db.getDocument(docID));
    }

    @Test
    public void testPurgeDocumentById() throws CouchbaseLiteException {
        String docID = "doc1";
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("type", "profile");
        doc.setValue("name", "Scott");

        // Purge before save:
        try {
            db.purge(docID);
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
        assertEquals("profile", doc.getValue("type"));
        assertEquals("Scott", doc.getValue("name"));

        //Save
        Document savedDoc = save(doc);

        // purge
        db.purge(docID);
        assertNull(db.getDocument(docID));
    }

    @Test
    public void testSetAndGetExpirationFromDoc() throws CouchbaseLiteException {
        Date dto30 = new Date(System.currentTimeMillis() + 30000L);
        Date dto0 = new Date(System.currentTimeMillis());

        MutableDocument doc1a = new MutableDocument("doc1");
        MutableDocument doc1b = new MutableDocument("doc2");
        MutableDocument doc1c = new MutableDocument("doc3");
        doc1a.setInt("answer", 12);
        doc1a.setValue("question", "What is six plus six?");
        save(doc1a);

        doc1b.setInt("answer", 22);
        doc1b.setValue("question", "What is eleven plus eleven?");
        save(doc1b);

        doc1c.setInt("answer", 32);
        doc1c.setValue("question", "What is twenty plus twelve?");
        save(doc1c);

        db.setDocumentExpiration("doc1", dto30);
        db.setDocumentExpiration("doc3", dto30);

        db.setDocumentExpiration("doc3", null);
        Date exp = db.getDocumentExpiration("doc1");
        assertEquals(exp, dto30);
        assertNull(db.getDocumentExpiration("doc2"));
        assertNull(db.getDocumentExpiration("doc3"));
    }

    @Test
    public void testSetExpirationOnDoc() throws Exception {
        Date dto3 = new Date(System.currentTimeMillis() + 3000L);
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setInt("answer", 12);
        doc1.setValue("question", "What is six plus six?");
        save(doc1);

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setInt("answer", 12);
        doc2.setValue("question", "What is six plus six?");
        save(doc2);

        db.setDocumentExpiration("doc2", new Date(System.currentTimeMillis()));//expire now
        Thread.sleep(500);
        assertNull(db.getDocument("doc2"));

        db.setDocumentExpiration("doc1", dto3);

        Thread.sleep(3 * 1000); // sleep 4 sec

        assertNull(db.getDocument("doc1"));
    }

    @Test
    public void testSetExpirationOnDeletedDoc() throws CouchbaseLiteException {
        Date dto30 = new Date(System.currentTimeMillis() + 30000L);
        MutableDocument doc1a = new MutableDocument("deleted_doc");
        doc1a.setInt("answer", 12);
        doc1a.setValue("question", "What is six plus six?");
        save(doc1a);
        db.delete(doc1a);
        try {
            db.setDocumentExpiration("deleted_doc", dto30);
        }
        catch (CouchbaseLiteException e) {
            e.printStackTrace();
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
    }

    @Test
    public void testGetExpirationFromDeletedDoc() throws CouchbaseLiteException {
        Date dto30 = new Date(System.currentTimeMillis() + 30000L);
        MutableDocument doc1a = new MutableDocument("deleted_doc");
        doc1a.setInt("answer", 12);
        doc1a.setValue("question", "What is six plus six?");
        save(doc1a);
        db.delete(doc1a);
        try {
            db.getDocumentExpiration("deleted_doc");
        }
        catch (CouchbaseLiteException e) {
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
    }

    @Test
    public void testSetExpirationOnNoneExistDoc() {
        Date dto30 = new Date(System.currentTimeMillis() + 30000L);
        try {
            db.setDocumentExpiration("not_exist", dto30);
        }
        catch (CouchbaseLiteException e) {
            e.printStackTrace();
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
    }

    @Test
    public void testGetExpirationFromNoneExistDoc() {
        Date dto30 = new Date(System.currentTimeMillis() + 30000L);
        try {
            db.getDocumentExpiration("not_exist");
        }
        catch (CouchbaseLiteException e) {
            assertEquals(e.getCode(), CBLError.Code.NOT_FOUND);
        }
    }

    @Test
    public void testLongExpiration() throws Exception {
        Date now = new Date(System.currentTimeMillis());
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DATE, 60);
        Date d60Days = c.getTime();

        MutableDocument doc = new MutableDocument("doc");
        doc.setInt("answer", 42);
        doc.setValue("question", "What is twenty-one times two?");
        save(doc);

        assertNull(db.getDocumentExpiration("doc"));
        db.setDocumentExpiration("doc", d60Days);

        Date exp = db.getDocumentExpiration("doc");
        assertNotNull(exp);
        long diff = exp.getTime() - now.getTime();
        assertTrue(Math.abs(TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) - 60.0) <= 1.0);
    }

    @Test
    public void testReopenDB() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("string", "str");
        save(mDoc);

        reopenDB();

        Document doc = db.getDocument("doc1");
        assertEquals("str", doc.getString("string"));
        Map<String, Object> expected = new HashMap<>();
        expected.put("string", "str");
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testBlob() throws IOException, CouchbaseLiteException {
        byte[] content = TEST_BLOB.getBytes();

        // store blob
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Jim");
        doc.setValue("data", data);

        doc = save(doc).toMutable();

        assertEquals("Jim", doc.getValue("name"));
        assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        assertEquals(8, data.length());
        assertTrue(Arrays.equals(content, data.getContent()));
        InputStream is = data.getContentStream();
        try {
            assertNotNull(is);
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            assertEquals(8, bytesRead);
        }
        finally {
            try {
                is.close();
            }
            catch (IOException e) {
                fail();
            }
        }
    }

    @Test
    public void testEmptyBlob() throws IOException, CouchbaseLiteException {
        byte[] content = "".getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("data", data);

        doc = save(doc).toMutable();

        assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        assertEquals(0, data.length());
        assertTrue(Arrays.equals(content, data.getContent()));
        InputStream is = data.getContentStream();
        try {
            assertNotNull(is);
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            assertEquals(-1, bytesRead);
        }
        finally {
            try {
                is.close();
            }
            catch (IOException e) {
                fail();
            }
        }
    }

    @Test
    public void testBlobWithEmptyStream() throws IOException, CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        byte[] content = "".getBytes();
        InputStream stream = new ByteArrayInputStream(content);
        try {
            Blob data = new Blob("text/plain", stream);
            assertNotNull(data);
            doc.setValue("data", data);
            doc = save(doc).toMutable();
        }
        finally {
            stream.close();
        }

        assertTrue(doc.getValue("data") instanceof Blob);
        Blob data = (Blob) doc.getValue("data");
        assertEquals(0, data.length());
        assertTrue(Arrays.equals(content, data.getContent()));
        InputStream is = data.getContentStream();
        try {
            assertNotNull(is);
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            assertEquals(-1, bytesRead);
        }
        finally {
            try {
                is.close();
            }
            catch (IOException e) {
                fail();
            }
        }
    }

    @Test
    public void testMultipleBlobRead() throws IOException, CouchbaseLiteException {
        byte[] content = TEST_BLOB.getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("data", data);

        data = (Blob) doc.getValue("data");
        for (int i = 0; i < 5; i++) {
            assertTrue(Arrays.equals(content, data.getContent()));
            InputStream is = data.getContentStream();
            try {
                assertNotNull(is);
                byte[] buffer = new byte[10];
                int bytesRead = is.read(buffer);
                assertEquals(8, bytesRead);
            }
            finally {
                try {
                    is.close();
                }
                catch (IOException e) {
                    fail();
                }
            }
        }

        doc = save(doc).toMutable();

        assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        for (int i = 0; i < 5; i++) {
            assertTrue(Arrays.equals(content, data.getContent()));
            InputStream is = data.getContentStream();
            try {
                assertNotNull(is);
                byte[] buffer = new byte[10];
                int bytesRead = is.read(buffer);
                assertEquals(8, bytesRead);
            }
            finally {
                try {
                    is.close();
                }
                catch (IOException e) {
                    fail();
                }
            }
        }
    }

    @Test
    public void testReadExistingBlob() throws CouchbaseLiteException {
        byte[] content = TEST_BLOB.getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("data", data);
        doc.setValue("name", "Jim");
        doc = save(doc).toMutable();

        Object obj = doc.getValue("data");
        assertTrue(obj instanceof Blob);
        data = (Blob) obj;
        assertTrue(Arrays.equals(content, data.getContent()));

        reopenDB();

        doc = db.getDocument("doc1").toMutable();
        doc.setValue("foo", "bar");
        doc = save(doc).toMutable();

        assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        assertTrue(Arrays.equals(content, data.getContent()));
    }

    @Test
    public void testEnumeratingKeys() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        for (long i = 0; i < 20; i++) { doc.setLong(String.format(Locale.ENGLISH, "key%d", i), i); }
        Map<String, Object> content = doc.toMap();
        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (String key : doc) {
            result.put(key, doc.getValue(key));
            count++;
        }
        assertEquals(content, result);
        assertEquals(content.size(), count);

        doc.remove("key2");
        doc.setLong("key20", 20L);
        doc.setLong("key21", 21L);
        final Map<String, Object> content2 = doc.toMap();
        save(doc, doc1 -> {
            Map<String, Object> content1 = doc1.toMap();
            Map<String, Object> result1 = new HashMap<>();
            int count1 = 0;
            for (String key : doc1) {
                result1.put(key, doc1.getValue(key));
                count1++;
            }
            assertEquals(content1.size(), count1);
            assertEquals(content1, result1);
            assertEquals(content1, content2);
        });
    }

    @Test
    public void testToMutable() throws CouchbaseLiteException {
        byte[] content = TEST_BLOB.getBytes();
        Blob data = new Blob("text/plain", content);
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setBlob("data", data);
        mDoc1.setString("name", "Jim");
        mDoc1.setInt("score", 10);

        MutableDocument mDoc2 = mDoc1.toMutable();

        // https://forums.couchbase.com/t/bug-in-document-tomutable-in-db21/15441
        assertEquals(3, mDoc2.getKeys().size());
        assertEquals(3, mDoc2.count());

        assertNotSame(mDoc1, mDoc2);
        assertEquals(mDoc2, mDoc1);
        assertEquals(mDoc1, mDoc2);
        assertEquals(mDoc1.getBlob("data"), mDoc2.getBlob("data"));
        assertEquals(mDoc1.getString("name"), mDoc2.getString("name"));
        assertEquals(mDoc1.getInt("score"), mDoc2.getInt("score"));

        Document doc1 = save(mDoc1);
        MutableDocument mDoc3 = doc1.toMutable();

        // https://forums.couchbase.com/t/bug-in-document-tomutable-in-db21/15441
        assertEquals(3, mDoc3.getKeys().size());
        assertEquals(3, mDoc3.count());

        assertEquals(doc1.getBlob("data"), mDoc3.getBlob("data"));
        assertEquals(doc1.getString("name"), mDoc3.getString("name"));
        assertEquals(doc1.getInt("score"), mDoc3.getInt("score"));
    }

    @Test
    public void testEquality() throws CouchbaseLiteException {
        byte[] data1 = "data1".getBytes();
        byte[] data2 = "data2".getBytes();

        MutableDocument doc1a = new MutableDocument("doc1");
        MutableDocument doc1b = new MutableDocument("doc1");
        MutableDocument doc1c = new MutableDocument("doc1");

        doc1a.setInt("answer", 42);
        doc1a.setValue("options", "1,2,3");
        doc1a.setBlob("attachment", new Blob("text/plain", data1));

        doc1b.setInt("answer", 42);
        doc1b.setValue("options", "1,2,3");
        doc1b.setBlob("attachment", new Blob("text/plain", data1));

        doc1c.setInt("answer", 41);
        doc1c.setValue("options", "1,2");
        doc1c.setBlob("attachment", new Blob("text/plain", data2));
        doc1c.setString("comment", "This is a comment");

        assertTrue(doc1a.equals(doc1a));
        assertTrue(doc1a.equals(doc1b));
        assertFalse(doc1a.equals(doc1c));

        assertTrue(doc1b.equals(doc1a));
        assertTrue(doc1b.equals(doc1b));
        assertFalse(doc1b.equals(doc1c));

        assertFalse(doc1c.equals(doc1a));
        assertFalse(doc1c.equals(doc1b));
        assertTrue(doc1c.equals(doc1c));

        Document savedDoc = save(doc1c);
        MutableDocument mDoc = savedDoc.toMutable();
        assertTrue(savedDoc.equals(mDoc));
        assertTrue(mDoc.equals(savedDoc));
        mDoc.setInt("answer", 50);
        assertFalse(savedDoc.equals(mDoc));
        assertFalse(mDoc.equals(savedDoc));
    }

    @Test
    public void testEqualityDifferentDocID() throws CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        MutableDocument doc2 = new MutableDocument("doc2");
        doc1.setLong("answer", 42L); // TODO: Integer cause inequality with saved doc
        doc2.setLong("answer", 42L); // TODO: Integer cause inequality with saved doc
        Document sDoc1 = save(doc1);
        Document sDoc2 = save(doc2);

        assertTrue(doc1.equals(doc1));
        assertTrue(sDoc1.equals(sDoc1));
        assertTrue(doc1.equals(sDoc1));
        assertTrue(sDoc1.equals(doc1));

        assertTrue(doc2.equals(doc2));
        assertTrue(sDoc2.equals(sDoc2));
        assertTrue(doc2.equals(sDoc2));
        assertTrue(sDoc2.equals(doc2));

        assertFalse(doc1.equals(doc2));
        assertFalse(doc2.equals(doc1));
        assertFalse(sDoc1.equals(sDoc2));
        assertFalse(sDoc2.equals(sDoc1));
    }

    @Test
    public void testEqualityDifferentDB() throws CouchbaseLiteException {
        Database otherDB = openDB("other");
        try {
            MutableDocument doc1a = new MutableDocument("doc1");
            MutableDocument doc1b = new MutableDocument("doc1");
            doc1a.setLong("answer", 42L);
            doc1b.setLong("answer", 42L);
            assertTrue(doc1a.equals(doc1b));
            assertTrue(doc1b.equals(doc1a));
            Document sDoc1a = save(doc1a);
            otherDB.save(doc1b);
            Document sDoc1b = otherDB.getDocument(doc1b.getId());
            assertTrue(doc1a.equals(sDoc1a));
            assertTrue(sDoc1a.equals(doc1a));
            assertTrue(doc1b.equals(sDoc1b));
            assertTrue(sDoc1b.equals(doc1b));
            assertFalse(sDoc1a.equals(sDoc1b));
            assertFalse(sDoc1b.equals(sDoc1a));

            sDoc1a = db.getDocument("doc1");
            sDoc1b = otherDB.getDocument("doc1");
            assertFalse(sDoc1b.equals(sDoc1a));

            Database sameDB = openDB(db.getName());
            try {
                Document anotherDoc1a = sameDB.getDocument("doc1");
                assertTrue(anotherDoc1a.equals(sDoc1a));
                assertTrue(sDoc1a.equals(anotherDoc1a));
            }
            finally {
                sameDB.close();
            }
        }
        finally {
            otherDB.close();
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1449
    @Test
    public void testDeleteDocAndGetDoc() throws CouchbaseLiteException {
        String docID = "doc-1";

        Document doc = db.getDocument(docID);
        assertNull(doc);

        MutableDocument mDoc = new MutableDocument(docID);
        mDoc.setValue("key", "value");
        doc = save(mDoc);
        assertNotNull(doc);
        assertEquals(1, db.getCount());

        doc = db.getDocument(docID);
        assertNotNull(doc);
        assertEquals("value", doc.getString("key"));

        db.delete(doc);
        assertEquals(0, db.getCount());
        doc = db.getDocument(docID);
        assertNull(doc);
    }

    @Test
    public void testEquals() throws CouchbaseLiteException {

        // mDoc1 and mDoc2 have exactly same data
        // mDoc3 is different
        // mDoc4 is different

        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setValue("key1", 1L);
        mDoc1.setValue("key2", "Hello");
        mDoc1.setValue("key3", null);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setValue("key1", 1L);
        mDoc2.setValue("key2", "Hello");
        mDoc2.setValue("key3", null);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setValue("key1", 100L);
        mDoc3.setValue("key3", true);

        MutableDocument mDoc4 = new MutableDocument();
        mDoc4.setValue("key1", 100L);

        MutableDocument mDoc5 = new MutableDocument();
        mDoc4.setValue("key1", 100L);
        mDoc3.setValue("key3", false);

        MutableDocument mDoc6 = new MutableDocument();
        mDoc6.setValue("key1", 100L);

        MutableDocument mDoc7 = new MutableDocument();
        mDoc7.setValue("key1", 100L);
        mDoc7.setValue("key3", false);

        MutableDocument mDoc8 = new MutableDocument("sameDocID");
        mDoc8.setValue("key1", 100L);

        MutableDocument mDoc9 = new MutableDocument("sameDocID");
        mDoc9.setValue("key1", 100L);
        mDoc9.setValue("key3", false);

        Document doc1 = save(mDoc1);
        Document doc2 = save(mDoc2);
        Document doc3 = save(mDoc3);
        Document doc4 = save(mDoc4);
        Document doc5 = save(mDoc5);

        // compare doc1, doc2, mdoc1, and mdoc2
        assertTrue(doc1.equals(doc1));
        assertTrue(doc2.equals(doc2));
        assertFalse(doc1.equals(doc2));
        assertFalse(doc2.equals(doc1));
        assertTrue(doc1.equals(doc1.toMutable()));
        assertFalse(doc1.equals(doc2.toMutable()));
        assertTrue(doc1.toMutable().equals(doc1));
        assertFalse(doc2.toMutable().equals(doc1));
        assertTrue(doc1.equals(mDoc1)); // mDoc's ID is updated
        assertFalse(doc1.equals(mDoc2));
        assertFalse(doc2.equals(mDoc1));
        assertTrue(doc2.equals(mDoc2));
        assertTrue(mDoc1.equals(doc1));
        assertFalse(mDoc2.equals(doc1));
        assertFalse(mDoc1.equals(doc2));
        assertTrue(mDoc2.equals(doc2));
        assertTrue(mDoc1.equals(mDoc1));
        assertTrue(mDoc2.equals(mDoc2));
        assertTrue(mDoc1.equals(mDoc1));
        assertTrue(mDoc2.equals(mDoc2));

        // compare doc1, doc3, mdoc1, and mdoc3
        assertTrue(doc3.equals(doc3));
        assertFalse(doc1.equals(doc3));
        assertFalse(doc3.equals(doc1));
        assertFalse(doc1.equals(doc3.toMutable()));
        assertFalse(doc3.toMutable().equals(doc1));
        assertFalse(doc1.equals(mDoc3));
        assertFalse(doc3.equals(mDoc1));
        assertTrue(doc3.equals(mDoc3));
        assertFalse(mDoc3.equals(doc1));
        assertFalse(mDoc1.equals(doc3));
        assertTrue(mDoc3.equals(doc3));
        assertTrue(mDoc3.equals(mDoc3));

        // compare doc1, doc4, mdoc1, and mdoc4
        assertTrue(doc4.equals(doc4));
        assertFalse(doc1.equals(doc4));
        assertFalse(doc4.equals(doc1));
        assertFalse(doc1.equals(doc4.toMutable()));
        assertFalse(doc4.toMutable().equals(doc1));
        assertFalse(doc1.equals(mDoc4));
        assertFalse(doc4.equals(mDoc1));
        assertTrue(doc4.equals(mDoc4));
        assertFalse(mDoc4.equals(doc1));
        assertFalse(mDoc1.equals(doc4));
        assertTrue(mDoc4.equals(doc4));
        assertTrue(mDoc4.equals(mDoc4));

        // compare doc3, doc4, mdoc3, and mdoc4
        assertFalse(doc3.equals(doc4));
        assertFalse(doc4.equals(doc3));
        assertFalse(doc3.equals(doc4.toMutable()));
        assertFalse(doc4.toMutable().equals(doc3));
        assertFalse(doc3.equals(mDoc4));
        assertFalse(doc4.equals(mDoc3));
        assertFalse(mDoc4.equals(doc3));
        assertFalse(mDoc3.equals(doc4));

        // compare doc3, doc5, mdoc3, and mdoc5
        assertFalse(doc3.equals(doc5));
        assertFalse(doc5.equals(doc3));
        assertFalse(doc3.equals(doc5.toMutable()));
        assertFalse(doc5.toMutable().equals(doc3));
        assertFalse(doc3.equals(mDoc5));
        assertFalse(doc5.equals(mDoc3));
        assertFalse(mDoc5.equals(doc3));
        assertFalse(mDoc3.equals(doc5));

        // compare doc5, doc4, mDoc5, and mdoc4
        assertFalse(doc5.equals(doc4));
        assertFalse(doc4.equals(doc5));
        assertFalse(doc5.equals(doc4.toMutable()));
        assertFalse(doc4.toMutable().equals(doc5));
        assertFalse(doc5.equals(mDoc4));
        assertFalse(doc4.equals(mDoc5));
        assertFalse(mDoc4.equals(doc5));
        assertFalse(mDoc5.equals(doc4));

        // compare doc1, mDoc1, and mdoc6
        assertFalse(doc1.equals(mDoc6));
        assertFalse(mDoc6.equals(doc1));
        assertFalse(mDoc6.equals(doc1.toMutable()));
        assertFalse(mDoc1.equals(mDoc6));
        assertFalse(mDoc6.equals(mDoc1));

        // compare doc4, mDoc4, and mdoc6
        assertTrue(mDoc6.equals(mDoc6));
        assertFalse(doc4.equals(mDoc6));
        assertFalse(mDoc6.equals(doc4));
        assertFalse(mDoc6.equals(doc4.toMutable()));
        assertFalse(mDoc4.equals(mDoc6));
        assertFalse(mDoc6.equals(mDoc4));

        // compare doc5, mDoc5, and mdoc7
        assertTrue(mDoc7.equals(mDoc7));
        assertFalse(doc5.equals(mDoc7));
        assertFalse(mDoc7.equals(doc5));
        assertFalse(mDoc7.equals(doc5.toMutable()));
        assertFalse(mDoc5.equals(mDoc7));
        assertFalse(mDoc7.equals(mDoc5));

        // compare mDoc6 and mDoc7
        assertTrue(mDoc6.equals(mDoc6));
        assertFalse(mDoc6.equals(mDoc7));
        assertFalse(mDoc6.equals(mDoc8));
        assertFalse(mDoc6.equals(mDoc9));
        assertFalse(mDoc7.equals(mDoc6));
        assertTrue(mDoc7.equals(mDoc7));
        assertFalse(mDoc7.equals(mDoc8));
        assertFalse(mDoc7.equals(mDoc9));

        // compare mDoc8 and mDoc9
        assertTrue(mDoc8.equals(mDoc8));
        assertFalse(mDoc8.equals(mDoc9));
        assertFalse(mDoc9.equals(mDoc8));
        assertTrue(mDoc9.equals(mDoc9));

        // against other type
        assertFalse(doc3.equals(null));
        assertFalse(doc3.equals(new Object()));
        assertFalse(doc3.equals(1));
        assertFalse(doc3.equals(new HashMap<>()));
        assertFalse(doc3.equals(new MutableDocument()));
        assertFalse(doc3.equals(new MutableArray()));
    }

    // TODO: this test causes native crash in case of running on Android API 19
    @Test
    public void testHashCode() throws CouchbaseLiteException {

        // mDoc1 and mDoc2 have exactly same data
        // mDoc3 is different
        // mDoc4 is different

        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setValue("key1", 1L);
        mDoc1.setValue("key2", "Hello");
        mDoc1.setValue("key3", null);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setValue("key1", 1L);
        mDoc2.setValue("key2", "Hello");
        mDoc2.setValue("key3", null);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setValue("key1", 100L);
        mDoc3.setValue("key3", true);

        MutableDocument mDoc4 = new MutableDocument();
        mDoc4.setValue("key1", 100L);

        MutableDocument mDoc5 = new MutableDocument();
        mDoc4.setValue("key1", 100L);
        mDoc3.setValue("key3", false);

        MutableDocument mDoc6 = new MutableDocument();
        mDoc6.setValue("key1", 100L);

        MutableDocument mDoc7 = new MutableDocument();
        mDoc7.setValue("key1", 100L);
        mDoc7.setValue("key3", false);

        Document doc1 = save(mDoc1);
        Document doc2 = save(mDoc2);
        Document doc3 = save(mDoc3);
        Document doc4 = save(mDoc4);
        Document doc5 = save(mDoc5);

        assertEquals(doc1.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(doc1.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(doc2.hashCode(), doc1.hashCode());
        assertEquals(doc1.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(doc1.hashCode(), doc2.toMutable().hashCode());
        assertEquals(doc1.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(doc1.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(doc2.hashCode(), mDoc1.hashCode());
        assertEquals(doc2.hashCode(), mDoc2.hashCode());

        Assert.assertNotEquals(doc3.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc2.hashCode());

        Assert.assertNotEquals(doc3.hashCode(), 0);
        Assert.assertNotEquals(doc3.hashCode(), new Object().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new Integer(1).hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new HashMap<>().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new MutableDictionary().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new MutableArray().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc2.hashCode());

        Assert.assertNotEquals(mDoc6.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc3.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc3.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc3.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc4.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc4.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc4.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc5.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc5.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc5.hashCode());
        assertEquals(mDoc6.hashCode(), mDoc6.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc7.hashCode());

        Assert.assertNotEquals(mDoc7.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc3.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc3.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc3.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc4.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc4.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc4.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc5.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc5.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc5.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc6.hashCode());
        assertEquals(mDoc7.hashCode(), mDoc7.hashCode());

        Assert.assertNotEquals(doc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc2.hashCode());
    }

    @Test
    public void testRevisionIDNewDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        assertNull(doc.getRevisionID());
        db.save(doc);
        assertNotNull(doc.getRevisionID());
    }

    @Test
    public void testRevisionIDExistingDoc() throws CouchbaseLiteException {
        MutableDocument mdoc = new MutableDocument("doc1");
        db.save(mdoc);

        Document doc = db.getDocument("doc1");
        String docRevID = mdoc.getRevisionID();
        assertEquals(mdoc.getRevisionID(), docRevID);

        mdoc = doc.toMutable();
        assertEquals(docRevID, mdoc.getRevisionID());

        mdoc.setInt("int", 88);
        db.save(mdoc);

        assertEquals(docRevID, doc.getRevisionID());
        assertNotEquals(docRevID, mdoc.getRevisionID());
    }
}
