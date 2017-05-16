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

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorBadDocID;
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
        doc.set("true", true);
        doc.set("false", false);
        doc.set("string", "string");
        doc.set("zero", 0);
        doc.set("one", 1);
        doc.set("minus_one", -1);
        doc.set("one_dot_one", 1.1);
        doc.set("date", DateUtils.fromJson(kDocumentTestDate));
        doc.set("null", null);

        // Dictionary:
        Dictionary dict = new Dictionary();
        dict.set("street", "1 Main street");
        dict.set("city", "Mountain View");
        dict.set("state", "CA");
        doc.set("dict", dict);

        // Array:
        Array array = new Array();
        array.add("650-123-0001");
        array.add("650-123-0002");
        doc.set("array", array);

        // Blob:
        byte[] content = kDocumentTestBlob.getBytes();
        Blob blob = new Blob("text/plain", content);
        doc.set("blob", blob);
    }

    @Before
    public void setUp() {
        super.setUp();
    }

    @After
    public void tearDown() {
        super.tearDown();
    }


    @Test
    public void testCreateDoc() {
        Document doc1a = new Document();
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertFalse(doc1a.isDeleted());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        db.save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
    }


    @Test
    public void testNewDocWithId() {
        Document doc1a = new Document("doc1");
        assertNotNull(doc1a);
        assertEquals("doc1", doc1a.getId());
        assertFalse(doc1a.isDeleted());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        db.save(doc1a);
        Document doc1b = db.getDocument("doc1");
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithEmptyStringID() {
        Document doc1a = new Document("");
        assertNotNull(doc1a);
        try {
            db.save(doc1a);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(LiteCoreDomain, e.getDomain());
            assertEquals(kC4ErrorBadDocID, e.getCode());
        }
    }

    @Test
    public void testCreateDocWithNilID() {
        Document doc1a = new Document((String) null);
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertFalse(doc1a.isDeleted());
        assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        db.save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        Document doc1a = new Document(dict);
        assertNotNull(doc1a);
        assertTrue(doc1a.getId().length() > 0);
        assertFalse(doc1a.isDeleted());
        assertEquals(dict, doc1a.toMap());

        db.save(doc1a);
        Document doc1b = db.getDocument(doc1a.getId());
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
        assertEquals(dict, doc1b.toMap());
    }

    @Test
    public void testCreateDocWithIDAndDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        Document doc1a = new Document("doc1", dict);
        assertNotNull(doc1a);
        assertEquals("doc1", doc1a.getId());
        assertFalse(doc1a.isDeleted());
        assertEquals(dict, doc1a.toMap());

        db.save(doc1a);
        Document doc1b = db.getDocument("doc1");
        assertNotNull(doc1b);
        assertTrue(doc1a != doc1b);
        assertTrue(doc1b.exists());
        assertFalse(doc1b.isDeleted());
        assertEquals(doc1a.getId(), doc1b.getId());
        assertEquals(dict, doc1b.toMap());
    }

    @Test
    public void testSetDictionaryContent() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        Document doc = new Document("doc1");
        doc.set(dict);
        assertEquals(dict, doc.toMap());

        db.save(doc);
        doc = db.getDocument("doc1");
        assertEquals(dict, doc.toMap());

        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("name", "Danial Tiger");
        nuDict.put("age", 32);

        Map<String, Object> nuAddress = new HashMap<>();
        nuAddress.put("street", "2 Main street");
        nuAddress.put("city", "Palo Alto");
        nuAddress.put("state", "CA");
        nuDict.put("address", nuAddress);

        nuDict.put("phones", Arrays.asList("650-234-0001", "650-234-0002"));

        doc.set(nuDict);
        assertEquals(nuDict, doc.toMap());

        db.save(doc);
        doc = db.getDocument("doc1");
        assertEquals(nuDict, doc.toMap());
    }


    @Test
    public void testGetValueFromNewEmptyDoc() {
        Document doc = new Document("doc1");
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
    public void testGetValueFromExistingEmptyDoc() {
        Document doc = new Document("doc1");
        db.save(doc);
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
    public void testSaveThenGetFromAnotherDB() {
        Document doc1a = new Document("doc1");
        doc1a.set("name", "Scott Tiger");
        db.save(doc1a);

        Database anotherDb = db.copy();
        Document doc1b = anotherDb.getDocument("doc1");
        assertTrue(doc1a != doc1b);
        assertEquals(doc1a.getId(), doc1b.getId());
        assertEquals(doc1a.toMap(), doc1b.toMap());
        anotherDb.close();
    }

    @Test
    public void testNoCacheNoLive() {
        Document doc1a = new Document("doc1");
        doc1a.set("name", "Scott Tiger");

        db.save(doc1a);

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

        doc1b.set("name", "Daniel Tiger");
        db.save(doc1b);

        assertNotEquals(doc1b.toMap(), doc1a.toMap());
        assertNotEquals(doc1b.toMap(), doc1c.toMap());
        assertNotEquals(doc1b.toMap(), doc1d.toMap());

        anotherDb.close();
    }

    @Test
    public void testSetString() {
        Document doc = new Document("doc1");
        doc.set("string1", "");
        doc.set("string2", "string");
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals("", d.getObject("string1"));
                assertEquals("string", d.getObject("string2"));
            }
        });

        doc.set("string1", "string");
        doc.set("string2", "");
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals("string", d.getObject("string1"));
                assertEquals("", d.getObject("string2"));
            }
        });
    }

    @Test
    public void testGetString() {
        Document doc = new Document("doc1");
        populateData(doc);
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

    @Test
    public void testSetNumber() {
        Document doc = new Document("doc1");

        doc.set("number1", 1);
        doc.set("number2", 0);
        doc.set("number3", -1);
        doc.set("number4", 1.1);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(1, d.getObject("number1"));
                assertEquals(0, d.getObject("number2"));
                assertEquals(-1, d.getObject("number3"));
                assertEquals(1.1, d.getObject("number4"));
            }
        });

        // Update:

        doc.set("number1", 0);
        doc.set("number2", 1);
        doc.set("number3", 1.1);
        doc.set("number4", -1);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(0, d.getObject("number1"));
                assertEquals(1, d.getObject("number2"));
                assertEquals(1.1, d.getObject("number3"));
                assertEquals(-1, d.getObject("number4"));
            }
        });
    }

    @Test
    public void testGetNumber() {
        Document doc = new Document("doc1");
        populateData(doc);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(final Document d) {
                assertNull(d.getNumber("null"));
                assertNull(d.getNumber("true"));
                assertNull(d.getNumber("false"));
                assertNull(d.getNumber("string"));
                assertEquals(0, d.getNumber("zero"));
                assertEquals(1, d.getNumber("one"));
                assertEquals(-1, d.getNumber("minus_one"));
                assertEquals(1.1, d.getNumber("one_dot_one"));
                assertNull(d.getNumber("date"));
                assertNull(d.getNumber("dict"));
                assertNull(d.getNumber("array"));
                assertNull(d.getNumber("blob"));
                assertNull(d.getNumber("non_existing_key"));
            }
        });
    }

    @Test
    public void testGetInteger() {
        Document doc = new Document("doc1");
        populateData(doc);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(final Document d) {
                assertEquals(0, d.getInt("null"));
                //TODO
                //assertEquals(1, d.getInt("true"));
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

    @Test
    public void testGetLong() {
        Document doc = new Document("doc1");
        populateData(doc);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(final Document d) {
                assertEquals(0, d.getLong("null"));
                //TODO
                //assertEquals(0, d.getLong("true"));
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

    @Test
    public void testGetFloat() {
        Document doc = new Document("doc1");
        populateData(doc);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(final Document d) {
                assertEquals(0.0f, d.getFloat("null"), 0.0f);
                //TODO
                //assertEquals(0.0f, d.getFloat("true"), 0.0f);
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

    @Test
    public void testGetDouble() {
        Document doc = new Document("doc1");
        populateData(doc);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(final Document d) {
                assertEquals(0.0, d.getDouble("null"), 0.0);
                //TODO
                //assertEquals(0.0, d.getDouble("true"), 0.0);
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

    @Test
    public void testSetGetMinMaxNumbers() {
        Document doc = new Document("doc1");

        doc.set("min_int", Integer.MIN_VALUE);
        doc.set("max_int", Integer.MAX_VALUE);
        doc.set("min_long", Long.MIN_VALUE);
        doc.set("max_long", Long.MAX_VALUE);
        doc.set("min_float", Float.MIN_VALUE);
        doc.set("max_float", Float.MAX_VALUE);
        doc.set("min_double", Double.MIN_VALUE);
        doc.set("max_double", Double.MAX_VALUE);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(Integer.MIN_VALUE, doc.getNumber("min_int"));
                assertEquals(Integer.MAX_VALUE, doc.getNumber("max_int"));
                assertEquals(Integer.MIN_VALUE, doc.getObject("min_int"));
                assertEquals(Integer.MAX_VALUE, doc.getObject("max_int"));
                assertEquals(Integer.MIN_VALUE, doc.getInt("min_int"));
                assertEquals(Integer.MAX_VALUE, doc.getInt("max_int"));

                //TODO: Long does not work with current implementation
//                assertEquals(Long.MIN_VALUE, doc.getNumber("min_long"));
//                assertEquals(Long.MAX_VALUE, doc.getNumber("max_long"));
//                assertEquals(Long.MIN_VALUE, doc.getObject("min_long"));
//                assertEquals(Long.MAX_VALUE, doc.getObject("max_long"));
//                assertEquals(Long.MIN_VALUE, doc.getLong("min_long"));
//                assertEquals(Long.MAX_VALUE, doc.getLong("max_long"));

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
        });
    }

    @Test
    public void testSetGetFloatNumbers() {
        Document doc = new Document("doc1");

        doc.set("number1", 1.00);
        doc.set("number2", 1.49);
        doc.set("number3", 1.50);
        doc.set("number4", 1.51);
        doc.set("number5", 1.99);


        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                // TODO
                //assertEquals(1.00, doc.getObject("number1"));  // return 1
                //assertEquals(1.00, doc.getNumber("number1"));  // return 1
                assertEquals(1, doc.getInt("number1"));
                assertEquals(1L, doc.getLong("number1"));
                assertEquals(1.00F, doc.getFloat("number1"), 0.0F);
                assertEquals(1.00, doc.getDouble("number1"), 0.0);

                //assertEquals(1.49, doc.getObject("number2"));  // return 1
                //assertEquals(1.49, doc.getNumber("number2"));  // return 1
                assertEquals(1, doc.getInt("number2"));
                assertEquals(1L, doc.getLong("number2"));
                assertEquals(1.49F, doc.getFloat("number2"), 0.0F);
                assertEquals(1.49, doc.getDouble("number2"), 0.0);

                //assertEquals(1.50, doc.getObject("number3"));  // return 1
                //assertEquals(1.50, doc.getNumber("number3"));  // return 1
                // TODO: lite core bug
                //assertEquals(1, doc.getInt("number3"));
                //assertEquals(1L, doc.getLong("number3"));
                assertEquals(1.50F, doc.getFloat("number3"), 0.0F);
                assertEquals(1.50, doc.getDouble("number3"), 0.0);

                //assertEquals(1.51, doc.getObject("number4"));  // return 1
                //assertEquals(1.51, doc.getNumber("number4"));  // return 1
                //assertEquals(1, doc.getInt("number4"));
                //assertEquals(1L, doc.getLong("number4"));
                assertEquals(1.51F, doc.getFloat("number4"), 0.0F);
                assertEquals(1.51, doc.getDouble("number4"), 0.0);

                //assertEquals(1.99, doc.getObject("number5"));  // return 1
                //assertEquals(1.99, doc.getNumber("number5"));  // return 1
                //assertEquals(1, doc.getInt("number5"));
                //assertEquals(1L, doc.getLong("number5"));
                assertEquals(1.99F, doc.getFloat("number5"), 0.0F);
                assertEquals(1.99, doc.getDouble("number5"), 0.0);
            }
        });
    }

    @Test
    public void testSetBoolean() {
        Document doc = new Document("doc1");

        doc.set("boolean1", true);
        doc.set("boolean2", false);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(true, d.getObject("boolean1"));
                assertEquals(false, d.getObject("boolean2"));
                assertEquals(true, d.getBoolean("boolean1"));
                assertEquals(false, d.getBoolean("boolean2"));
            }
        });

        // Update:

        doc.set("boolean1", false);
        doc.set("boolean2", true);

        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(false, d.getObject("boolean1"));
                assertEquals(true, d.getObject("boolean2"));
                assertEquals(false, d.getBoolean("boolean1"));
                assertEquals(true, d.getBoolean("boolean2"));
            }
        });
    }

    @Test
    public void testGetBoolean() {
        Document doc = new Document("doc1");
        populateData(doc);
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

    @Test
    public void testSetDate() {
        Document doc = new Document("doc1");

        Date date = new Date();
        final String dateStr = DateUtils.toJson(date);
        assertTrue(dateStr.length() > 0);
        doc.set("date", date);

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
        doc.set("date", nuDate);

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
    public void testGetDate() {
        Document doc = new Document("doc1");
        populateData(doc);
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


    @Test
    public void testSetBlob() {
        Document doc = new Document("doc1");

        final Blob blob = new Blob("text/plain", kDocumentTestBlob.getBytes());
        doc.set("blob", blob);


        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(blob.getProperties().toString(), d.getBlob("blob").getProperties().toString());
                assertEquals(blob.getProperties().toString(), ((Blob) d.getObject("blob")).getProperties().toString());
                assertEquals(kDocumentTestBlob, new String(d.getBlob("blob").getContent()));
                assertTrue(Arrays.equals(kDocumentTestBlob.getBytes(), d.getBlob("blob").getContent()));
            }
        });

        // Update:
        final Blob nuBlob = new Blob("text/plain", "1234567890".getBytes());
        doc.set("blob", nuBlob);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertEquals(nuBlob.getProperties().toString(), d.getBlob("blob").getProperties().toString());
                assertEquals(nuBlob.getProperties().toString(), ((Blob) d.getObject("blob")).getProperties().toString());
                assertEquals("1234567890", new String(d.getBlob("blob").getContent()));
                assertTrue(Arrays.equals("1234567890".getBytes(), d.getBlob("blob").getContent()));
            }
        });
    }

    @Test
    public void testGetBlob() {
        Document doc = new Document("doc1");
        populateData(doc);
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
                assertTrue(Arrays.equals(kDocumentTestBlob.getBytes(), d.getBlob("blob").getContent()));
                assertNull(d.getBlob("non_existing_key"));
            }
        });
    }

    @Test
    public void testSetDictionary() {
        Document doc = new Document("doc1");

        Dictionary dict = new Dictionary();
        dict.set("street", "1 Main street");
        doc.set("dict", dict);

        assertEquals(dict, doc.getObject("dict"));
        assertEquals(dict.toMap(), ((Dictionary) doc.getObject("dict")).toMap());

        db.save(doc);
        doc = db.getDocument("doc1");

        assertTrue(dict != doc.getObject("dict"));
        assertEquals(doc.getObject("dict"), doc.getDictionary("dict"));
        assertEquals(dict.toMap(), ((Dictionary) doc.getObject("dict")).toMap());

        // Update:
        dict = doc.getDictionary("dict");
        dict.set("city", "Mountain View");

        assertEquals(doc.getObject("dict"), doc.getDictionary("dict"));
        Map<String, Object> map = new HashMap<>();
        map.put("street", "1 Main street");
        map.put("city", "Mountain View");
        assertEquals(map, doc.getDictionary("dict").toMap());

        db.save(doc);
        doc = db.getDocument("doc1");

        assertTrue(dict != doc.getObject("dict"));
        assertEquals(doc.getObject("dict"), doc.getDictionary("dict"));
        assertEquals(map, doc.getDictionary("dict").toMap());
    }

    @Test
    public void testGetDictionary() {
        Document doc = new Document("doc1");
        populateData(doc);
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

    @Test
    public void testSetArray() {
        Document doc = new Document("doc1");

        Array array = new Array();
        array.add("item1");
        array.add("item2");
        array.add("item3");
        doc.set("array", array);

        assertEquals(array, doc.getObject("array"));
        assertEquals(array.toList(), ((Array) doc.getObject("array")).toList());

        db.save(doc);
        doc = db.getDocument("doc1");

        assertTrue(array != doc.getObject("array"));
        assertEquals(doc.getObject("array"), doc.getArray("array"));
        assertEquals(array.toList(), ((Array) doc.getObject("array")).toList());

        // Update:
        array = doc.getArray("array");
        array.add("item4");
        array.add("item5");

        db.save(doc);
        doc = db.getDocument("doc1");

        assertTrue(array != doc.getObject("array"));
        assertEquals(doc.getObject("array"), doc.getArray("array"));
        List<String> list = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        assertEquals(list, doc.getArray("array").toList());
    }

    @Test
    public void testGetArray() {
        Document doc = new Document("doc1");
        populateData(doc);
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

    @Test
    public void testSetNull() {
        Document doc = new Document("doc1");
        doc.set("null", null);
        assertEquals(1, doc.count());
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document d) {
                assertNull(d.getObject("null"));
                assertEquals(1, d.count());
            }
        });
    }


    @Test
    public void testSetMap() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");

        Document doc = new Document("doc1");
        doc.set("address", dict);

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
        doc.set("address", nuDict);

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
        nuAddress.set("zip", "94302");
        assertEquals("94302", nuAddress.getString("zip"));
        assertNull(address.getString("zip"));

        // Save:
        db.save(doc);
        doc = db.getDocument(doc.getId());

        nuDict.put("zip", "94302");
        Map<String, Object> expected = new HashMap<>();
        expected.put("address", nuDict);
        assertEquals(expected, doc.toMap());
    }


    @Test
    public void testSetNSArray() {
        //TODO
    }

    @Test
    public void testUpdateNestedDictionary() {
        //TODO
    }

    @Test
    public void testUpdateDictionaryInArray() {
        //TODO
    }

    @Test
    public void testUpdateNestedArray() {
        //TODO
    }

    @Test
    public void testUpdateArrayInDictionary() {
        //TODO
    }

    @Test
    public void testSetDictionaryToMultipleKeys() {
        //TODO
    }

    @Test
    public void testSetArrayToMultipleKeys() {
        //TODO
    }

    @Test
    public void testToDictionary() {
        //TODO
    }

    @Test
    public void testRemoveKeys() {
        //TODO
    }

    @Test
    public void testContainsKey() {
        //TODO
    }

    @Test
    public void testDeleteNewDocument() {
        //TODO
    }

    @Test
    public void testDeleteDocument() {
        //TODO
    }

    @Test
    public void testDictionaryAfterDeleteDocument() {
        //TODO
    }

    @Test
    public void testArrayAfterDeleteDocument() {
        //TODO
    }

    @Test
    public void testPurgeDocument() {
        //TODO
    }

    @Test
    public void testReopenDB() {
        //TODO
    }

    @Test
    public void testBlob() {
        //TODO
    }

    @Test
    public void testEmptyBlob() {
        //TODO
    }

    @Test
    public void testBlobWithStream() {
        //TODO
    }

    @Test
    public void testMultipleBlobRead() {
        //TODO
    }

    @Test
    public void testReadExistingBlob() {
        //TODO
    }

