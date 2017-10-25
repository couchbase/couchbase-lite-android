/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBadDocID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentTest extends BaseTest {

    final static String kDocumentTestDate = "2017-01-01T00:00:00.000Z";
    final static String kDocumentTestBlob = "i'm blob";

    private void populateData(Document doc) {
        doc.setObject("true", true);
        doc.setObject("false", false);
        doc.setObject("string", "string");
        doc.setObject("zero", 0);
        doc.setObject("one", 1);
        doc.setObject("minus_one", -1);
        doc.setObject("one_dot_one", 1.1);
        doc.setObject("date", DateUtils.fromJson(kDocumentTestDate));
        doc.setObject("null", null);

        // Dictionary:
        Dictionary dict = new Dictionary();
        dict.setObject("street", "1 Main street");
        dict.setObject("city", "Mountain View");
        dict.setObject("state", "CA");
        doc.setObject("dict", dict);

        // Array:
        Array array = new Array();
        array.addObject("650-123-0001");
        array.addObject("650-123-0002");
        doc.setObject("array", array);

        // Blob:
        byte[] content = kDocumentTestBlob.getBytes();
        Blob blob = new Blob("text/plain", content);
        doc.setObject("blob", blob);
    }

    private void populateDataByTypedSetter(Document doc) {
        doc.setBoolean("true", true);
        doc.setBoolean("false", false);
        doc.setString("string", "string");
        doc.setNumber("zero", 0);
        doc.setInt("one", 1);
        doc.setLong("minus_one", -1);
        doc.setDouble("one_dot_one", 1.1);
        doc.setDate("date", DateUtils.fromJson(kDocumentTestDate));
        doc.setString("null", null);

        // Dictionary:
        Dictionary dict = new Dictionary();
        dict.setString("street", "1 Main street");
        dict.setString("city", "Mountain View");
        dict.setString("state", "CA");
        doc.setDictionary("dict", dict);

        // Array:
        Array array = new Array();
        array.addString("650-123-0001");
        array.addString("650-123-0002");
        doc.setArray("array", array);

        // Blob:
        byte[] content = kDocumentTestBlob.getBytes();
        Blob blob = new Blob("text/plain", content);
        doc.setBlob("blob", blob);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCreateDoc() throws CouchbaseLiteException {
        Document doc1a = new Document();
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertFalse(doc1a.isDeleted());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testNewDocWithId() throws CouchbaseLiteException {
        Document doc1a = createDocument("doc1");
        assertNotNull(doc1a);
        assertEquals("doc1", doc1a.getId());
        assertFalse(doc1a.isDeleted());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument("doc1");
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithEmptyStringID() {
        Document doc1a = createDocument("");
        assertNotNull(doc1a);
        try {
            save(doc1a);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(LiteCoreDomain, e.getDomain());
            assertEquals(kC4ErrorBadDocID, e.getCode());
        }
    }

    @Test
    public void testCreateDocWithNilID() throws CouchbaseLiteException {
        Document doc1a = createDocument(null);
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertFalse(doc1a.isDeleted());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
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

        final Document doc1a = new Document(dict);
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertFalse(doc1a.isDeleted());
        assertEquals(dict, doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
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

        Document doc1a = createDocument("doc1", dict);
        assertNotNull(doc1a);
        assertEquals("doc1", doc1a.getId());
        assertFalse(doc1a.isDeleted());
        assertEquals(dict, doc1a.toMap());

        save(doc1a);
        Document doc1b = db.getDocument("doc1");
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
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

        Document doc = createDocument("doc1");
        doc.set(dict);
        assertEquals(dict, doc.toMap());

        save(doc);
        doc = db.getDocument("doc1");
        assertEquals(dict, doc.toMap());

        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("name", "Danial Tiger");
        nuDict.put("age", 32L);

        Map<String, Object> nuAddress = new HashMap<>();
        nuAddress.put("street", "2 Main street");
        nuAddress.put("city", "Palo Alto");
        nuAddress.put("state", "CA");
        nuDict.put("address", nuAddress);

        nuDict.put("phones", Arrays.asList("650-234-0001", "650-234-0002"));

        doc.set(nuDict);
        assertEquals(nuDict, doc.toMap());

        save(doc);
        doc = db.getDocument("doc1");
        assertEquals(nuDict, doc.toMap());
    }


    @Test
    public void testGetValueFromNewEmptyDoc() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        save(doc, new Validator<Document>() {
            @Override
            public void validate(final Document d) {
                assertEquals(0, d.getInt("key"));
                assertEquals(0.0f, d.getFloat("key"), 0.0f);
                assertEquals(0.0, d.getDouble("key"), 0.0);
                assertEquals(false, d.getBoolean("key"));
                assertNull(d.getBlob("key"));
                assertNull(d.getDate("key"));
                assertNull(d.getNumber("key"));
                assertNull(d.getObject("key"));
                assertNull(d.getString("key"));
                assertNull(d.getArray("key"));
                assertNull(d.getDictionary("key"));
                assertEquals(new HashMap<>(), d.toMap());
            }
        });
    }

    @Test
    public void testGetValueFromExistingEmptyDoc() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        save(doc);
        doc = db.getDocument("doc1");

        assertEquals(0, doc.getInt("key"));
        assertEquals(0.0f, doc.getFloat("key"), 0.0f);
        assertEquals(0.0, doc.getDouble("key"), 0.0);
        assertEquals(false, doc.getBoolean("key"));
        assertNull(doc.getBlob("key"));
        assertNull(doc.getDate("key"));
        assertNull(doc.getNumber("key"));
        assertNull(doc.getObject("key"));
        assertNull(doc.getString("key"));
        assertNull(doc.getArray("key"));
        assertNull(doc.getDictionary("key"));
        assertEquals(new HashMap<>(), doc.toMap());
    }


    @Test
    public void testSaveThenGetFromAnotherDB() throws CouchbaseLiteException {
        Document doc1a = createDocument("doc1");
        doc1a.setObject("name", "Scott Tiger");
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
        Document doc1a = createDocument("doc1");
        doc1a.setObject("name", "Scott Tiger");

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

        doc1b.setObject("name", "Daniel Tiger");
        save(doc1b);

        assertNotEquals(doc1b.toMap(), doc1a.toMap());
        assertNotEquals(doc1b.toMap(), doc1c.toMap());
        assertNotEquals(doc1b.toMap(), doc1d.toMap());

        anotherDb.close();
    }

    @Test
    public void testSetString() throws CouchbaseLiteException {
        Validator<Document> validator4Save = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals("", d.getObject("string1"));
                assertEquals("string", d.getObject("string2"));
            }
        };

        Validator<Document> validator4SUpdate = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals("string", d.getObject("string1"));
                assertEquals("", d.getObject("string2"));
            }
        };

        // -- setObject
        // save
        Document doc = createDocument("doc1");
        doc.setObject("string1", "");
        doc.setObject("string2", "string");
        save(doc, validator4Save);
        // update
        doc.setObject("string1", "string");
        doc.setObject("string2", "");
        save(doc, validator4SUpdate);

        // -- setString
        // save
        Document doc2 = createDocument("doc2");
        doc2.setString("string1", "");
        doc2.setString("string2", "string");
        save(doc2, validator4Save);

        // update
        doc2.setString("string1", "string");
        doc2.setString("string2", "");
        save(doc2, validator4SUpdate);
    }

    @Test
    public void testGetString() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
                    assertNull(d.getString("null"));
                    assertNull(d.getString("true"));
                    assertNull(d.getString("false"));
                    assertEquals("string", d.getString("string"));
                    assertNull(d.getString("zero"));
                    assertNull(d.getString("one"));
                    assertNull(d.getString("minus_one"));
                    assertNull(d.getString("one_dot_one"));
                    assertEquals(kDocumentTestDate, d.getString("date"));
                    assertNull(d.getString("dict"));
                    assertNull(d.getString("array"));
                    assertNull(d.getString("blob"));
                    assertNull(d.getString("non_existing_key"));
                }
            });
        }
    }

    @Test
    public void testSetNumber() throws CouchbaseLiteException {
        Validator<Document> validator4Save = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(1, ((Number) d.getObject("number1")).intValue());
                assertEquals(0, ((Number) d.getObject("number2")).intValue());
                assertEquals(-1, ((Number) d.getObject("number3")).intValue());
                assertEquals(-10, ((Number) d.getObject("number4")).intValue());
            }
        };

        Validator<Document> validator4SUpdate = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(0, ((Number) d.getObject("number1")).intValue());
                assertEquals(1, ((Number) d.getObject("number2")).intValue());
                assertEquals(-10, ((Number) d.getObject("number3")).intValue());
                assertEquals(-1, ((Number) d.getObject("number4")).intValue());
            }
        };

        // -- setObject
        Document doc = createDocument("doc1");
        doc.setObject("number1", 1);
        doc.setObject("number2", 0);
        doc.setObject("number3", -1);
        doc.setObject("number4", -10);
        save(doc, validator4Save);

        // Update:
        doc.setObject("number1", 0);
        doc.setObject("number2", 1);
        doc.setObject("number3", -10);
        doc.setObject("number4", -1);
        save(doc, validator4SUpdate);

        // -- setNumber
        // save
        Document doc2 = createDocument("doc2");
        doc2.setNumber("number1", 1);
        doc2.setNumber("number2", 0);
        doc2.setNumber("number3", -1);
        doc2.setNumber("number4", -10);
        save(doc2, validator4Save);

        // Update:
        doc2.setNumber("number1", 0);
        doc2.setNumber("number2", 1);
        doc2.setNumber("number3", -10);
        doc2.setNumber("number4", -1);
        save(doc2, validator4SUpdate);

        // -- setInt
        // save
        Document doc3 = createDocument("doc3");
        doc3.setInt("number1", 1);
        doc3.setInt("number2", 0);
        doc3.setInt("number3", -1);
        doc3.setInt("number4", -10);
        save(doc3, validator4Save);

        // Update:
        doc3.setInt("number1", 0);
        doc3.setInt("number2", 1);
        doc3.setInt("number3", -10);
        doc3.setInt("number4", -1);
        save(doc3, validator4SUpdate);

        // -- setLong
        // save
        Document doc4 = createDocument("doc4");
        doc4.setLong("number1", 1);
        doc4.setLong("number2", 0);
        doc4.setLong("number3", -1);
        doc4.setLong("number4", -10);
        save(doc4, validator4Save);

        // Update:
        doc4.setLong("number1", 0);
        doc4.setLong("number2", 1);
        doc4.setLong("number3", -10);
        doc4.setLong("number4", -1);
        save(doc4, validator4SUpdate);

        // -- setFloat
        // save
        Document doc5 = createDocument("doc5");
        doc5.setFloat("number1", 1);
        doc5.setFloat("number2", 0);
        doc5.setFloat("number3", -1);
        doc5.setFloat("number4", -10);
        save(doc5, validator4Save);

        // Update:
        doc5.setFloat("number1", 0);
        doc5.setFloat("number2", 1);
        doc5.setFloat("number3", -10);
        doc5.setFloat("number4", -1);
        save(doc5, validator4SUpdate);

        // -- setDouble
        // save
        Document doc6 = createDocument("doc6");
        doc6.setDouble("number1", 1);
        doc6.setDouble("number2", 0);
        doc6.setDouble("number3", -1);
        doc6.setDouble("number4", -10);
        save(doc6, validator4Save);

        // Update:
        doc6.setDouble("number1", 0);
        doc6.setDouble("number2", 1);
        doc6.setDouble("number3", -10);
        doc6.setDouble("number4", -1);
        save(doc6, validator4SUpdate);
    }

    @Test
    public void testGetNumber() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                }
            });
        }
    }

    @Test
    public void testGetInteger() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                }
            });
        }
    }

    @Test
    public void testGetLong() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                }
            });
        }
    }

    @Test
    public void testGetFloat() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                }
            });
        }
    }

    @Test
    public void testGetDouble() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                }
            });
        }
    }

    @Test
    public void testSetGetMinMaxNumbers() throws CouchbaseLiteException {
        Validator<Document> validator = new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(Integer.MIN_VALUE, doc.getNumber("min_int").intValue());
                assertEquals(Integer.MAX_VALUE, doc.getNumber("max_int").intValue());
                assertEquals(Integer.MIN_VALUE, ((Number) doc.getObject("min_int")).intValue());
                assertEquals(Integer.MAX_VALUE, ((Number) doc.getObject("max_int")).intValue());
                assertEquals(Integer.MIN_VALUE, doc.getInt("min_int"));
                assertEquals(Integer.MAX_VALUE, doc.getInt("max_int"));

                assertEquals(Long.MIN_VALUE, doc.getNumber("min_long"));
                assertEquals(Long.MAX_VALUE, doc.getNumber("max_long"));
                assertEquals(Long.MIN_VALUE, doc.getObject("min_long"));
                assertEquals(Long.MAX_VALUE, doc.getObject("max_long"));
                assertEquals(Long.MIN_VALUE, doc.getLong("min_long"));
                assertEquals(Long.MAX_VALUE, doc.getLong("max_long"));

                assertEquals(Float.MIN_VALUE, doc.getNumber("min_float"));
                assertEquals(Float.MAX_VALUE, doc.getNumber("max_float"));
                assertEquals(Float.MIN_VALUE, doc.getObject("min_float"));
                assertEquals(Float.MAX_VALUE, doc.getObject("max_float"));
                assertEquals(Float.MIN_VALUE, doc.getFloat("min_float"), 0.0f);
                assertEquals(Float.MAX_VALUE, doc.getFloat("max_float"), 0.0f);

                assertEquals(Double.MIN_VALUE, doc.getNumber("min_double"));
                assertEquals(Double.MAX_VALUE, doc.getNumber("max_double"));
                assertEquals(Double.MIN_VALUE, doc.getObject("min_double"));
                assertEquals(Double.MAX_VALUE, doc.getObject("max_double"));
                assertEquals(Double.MIN_VALUE, doc.getDouble("min_double"), 0.0);
                assertEquals(Double.MAX_VALUE, doc.getDouble("max_double"), 0.0);
            }
        };

        // -- setObject
        Document doc = createDocument("doc1");
        doc.setObject("min_int", Integer.MIN_VALUE);
        doc.setObject("max_int", Integer.MAX_VALUE);
        doc.setObject("min_long", Long.MIN_VALUE);
        doc.setObject("max_long", Long.MAX_VALUE);
        doc.setObject("min_float", Float.MIN_VALUE);
        doc.setObject("max_float", Float.MAX_VALUE);
        doc.setObject("min_double", Double.MIN_VALUE);
        doc.setObject("max_double", Double.MAX_VALUE);
        save(doc, validator);

        // -- setInt, setLong, setFloat, setDouble
        Document doc2 = createDocument("doc2");
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
        Validator<Document> validator = new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1.00, ((Number) doc.getObject("number1")).doubleValue(), 0.00001);
                assertEquals(1.00, doc.getNumber("number1").doubleValue(), 0.00001);
                assertEquals(1, doc.getInt("number1"));
                assertEquals(1L, doc.getLong("number1"));
                assertEquals(1.00F, doc.getFloat("number1"), 0.00001F);
                assertEquals(1.00, doc.getDouble("number1"), 0.00001);

                assertEquals(1.49, ((Number) doc.getObject("number2")).doubleValue(), 0.00001);
                assertEquals(1.49, doc.getNumber("number2").doubleValue(), 0.00001);
                assertEquals(1, doc.getInt("number2"));
                assertEquals(1L, doc.getLong("number2"));
                assertEquals(1.49F, doc.getFloat("number2"), 0.00001F);
                assertEquals(1.49, doc.getDouble("number2"), 0.00001);

                assertEquals(1.50, ((Number) doc.getObject("number3")).doubleValue(), 0.00001);
                assertEquals(1.50, doc.getNumber("number3").doubleValue(), 0.00001);
                assertEquals(1, doc.getInt("number3"));
                assertEquals(1L, doc.getLong("number3"));
                assertEquals(1.50F, doc.getFloat("number3"), 0.00001F);
                assertEquals(1.50, doc.getDouble("number3"), 0.00001);

                assertEquals(1.51, ((Number) doc.getObject("number4")).doubleValue(), 0.00001);
                assertEquals(1.51, doc.getNumber("number4").doubleValue(), 0.00001);
                assertEquals(1, doc.getInt("number4"));
                assertEquals(1L, doc.getLong("number4"));
                assertEquals(1.51F, doc.getFloat("number4"), 0.00001F);
                assertEquals(1.51, doc.getDouble("number4"), 0.00001);

                assertEquals(1.99, ((Number) doc.getObject("number5")).doubleValue(), 0.00001);// return 1
                assertEquals(1.99, doc.getNumber("number5").doubleValue(), 0.00001);  // return 1
                assertEquals(1, doc.getInt("number5"));
                assertEquals(1L, doc.getLong("number5"));
                assertEquals(1.99F, doc.getFloat("number5"), 0.00001F);
                assertEquals(1.99, doc.getDouble("number5"), 0.00001);
            }
        };


        // -- setObject
        Document doc = createDocument("doc1");

        doc.setObject("number1", 1.00);
        doc.setObject("number2", 1.49);
        doc.setObject("number3", 1.50);
        doc.setObject("number4", 1.51);
        doc.setObject("number5", 1.99);
        save(doc, validator);

        // -- setFloat
        Document doc2 = createDocument("doc2");
        doc2.setFloat("number1", 1.00f);
        doc2.setFloat("number2", 1.49f);
        doc2.setFloat("number3", 1.50f);
        doc2.setFloat("number4", 1.51f);
        doc2.setFloat("number5", 1.99f);
        save(doc2, validator);

        // -- setDouble
        Document doc3 = createDocument("doc3");
        doc3.setDouble("number1", 1.00);
        doc3.setDouble("number2", 1.49);
        doc3.setDouble("number3", 1.50);
        doc3.setDouble("number4", 1.51);
        doc3.setDouble("number5", 1.99);
        save(doc3, validator);
    }

    @Test
    public void testSetBoolean() throws CouchbaseLiteException {
        Validator<Document> validator4Save = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(true, d.getObject("boolean1"));
                assertEquals(false, d.getObject("boolean2"));
                assertEquals(true, d.getBoolean("boolean1"));
                assertEquals(false, d.getBoolean("boolean2"));
            }
        };
        Validator<Document> validator4Update = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(false, d.getObject("boolean1"));
                assertEquals(true, d.getObject("boolean2"));
                assertEquals(false, d.getBoolean("boolean1"));
                assertEquals(true, d.getBoolean("boolean2"));
            }
        };

        // -- setObject
        Document doc = createDocument("doc1");
        doc.setObject("boolean1", true);
        doc.setObject("boolean2", false);
        save(doc, validator4Save);

        // Update:
        doc.setObject("boolean1", false);
        doc.setObject("boolean2", true);
        save(doc, validator4Update);

        // -- setBoolean
        Document doc2 = createDocument("doc2");
        doc2.setObject("boolean1", true);
        doc2.setObject("boolean2", false);
        save(doc2, validator4Save);

        // Update:
        doc2.setObject("boolean1", false);
        doc2.setObject("boolean2", true);
        save(doc2, validator4Update);
    }

    @Test
    public void testGetBoolean() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                }
            });
        }
    }

    @Test
    public void testSetDate() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");

        Date date = new Date();
        final String dateStr = DateUtils.toJson(date);
        assertTrue(dateStr.length() > 0);
        doc.setObject("date", date);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(dateStr, d.getObject("date"));
                assertEquals(dateStr, d.getString("date"));
                assertEquals(dateStr, DateUtils.toJson(d.getDate("date")));
            }
        });

        // Update:
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, 60);
        Date nuDate = cal.getTime();
        final String nuDateStr = DateUtils.toJson(nuDate);
        doc.setObject("date", nuDate);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(nuDateStr, d.getObject("date"));
                assertEquals(nuDateStr, d.getString("date"));
                assertEquals(nuDateStr, DateUtils.toJson(d.getDate("date")));
            }
        });
    }

    @Test
    public void testGetDate() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
                    assertNull(d.getDate("null"));
                    assertNull(d.getDate("true"));
                    assertNull(d.getDate("false"));
                    assertNull(d.getDate("string"));
                    assertNull(d.getDate("zero"));
                    assertNull(d.getDate("one"));
                    assertNull(d.getDate("minus_one"));
                    assertNull(d.getDate("one_dot_one"));
                    assertEquals(kDocumentTestDate, DateUtils.toJson(d.getDate("date")));
                    assertNull(d.getDate("dict"));
                    assertNull(d.getDate("array"));
                    assertNull(d.getDate("blob"));
                    assertNull(d.getDate("non_existing_key"));
                }
            });
        }
    }

    @Test
    public void testSetBlob() throws CouchbaseLiteException {
        final Blob blob = new Blob("text/plain", kDocumentTestBlob.getBytes());
        final Blob nuBlob = new Blob("text/plain", "1234567890".getBytes());

        Validator<Document> validator4Save = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(blob.getProperties().get("length"), d.getBlob("blob").getProperties().get("length"));
                assertEquals(blob.getProperties().get("content-type"), d.getBlob("blob").getProperties().get("content-type"));
                assertEquals(blob.getProperties().get("digest"), d.getBlob("blob").getProperties().get("digest"));
                assertEquals(blob.getProperties().get("length"), ((Blob) d.getObject("blob")).getProperties().get("length"));
                assertEquals(blob.getProperties().get("content-type"), ((Blob) d.getObject("blob")).getProperties().get("content-type"));
                assertEquals(blob.getProperties().get("digest"), ((Blob) d.getObject("blob")).getProperties().get("digest"));
                assertEquals(kDocumentTestBlob, new String(d.getBlob("blob").getContent()));
                assertTrue(Arrays.equals(kDocumentTestBlob.getBytes(), d.getBlob("blob").getContent()));
            }
        };

        Validator<Document> validator4Update = new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(nuBlob.getProperties().get("length"), d.getBlob("blob").getProperties().get("length"));
                assertEquals(nuBlob.getProperties().get("content-type"), d.getBlob("blob").getProperties().get("content-type"));
                assertEquals(nuBlob.getProperties().get("digest"), d.getBlob("blob").getProperties().get("digest"));
                assertEquals(nuBlob.getProperties().get("length"), ((Blob) d.getObject("blob")).getProperties().get("length"));
                assertEquals(nuBlob.getProperties().get("content-type"), ((Blob) d.getObject("blob")).getProperties().get("content-type"));
                assertEquals(nuBlob.getProperties().get("digest"), ((Blob) d.getObject("blob")).getProperties().get("digest"));
                assertEquals("1234567890", new String(d.getBlob("blob").getContent()));
                assertTrue(Arrays.equals("1234567890".getBytes(), d.getBlob("blob").getContent()));
            }
        };

        // --setObject
        Document doc = createDocument("doc1");
        doc.setObject("blob", blob);
        save(doc, validator4Save);

        // Update:
        doc.setObject("blob", nuBlob);
        save(doc, validator4Update);

        // --setBlob
        Document doc2 = createDocument("doc2");
        doc2.setBlob("blob", blob);
        save(doc2, validator4Save);

        // Update:
        doc2.setBlob("blob", nuBlob);
        save(doc2, validator4Update);
    }

    @Test
    public void testGetBlob() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                    assertEquals(kDocumentTestBlob, new String(d.getBlob("blob").getContent()));
                    assertTrue(Arrays.equals(kDocumentTestBlob.getBytes(),
                            d.getBlob("blob").getContent()));
                    assertNull(d.getBlob("non_existing_key"));
                }
            });
        }
    }

    @Test
    public void testSetDictionary() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            // -- setObject
            Document doc = createDocument(docID);
            Dictionary dict = new Dictionary();
            dict.setObject("street", "1 Main street");
            if (i % 2 == 1)
                doc.setObject("dict", dict);
            else
                doc.setDictionary("dict", dict);
            assertEquals(dict, doc.getObject("dict"));
            assertEquals(dict.toMap(), ((Dictionary) doc.getObject("dict")).toMap());
            save(doc);
            doc = db.getDocument(docID);
            assertTrue(dict != doc.getObject("dict"));
            assertEquals(doc.getObject("dict"), doc.getDictionary("dict"));
            assertEquals(dict.toMap(), ((Dictionary) doc.getObject("dict")).toMap());

            // Update:
            dict = doc.getDictionary("dict");
            dict.setObject("city", "Mountain View");
            assertEquals(doc.getObject("dict"), doc.getDictionary("dict"));
            Map<String, Object> map = new HashMap<>();
            map.put("street", "1 Main street");
            map.put("city", "Mountain View");
            assertEquals(map, doc.getDictionary("dict").toMap());
            save(doc);
            doc = db.getDocument(docID);
            assertTrue(dict != doc.getObject("dict"));
            assertEquals(doc.getObject("dict"), doc.getDictionary("dict"));
            assertEquals(map, doc.getDictionary("dict").toMap());
        }
    }

    @Test
    public void testGetDictionary() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                    assertNull(d.getDictionary("non_existing_key"));
                }
            });
        }
    }

    @Test
    public void testSetArray() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            Array array = new Array();
            array.addObject("item1");
            array.addObject("item2");
            array.addObject("item3");
            if (i % 2 == 1)
                doc.setObject("array", array);
            else
                doc.setArray("array", array);
            assertEquals(array, doc.getObject("array"));
            assertEquals(array.toList(), ((Array) doc.getObject("array")).toList());
            save(doc);
            doc = db.getDocument(docID);
            assertTrue(array != doc.getObject("array"));
            assertEquals(doc.getObject("array"), doc.getArray("array"));
            assertEquals(array.toList(), ((Array) doc.getObject("array")).toList());

            // Update:
            array = doc.getArray("array");
            array.addObject("item4");
            array.addObject("item5");
            save(doc);
            doc = db.getDocument(docID);
            assertTrue(array != doc.getObject("array"));
            assertEquals(doc.getObject("array"), doc.getArray("array"));
            List<String> list = Arrays.asList("item1", "item2", "item3", "item4", "item5");
            assertEquals(list, doc.getArray("array").toList());
        }
    }

    @Test
    public void testGetArray() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);
            save(doc, new Validator<Document>() {
                @Override
                public void validate(final Document d) {
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
                    List<Object> list = Arrays.asList((Object) "650-123-0001", (Object) "650-123-0002");
                    assertEquals(list, d.getArray("array").toList());
                    assertNull(d.getArray("non_existing_key"));
                }
            });
        }
    }

    @Test
    public void testSetNull() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        doc.setObject("obj-null", null);
        doc.setString("string-null", null);
        doc.setNumber("number-null", null);
        doc.setDate("date-null", null);
        doc.setArray("array-null", null);
        doc.setDictionary("dict-null", null);
        assertEquals(6, doc.count());
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertNull(d.getObject("obj-null"));
                assertNull(d.getObject("string-null"));
                assertNull(d.getObject("number-null"));
                assertNull(d.getObject("date-null"));
                assertNull(d.getObject("array-null"));
                assertNull(d.getObject("dict-null"));
                assertEquals(6, d.count());
            }
        });
    }

    @Test
    public void testSetMap() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");

        Document doc = createDocument("doc1");
        doc.setObject("address", dict);

        Dictionary address = doc.getDictionary("address");
        assertNotNull(address);
        assertEquals(address, doc.getObject("address"));
        assertEquals("1 Main street", address.getString("street"));
        assertEquals("Mountain View", address.getString("city"));
        assertEquals("CA", address.getString("state"));
        assertEquals(dict, address.toMap());

        // Update with a new dictionary:
        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("street", "1 Second street");
        nuDict.put("city", "Palo Alto");
        nuDict.put("state", "CA");
        doc.setObject("address", nuDict);

        // Check whether the old address dictionary is still accessible:
        assertTrue(address != doc.getDictionary("address"));
        assertEquals("1 Main street", address.getString("street"));
        assertEquals("Mountain View", address.getString("city"));
        assertEquals("CA", address.getString("state"));
        assertEquals(dict, address.toMap());

        // The old address dictionary should be detached:
        Dictionary nuAddress = doc.getDictionary("address");
        assertTrue(address != nuAddress);

        // Update nuAddress:
        nuAddress.setObject("zip", "94302");
        assertEquals("94302", nuAddress.getString("zip"));
        assertNull(address.getString("zip"));

        // Save:
        save(doc);
        doc = db.getDocument(doc.getId());

        nuDict.put("zip", "94302");
        Map<String, Object> expected = new HashMap<>();
        expected.put("address", nuDict);
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testSetList() throws CouchbaseLiteException {
        List<String> array = Arrays.asList("a", "b", "c");

        Document doc = createDocument("doc1");
        doc.setObject("members", array);

        Array members = doc.getArray("members");
        assertNotNull(members);
        assertEquals(members, doc.getObject("members"));

        assertEquals(3, members.count());
        assertEquals("a", members.getObject(0));
        assertEquals("b", members.getObject(1));
        assertEquals("c", members.getObject(2));
        assertEquals(array, members.toList());

        // Update with a new array:
        List<String> nuArray = Arrays.asList("d", "e", "f");
        doc.setObject("members", nuArray);

        // Check whether the old members array is still accessible:
        assertEquals(3, members.count());
        assertEquals("a", members.getObject(0));
        assertEquals("b", members.getObject(1));
        assertEquals("c", members.getObject(2));
        assertEquals(array, members.toList());

        // The old members array should be detached:
        Array nuMembers = doc.getArray("members");
        assertTrue(members != nuMembers);

        // Update nuMembers:
        nuMembers.addObject("g");
        assertEquals(4, nuMembers.count());
        assertEquals("g", nuMembers.getObject(3));
        assertEquals(3, members.count());

        // Save
        save(doc);
        doc = db.getDocument("doc1");

        Map<String, Object> expected = new HashMap<>();
        expected.put("members", Arrays.asList("d", "e", "f", "g"));
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateNestedDictionary() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        Dictionary addresses = new Dictionary();
        doc.setObject("addresses", addresses);

        Dictionary shipping = new Dictionary();
        shipping.setObject("street", "1 Main street");
        shipping.setObject("city", "Mountain View");
        shipping.setObject("state", "CA");
        addresses.setObject("shipping", shipping);

        doc = save(doc);

        shipping = doc.getDictionary("addresses").getDictionary("shipping");
        shipping.setObject("zip", "94042");

        doc = save(doc);

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
        Document doc = createDocument("doc1");
        Array addresses = new Array();
        doc.setObject("addresses", addresses);

        Dictionary address1 = new Dictionary();
        address1.setObject("street", "1 Main street");
        address1.setObject("city", "Mountain View");
        address1.setObject("state", "CA");
        addresses.addObject(address1);

        Dictionary address2 = new Dictionary();
        address2.setObject("street", "1 Second street");
        address2.setObject("city", "Palo Alto");
        address2.setObject("state", "CA");
        addresses.addObject(address2);

        doc = save(doc);

        address1 = doc.getArray("addresses").getDictionary(0);
        address1.setObject("street", "2 Main street");
        address1.setObject("zip", "94042");

        address2 = doc.getArray("addresses").getDictionary(1);
        address2.setObject("street", "2 Second street");
        address2.setObject("zip", "94302");

        doc = save(doc);

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
        Document doc = createDocument("doc1");
        Array groups = new Array();
        doc.setObject("groups", groups);

        Array group1 = new Array();
        group1.addObject("a");
        group1.addObject("b");
        group1.addObject("c");
        groups.addObject(group1);

        Array group2 = new Array();
        group2.addObject(1);
        group2.addObject(2);
        group2.addObject(3);
        groups.addObject(group2);

        doc = save(doc);

        group1 = doc.getArray("groups").getArray(0);
        group1.setObject(0, "d");
        group1.setObject(1, "e");
        group1.setObject(2, "f");

        group2 = doc.getArray("groups").getArray(1);
        group2.setObject(0, 4);
        group2.setObject(1, 5);
        group2.setObject(2, 6);

        doc = save(doc);

        Map<String, Object> expected = new HashMap<>();
        expected.put("groups", Arrays.asList(Arrays.asList("d", "e", "f"),
                Arrays.asList(4L, 5L, 6L)));
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateArrayInDictionary() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");

        Dictionary group1 = new Dictionary();
        Array member1 = new Array();
        member1.addObject("a");
        member1.addObject("b");
        member1.addObject("c");
        group1.setObject("member", member1);
        doc.setObject("group1", group1);

        Dictionary group2 = new Dictionary();
        Array member2 = new Array();
        member2.addObject(1);
        member2.addObject(2);
        member2.addObject(3);
        group2.setObject("member", member2);
        doc.setObject("group2", group2);

        doc = save(doc);

        member1 = doc.getDictionary("group1").getArray("member");
        member1.setObject(0, "d");
        member1.setObject(1, "e");
        member1.setObject(2, "f");

        member2 = doc.getDictionary("group2").getArray("member");
        member2.setObject(0, 4);
        member2.setObject(1, 5);
        member2.setObject(2, 6);

        doc = save(doc);

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
        Document doc = createDocument("doc1");

        Dictionary address = new Dictionary();
        address.setObject("street", "1 Main street");
        address.setObject("city", "Mountain View");
        address.setObject("state", "CA");
        doc.setObject("shipping", address);
        doc.setObject("billing", address);

        // Update address: both shipping and billing should get the update.
        address.setObject("zip", "94042");
        assertEquals("94042", doc.getDictionary("shipping").getString("zip"));
        assertEquals("94042", doc.getDictionary("billing").getString("zip"));

        doc = save(doc);

        Dictionary shipping = doc.getDictionary("shipping");
        Dictionary billing = doc.getDictionary("billing");

        // After save: both shipping and billing address are now independent to each other
        assertTrue(shipping != address);
        assertTrue(billing != address);
        assertTrue(shipping != billing);

        shipping.setObject("street", "2 Main street");
        billing.setObject("street", "3 Main street");

        // Save update:
        doc = save(doc);
        assertEquals("2 Main street", doc.getDictionary("shipping").getString("street"));
        assertEquals("3 Main street", doc.getDictionary("billing").getString("street"));
    }

    @Test
    public void testSetArrayToMultipleKeys() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");

        Array phones = new Array();
        phones.addObject("650-000-0001");
        phones.addObject("650-000-0002");

        doc.setObject("mobile", phones);
        doc.setObject("home", phones);

        assertEquals(phones, doc.getObject("mobile"));
        assertEquals(phones, doc.getObject("home"));

        // Update phones: both mobile and home should get the update
        phones.addObject("650-000-0003");

        assertEquals(Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003"),
                doc.getArray("mobile").toList());
        assertEquals(Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003"),
                doc.getArray("home").toList());

        doc = save(doc);

        // After save: both mobile and home are not independent to each other
        Array mobile = doc.getArray("mobile");
        Array home = doc.getArray("home");
        assertTrue(mobile != phones);
        assertTrue(home != phones);
        assertTrue(mobile != home);

        // Update mobile and home:
        mobile.addObject("650-000-1234");
        home.addObject("650-000-5678");

        // Save update:
        doc = save(doc);

        assertEquals(Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003", "650-000-1234"),
                doc.getArray("mobile").toList());
        assertEquals(Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003", "650-000-5678"),
                doc.getArray("home").toList());
    }

    @Test
    public void testToDictionary() {
        Document doc1 = createDocument("doc1");
        populateData(doc1);
        // TODO: Should blob be serialized into JSON dictionary?
    }

    @Test
    public void testCount() throws CouchbaseLiteException {
        for (int i = 1; i <= 2; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            Document doc = createDocument(docID);
            if (i % 2 == 1)
                populateData(doc);
            else
                populateDataByTypedSetter(doc);

            assertEquals(12, doc.count());
            assertEquals(12, doc.toMap().size());

            doc = save(doc);

            assertEquals(12, doc.count());
            assertEquals(12, doc.toMap().size());
        }
    }

    @Test
    public void testRemoveKeys() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
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
        doc.set(profile);

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

        assertNull(doc.getObject("name"));
        assertNull(doc.getObject("weight"));
        assertNull(doc.getObject("age"));
        assertNull(doc.getObject("active"));
        assertNull(doc.getDictionary("address").getObject("city"));

        Dictionary address = doc.getDictionary("address");
        Map<String, Object> addr = new HashMap<>();
        addr.put("street", "1 milky way.");
        addr.put("zip", 12345L);
        assertEquals(addr, address.toMap());
        Map<String, Object> expected = new HashMap<>();
        expected.put("type", "profile");
        expected.put("address", addr);
        assertEquals(expected, doc.toMap());

        doc.remove("type");
        doc.remove("address");
        assertNull(doc.getObject("type"));
        assertNull(doc.getObject("address"));
        assertEquals(new HashMap<>(), doc.toMap());
    }

    @Test
    public void testContainsKey() {
        Document doc = createDocument("doc1");
        Map<String, Object> mapAddress = new HashMap<>();
        mapAddress.put("street", "1 milky way.");
        Map<String, Object> profile = new HashMap<>();
        profile.put("type", "profile");
        profile.put("name", "Jason");
        profile.put("age", 30);
        profile.put("address", mapAddress);
        doc.set(profile);

        assertTrue(doc.contains("type"));
        assertTrue(doc.contains("name"));
        assertTrue(doc.contains("age"));
        assertTrue(doc.contains("address"));
        assertFalse(doc.contains("weight"));
    }

    @Test
    public void testDeleteNewDocument() {
        Document doc = createDocument("doc1");
        doc.setObject("name", "Scott Tiger");
        assertFalse(doc.isDeleted());
        try {
            db.delete(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(404, e.getCode());
        }
        assertFalse(doc.isDeleted());
        assertEquals("Scott Tiger", doc.getObject("name"));
    }

    @Test
    public void testDeleteDocument() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        doc.setObject("name", "Scott Tiger");
        assertFalse(doc.isDeleted());

        // Save:
        save(doc);

        // Delete:
        db.delete(doc);
        assertTrue(doc.isDeleted());
        assertNull(doc.getObject("name"));
        assertEquals(new HashMap<>(), doc.toMap());
    }

    @Test
    public void testDictionaryAfterDeleteDocument() throws CouchbaseLiteException {
        Map<String, Object> addr = new HashMap<>();
        addr.put("street", "1 Main street");
        addr.put("city", "Mountain View");
        addr.put("state", "CA");
        Map<String, Object> dict = new HashMap<>();
        dict.put("address", addr);

        Document doc = createDocument("doc1", dict);
        save(doc);

        Dictionary address = doc.getDictionary("address");
        assertEquals("1 Main street", address.getObject("street"));
        assertEquals("Mountain View", address.getObject("city"));
        assertEquals("CA", address.getObject("state"));

        db.delete(doc);
        assertNull(doc.getDictionary("address"));
        assertEquals(new HashMap<>(), doc.toMap());

        // The dictionary still has data but is detached:
        assertEquals("1 Main street", address.getObject("street"));
        assertEquals("Mountain View", address.getObject("city"));
        assertEquals("CA", address.getObject("state"));

        // Make changes to the dictionary shouldn't affect the document.
        address.setObject("zip", "94042");
        assertNull(doc.getDictionary("address"));
        assertEquals(new HashMap<>(), doc.toMap());
    }

    @Test
    public void testArrayAfterDeleteDocument() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("members", Arrays.asList("a", "b", "c"));

        Document doc = createDocument("doc1", dict);
        save(doc);

        Array members = doc.getArray("members");
        assertEquals(3, members.count());
        assertEquals("a", members.getObject(0));
        assertEquals("b", members.getObject(1));
        assertEquals("c", members.getObject(2));


        db.delete(doc);
        assertNull(doc.getDictionary("members"));
        assertEquals(new HashMap<>(), doc.toMap());

        // The array still has data but is detached:

        assertEquals(3, members.count());
        assertEquals("a", members.getObject(0));
        assertEquals("b", members.getObject(1));
        assertEquals("c", members.getObject(2));

        // Make changes to the dictionary shouldn't affect the document.
        members.setObject(2, "1");
        members.addObject("2");
        assertNull(doc.getDictionary("members"));
        assertEquals(new HashMap<>(), doc.toMap());
    }

    @Test
    public void testPurgeDocument() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        doc.setObject("type", "profile");
        doc.setObject("name", "Scott");
        assertFalse(doc.isDeleted());

        // Purge before save:
        try {
            db.purge(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(404, e.getCode());
        }
        assertEquals("profile", doc.getObject("type"));
        assertEquals("Scott", doc.getObject("name"));

        //Save
        save(doc);
        assertFalse(doc.isDeleted());

        // purge
        db.purge(doc);
        assertNull(doc.getObject("type"));
        assertNull(doc.getObject("name"));
        assertFalse(doc.isDeleted());
    }

    @Test
    public void testReopenDB() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        doc.setObject("string", "str");
        save(doc);

        reopenDB();

        doc = db.getDocument("doc1");
        assertEquals("str", doc.getString("string"));
        Map<String, Object> expected = new HashMap<>();
        expected.put("string", "str");
        assertEquals(expected, doc.toMap());
    }

    @Test
    public void testBlob() throws IOException, CouchbaseLiteException {
        byte[] content = kDocumentTestBlob.getBytes();

        // store blob
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        Document doc = createDocument("doc1");
        doc.setObject("name", "Jim");
        doc.setObject("data", data);

        doc = save(doc);

        assertEquals("Jim", doc.getObject("name"));
        assertTrue(doc.getObject("data") instanceof Blob);
        data = (Blob) doc.getObject("data");
        assertEquals(8, data.length());
        assertTrue(Arrays.equals(content, data.getContent()));
        InputStream is = data.getContentStream();
        try {
            assertNotNull(is);
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            assertEquals(8, bytesRead);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                fail();
            }
        }
    }

    @Test
    public void testEmptyBlob() throws IOException, CouchbaseLiteException {
        byte[] content = "".getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        Document doc = createDocument("doc1");
        doc.setObject("data", data);

        doc = save(doc);

        assertTrue(doc.getObject("data") instanceof Blob);
        data = (Blob) doc.getObject("data");
        assertEquals(0, data.length());
        assertTrue(Arrays.equals(content, data.getContent()));
        InputStream is = data.getContentStream();
        try {
            assertNotNull(is);
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            assertEquals(0, bytesRead);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                fail();
            }
        }
    }

    @Test
    public void testBlobWithStream() throws IOException, CouchbaseLiteException {
        Document doc = createDocument("doc1");
        byte[] content = "".getBytes();
        InputStream stream = new ByteArrayInputStream(content);
        try {
            Blob data = new Blob("text/plain", stream);
            assertNotNull(data);
            doc.setObject("data", data);
            doc = save(doc);
        } finally {
            stream.close();
        }

        assertTrue(doc.getObject("data") instanceof Blob);
        Blob data = (Blob) doc.getObject("data");
        assertEquals(0, data.length());
        assertTrue(Arrays.equals(content, data.getContent()));
        InputStream is = data.getContentStream();
        try {
            assertNotNull(is);
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            assertEquals(0, bytesRead);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                fail();
            }
        }
    }

    @Test
    public void testMultipleBlobRead() throws IOException, CouchbaseLiteException {
        byte[] content = kDocumentTestBlob.getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        Document doc = createDocument("doc1");
        doc.setObject("data", data);

        data = (Blob) doc.getObject("data");
        for (int i = 0; i < 5; i++) {
            assertTrue(Arrays.equals(content, data.getContent()));
            InputStream is = data.getContentStream();
            try {
                assertNotNull(is);
                byte[] buffer = new byte[10];
                int bytesRead = is.read(buffer);
                assertEquals(8, bytesRead);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    fail();
                }
            }
        }

        doc = save(doc);

        assertTrue(doc.getObject("data") instanceof Blob);
        data = (Blob) doc.getObject("data");
        for (int i = 0; i < 5; i++) {
            assertTrue(Arrays.equals(content, data.getContent()));
            InputStream is = data.getContentStream();
            try {
                assertNotNull(is);
                byte[] buffer = new byte[10];
                int bytesRead = is.read(buffer);
                assertEquals(8, bytesRead);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    fail();
                }
            }
        }
    }

    @Test
    public void testReadExistingBlob() throws CouchbaseLiteException {
        byte[] content = kDocumentTestBlob.getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);

        Document doc = createDocument("doc1");
        doc.setObject("data", data);
        doc.setObject("name", "Jim");
        doc = save(doc);

        assertTrue(doc.getObject("data") instanceof Blob);
        data = (Blob) doc.getObject("data");
        assertTrue(Arrays.equals(content, data.getContent()));

        reopenDB();

        doc = db.getDocument("doc1");
        doc.setObject("foo", "bar");
        doc = save(doc);

        assertTrue(doc.getObject("data") instanceof Blob);
        data = (Blob) doc.getObject("data");
        assertTrue(Arrays.equals(content, data.getContent()));
    }

    @Test
    public void testEnumeratingKeys() throws CouchbaseLiteException {
        Document doc = createDocument("doc1");
        for (long i = 0; i < 20; i++)
            doc.setLong(String.format(Locale.ENGLISH, "key%d", i), i);
        Map<String, Object> content = doc.toMap();
        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (String key : doc) {
            result.put(key, doc.getObject(key));
            count++;
        }
        assertEquals(content, result);
        assertEquals(content.size(), count);

        doc.remove("key2");
        doc.setLong("key20", 20L);
        doc.setLong("key21", 21L);
        final Map<String, Object> content2 = doc.toMap();
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                Map<String, Object> content = doc.toMap();
                Map<String, Object> result = new HashMap<>();
                int count = 0;
                for (String key : doc) {
                    result.put(key, doc.getObject(key));
                    count++;
                }
                assertEquals(content.size(), count);
                assertEquals(content, result);
                assertEquals(content, content2);
            }
        });
    }
}