/*
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
    public void testSetNullKey() {
        Document doc = db.getDocument("doc1");
        try {
            doc.set(null, null);
            fail();
        } catch (IllegalArgumentException e) {
            // OK!
        }
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

        // null(s):
        doc.set("null", null);
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

        // TODO: Confirm spec how unsaved doc deletion should behave
        
//        // Delete before save:
//        try {
//            doc.delete();
//            fail("CouchbaseLiteException expected");
//        } catch (CouchbaseLiteException e) {
//            // should be com here...
//            assertEquals(LiteCoreDomain, e.getDomain());
//        }
//        assertEquals("profile", doc.get("type"));
//        assertEquals("Scott", doc.get("name"));

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
    public void testBlob() throws CouchbaseLiteException, IOException {
        byte[] content = "12345".getBytes();

        // store blob
        {

            Blob data = new Blob("text/plain", content);
            assertNotNull(data);
            doc.set("name", "Jim");
            doc.set("data", data);
            doc.save();

            assertEquals("Jim", doc.get("name"));
            assertTrue(doc.get("data") instanceof Blob);
            data = (Blob) doc.get("data");
            assertEquals(5, data.length());
            assertTrue(Arrays.equals(content, data.getContent()));

            closeDB();
        }

        // obtain blob
        {
            openDB();
            Document doc1 = db.getDocument("doc1");
            assertEquals("Jim", doc1.get("name"));
            assertTrue(doc1.get("data") instanceof Blob);
            Blob data = (Blob) doc1.get("data");
            assertEquals(5, data.length());
            assertTrue(Arrays.equals(content, data.getContent()));
            InputStream is = data.getContentStream();
            try {
                assertNotNull(is);
                byte[] buffer = new byte[10];
                int bytesRead = is.read(buffer);
                assertEquals(5, bytesRead);
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
    public void testEmptyBlob() throws IOException {
        Document doc = db.getDocument("doc1");
        byte[] content = "".getBytes();
        Blob data = new Blob("text/plain", content);
        assertNotNull(data);
        doc.set("data", data);
        doc.save();

        Database copyOfDB = db.copy();
        try {
            Document doc1 = copyOfDB.getDocument("doc1");
            assertTrue(doc1.get("data") instanceof Blob);
            data = (Blob) doc1.get("data");
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
        } finally {
            copyOfDB.close();
        }
    }

    @Test
    public void testBlobWithStream() throws IOException {
        byte[] content = "".getBytes();
        InputStream stream = new ByteArrayInputStream(content);
        try {
            Blob data = new Blob("text/plain", stream);
            assertNotNull(data);
            doc.set("data", data);
            doc.save();
        } finally {
            stream.close();
        }

        Database copy = db.copy();
        try {
            Document doc1 = db.getDocument("doc1");
            assertTrue(doc1.get("data") instanceof Blob);
            Blob data = (Blob) doc1.get("data");
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
        } finally {
            copy.close();
        }
    }

    @Test
    public void testMultipleBlobRead() throws IOException {
        byte[] content = "12345".getBytes();

        Blob data = new Blob("text/plain", content);
        assertNotNull(data);
        doc.set("data", data);

        data = (Blob) doc.get("data");
        for (int i = 0; i < 5; i++) {
            assertTrue(Arrays.equals(content, data.getContent()));
            InputStream is = data.getContentStream();
            try {
                assertNotNull(is);
                byte[] buffer = new byte[10];
                int bytesRead = is.read(buffer);
                assertEquals(5, bytesRead);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    fail();
                }
            }
        }

        doc.save();

        Database copy = db.copy();
        try {
            Document doc1 = db.getDocument("doc1");
            assertTrue(doc1.get("data") instanceof Blob);
            data = (Blob) doc1.get("data");
            for (int i = 0; i < 5; i++) {
                assertTrue(Arrays.equals(content, data.getContent()));
                InputStream is = data.getContentStream();
                try {
                    assertNotNull(is);
                    byte[] buffer = new byte[10];
                    int bytesRead = is.read(buffer);
                    assertEquals(5, bytesRead);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        fail();
                    }
                }
            }
        } finally {
            copy.close();
        }
    }

    @Test
    public void testReadExistingBlob() {
        byte[] content = "12345".getBytes();

        Blob data = new Blob("text/plain", content);
        assertNotNull(data);
        doc.set("data", data);
        doc.set("name", "Jim");
        doc.save();

        assertTrue(doc.get("data") instanceof Blob);

        reopenDB();

        assertTrue(doc.get("data") instanceof Blob);
        data = (Blob) doc.get("data");
        assertTrue(Arrays.equals(content, data.getContent()));

        reopenDB();

        doc.set("foo", "bar");
        doc.save();

        assertTrue(doc.get("data") instanceof Blob);
        data = (Blob) doc.get("data");
        assertTrue(Arrays.equals(content, data.getContent()));
    }

    @Test
    public void testBlobInNestedMap() {
        byte[] content = "12345".getBytes();

        Blob data = new Blob("text/plain", content);
        Map<String, Object> map = new HashMap<>();
        map.put("data", data);
        doc.set("map", map);

        assertTrue(map.get("data") instanceof Blob);
        data = (Blob) map.get("data");
        assertTrue(Arrays.equals(content, data.getContent()));

        doc.save();

        map = (Map<String, Object>) doc.get("map");
        assertNotNull(map);
        assertTrue(map.get("data") instanceof Blob);
        data = (Blob) map.get("data");
        assertTrue(Arrays.equals(content, data.getContent()));

        reopenDB();

        doc = db.getDocument("doc1");
        map = (Map<String, Object>) doc.get("map");
        assertNotNull(map);
        assertTrue(map.get("data") instanceof Blob);
        data = (Blob) map.get("data");
        assertTrue(Arrays.equals(content, data.getContent()));
    }

    @Test
    public void testCrashWithBlob() throws CouchbaseLiteException, IOException {
        DatabaseConfiguration options = new DatabaseConfiguration();
        options.setDirectory(dir);
        Database db1 = new Database("abc", options);
        Document doc1 = db1.getDocument("doc1");

        byte[] content = "12345".getBytes();
        Blob data = new Blob("text/plain", content);
        doc1.set("data", data);
        doc1.save();
        db1.close();
    }

    @Test
    public void testSetProperties() throws Exception {
        loadJSONResource("names_100.json");
        assertEquals(100, db.internal().getDocumentCount());
    }


    */
}
