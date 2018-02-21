//
// DictionaryTest.java
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

import com.couchbase.lite.internal.support.Log;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DictionaryTest extends BaseTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCreateDictionary() throws CouchbaseLiteException {
        MutableDictionary address = new MutableDictionary();
        assertEquals(0, address.count());
        assertEquals(new HashMap<String, Object>(), address.toMap());

        MutableDocument mDoc = createMutableDocument("doc1");
        mDoc.setValue("address", address);
        assertEquals(address, mDoc.getDictionary("address"));

        Document doc = save(mDoc);
        assertEquals(new HashMap<String, Object>(), doc.getDictionary("address").toMap());
    }

    @Test
    public void testCreateDictionaryWithNSDictionary() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");
        MutableDictionary address = new MutableDictionary(dict);
        assertEquals(3, address.count());
        assertEquals("1 Main street", address.getValue("street"));
        assertEquals("Mountain View", address.getValue("city"));
        assertEquals("CA", address.getValue("state"));
        assertEquals(dict, address.toMap());

        MutableDocument mDoc1 = createMutableDocument("doc1");
        mDoc1.setValue("address", address);
        assertEquals(address, mDoc1.getDictionary("address"));

        Document doc1 = save(mDoc1);
        assertEquals(dict, doc1.getDictionary("address").toMap());
    }

    @Test
    public void testGetValueFromNewEmptyDictionary() throws CouchbaseLiteException {
        MutableDictionary mDict = new MutableDictionary();

        assertEquals(0, mDict.getInt("key"));
        assertEquals(0.0f, mDict.getFloat("key"), 0.0f);
        assertEquals(0.0, mDict.getDouble("key"), 0.0);
        assertFalse(mDict.getBoolean("key"));
        assertTrue(mDict.getBlob("key") == null);
        assertTrue(mDict.getDate("key") == null);
        assertTrue(mDict.getNumber("key") == null);
        assertTrue(mDict.getValue("key") == null);
        assertTrue(mDict.getString("key") == null);
        assertTrue(mDict.getDictionary("key") == null);
        assertTrue(mDict.getArray("key") == null);
        assertEquals(new HashMap<String, Object>(), mDict.toMap());

        MutableDocument mDoc = createMutableDocument("doc1");
        mDoc.setValue("dict", mDict);

        Document doc = save(mDoc);

        Dictionary dict = doc.getDictionary("dict");

        assertEquals(0, dict.getInt("key"));
        assertEquals(0.0f, dict.getFloat("key"), 0.0f);
        assertEquals(0.0, dict.getDouble("key"), 0.0);
        assertFalse(dict.getBoolean("key"));
        assertTrue(dict.getBlob("key") == null);
        assertTrue(dict.getDate("key") == null);
        assertTrue(dict.getNumber("key") == null);
        assertTrue(dict.getValue("key") == null);
        assertTrue(dict.getString("key") == null);
        assertTrue(dict.getDictionary("key") == null);
        assertTrue(dict.getArray("key") == null);
        assertEquals(new HashMap<String, Object>(), dict.toMap());
    }

    @Test
    public void testSetNestedDictionaries() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");

        MutableDictionary level1 = new MutableDictionary();
        level1.setValue("name", "n1");
        doc.setValue("level1", level1);

        MutableDictionary level2 = new MutableDictionary();
        level2.setValue("name", "n2");
        doc.setValue("level2", level2);

        MutableDictionary level3 = new MutableDictionary();
        level3.setValue("name", "n3");
        doc.setValue("level3", level3);

        assertEquals(level1, doc.getDictionary("level1"));
        assertEquals(level2, doc.getDictionary("level2"));
        assertEquals(level3, doc.getDictionary("level3"));

        Map<String, Object> dict = new HashMap<>();
        Map<String, Object> l1 = new HashMap<>();
        l1.put("name", "n1");
        dict.put("level1", l1);
        Map<String, Object> l2 = new HashMap<>();
        l2.put("name", "n2");
        dict.put("level2", l2);
        Map<String, Object> l3 = new HashMap<>();
        l3.put("name", "n3");
        dict.put("level3", l3);
        assertEquals(dict, doc.toMap());

        Document savedDoc = save(doc);

        assertTrue(level1 != savedDoc.getDictionary("level1"));
        assertEquals(dict, savedDoc.toMap());
    }

    @Test
    public void testDictionaryArray() throws CouchbaseLiteException {
        MutableDocument mDoc = createMutableDocument("doc1");

        List<Object> data = new ArrayList<>();

        Map<String, Object> d1 = new HashMap<>();
        d1.put("name", "1");
        data.add(d1);
        Map<String, Object> d2 = new HashMap<>();
        d2.put("name", "2");
        data.add(d2);
        Map<String, Object> d3 = new HashMap<>();
        d3.put("name", "3");
        data.add(d3);
        Map<String, Object> d4 = new HashMap<>();
        d4.put("name", "4");
        data.add(d4);
        assertEquals(4, data.size());

        mDoc.setValue("array", data);

        MutableArray mArray = mDoc.getArray("array");
        assertEquals(4, mArray.count());

        MutableDictionary mDict1 = mArray.getDictionary(0);
        MutableDictionary mDict2 = mArray.getDictionary(1);
        MutableDictionary mDict3 = mArray.getDictionary(2);
        MutableDictionary mDict4 = mArray.getDictionary(3);

        assertEquals("1", mDict1.getString("name"));
        assertEquals("2", mDict2.getString("name"));
        assertEquals("3", mDict3.getString("name"));
        assertEquals("4", mDict4.getString("name"));

        // after save
        Document doc = save(mDoc);

        Array array = doc.getArray("array");
        assertEquals(4, array.count());

        Dictionary dict1 = array.getDictionary(0);
        Dictionary dict2 = array.getDictionary(1);
        Dictionary dict3 = array.getDictionary(2);
        Dictionary dict4 = array.getDictionary(3);

        assertEquals("1", dict1.getString("name"));
        assertEquals("2", dict2.getString("name"));
        assertEquals("3", dict3.getString("name"));
        assertEquals("4", dict4.getString("name"));
    }

    @Test
    public void testReplaceDictionary() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");

        MutableDictionary profile1 = new MutableDictionary();
        profile1.setValue("name", "Scott Tiger");
        doc.setValue("profile", profile1);
        assertEquals(profile1, doc.getDictionary("profile"));

        MutableDictionary profile2 = new MutableDictionary();
        profile2.setValue("name", "Daniel Tiger");
        doc.setValue("profile", profile2);
        assertEquals(profile2, doc.getDictionary("profile"));

        // Profile1 should be now detached:
        profile1.setValue("age", 20);
        assertEquals("Scott Tiger", profile1.getValue("name"));
        assertEquals(20, profile1.getValue("age"));

        // Check profile2:
        assertEquals("Daniel Tiger", profile2.getValue("name"));
        assertTrue(null == profile2.getValue("age"));

        // Save:
        Document savedDoc = save(doc);

        assertTrue(profile2 != savedDoc.getDictionary("profile"));
        Dictionary savedDict = savedDoc.getDictionary("profile");
        assertEquals("Daniel Tiger", savedDict.getValue("name"));
    }

    @Test
    public void testReplaceDictionaryDifferentType() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");

        MutableDictionary profile1 = new MutableDictionary();
        profile1.setValue("name", "Scott Tiger");
        doc.setValue("profile", profile1);
        assertEquals(profile1, doc.getDictionary("profile"));

        // Set string value to profile:
        doc.setValue("profile", "Daniel Tiger");
        assertEquals("Daniel Tiger", doc.getValue("profile"));

        // Profile1 should be now detached:
        profile1.setValue("age", 20);
        assertEquals("Scott Tiger", profile1.getValue("name"));
        assertEquals(20, profile1.getValue("age"));

        // Check whether the profile value has no change:
        assertEquals("Daniel Tiger", doc.getValue("profile"));

        // Save
        Document savedDoc = save(doc);
        assertEquals("Daniel Tiger", savedDoc.getValue("profile"));
    }

    @Test
    public void testRemoveDictionary() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("doc1");
        MutableDictionary profile1 = new MutableDictionary();
        profile1.setValue("name", "Scott Tiger");
        doc.setValue("profile", profile1);
        assertEquals(profile1.toMap(), doc.getDictionary("profile").toMap());
        assertTrue(doc.contains("profile"));

        // Remove profile
        doc.remove("profile");
        assertNull(doc.getValue("profile"));
        assertFalse(doc.contains("profile"));

        // Profile1 should be now detached:
        profile1.setValue("age", 20);
        assertEquals("Scott Tiger", profile1.getValue("name"));
        assertEquals(20, profile1.getValue("age"));

        // Check whether the profile value has no change:
        assertNull(doc.getValue("profile"));

        // Save:
        doc = save(doc).toMutable();

        assertNull(doc.getValue("profile"));
        assertFalse(doc.contains("profile"));
    }

    @Test
    public void testEnumeratingKeys() throws CouchbaseLiteException {
        final MutableDictionary dict = new MutableDictionary();
        for (int i = 0; i < 20; i++)
            dict.setValue(String.format(Locale.ENGLISH, "key%d", i), i);
        Map<String, Object> content = dict.toMap();

        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (String key : dict) {
            result.put(key, dict.getValue(key));
            count++;
        }
        assertEquals(content.size(), count);
        assertEquals(content, result);

        // Update:
        dict.remove("key2");
        dict.setValue("key20", 20);
        dict.setValue("key21", 21);
        content = dict.toMap();

        result = new HashMap<>();
        count = 0;
        for (String key : dict) {
            result.put(key, dict.getValue(key));
            count++;
        }
        assertEquals(content.size(), count);
        assertEquals(content, result);

        final Map<String, Object> finalContent = content;

        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("dict", dict);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                Map<String, Object> result = new HashMap<>();
                int count = 0;
                Dictionary dictObj = doc.getDictionary("dict");
                for (String key : dictObj) {
                    result.put(key, dict.getValue(key));
                    count++;
                }
                assertEquals(finalContent.size(), count);
                assertEquals(finalContent, result);
            }
        });
    }

    // TODO: MDict has isMuated() method, but unable to check mutated to mutated.
    // @Test
    public void testDictionaryEnumerationWithDataModification() throws CouchbaseLiteException {
        MutableDictionary dict = new MutableDictionary();
        for (int i = 0; i < 2; i++)
            dict.setValue(String.format(Locale.ENGLISH, "key%d", i), i);

        Iterator<String> itr = dict.iterator();
        int count = 0;
        try {
            while (itr.hasNext()) {
                itr.next();
                if (count++ == 0)
                    dict.setValue("key2", 2);
            }
            fail("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException e) {
            // Expected to come here!
        }
        assertEquals(3, dict.count());

        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("dict", dict);
        doc = save(doc).toMutable();
        dict = doc.getDictionary("dict");

        itr = dict.iterator();
        count = 0;
        try {
            while (itr.hasNext()) {
                itr.next();
                if (count++ == 0)
                    dict.setValue("key3", 3);
            }
            fail("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException e) {
            // Expected to come here!
        }
        assertEquals(4, dict.count());
    }

    // https://github.com/couchbase/couchbase-lite-core/issues/230
    @Test
    public void testLargeLongValue() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("test");
        long num1 = 1234567L;
        long num2 = 12345678L;
        long num3 = 123456789L;
        doc.setValue("num1", num1);
        doc.setValue("num2", num2);
        doc.setValue("num3", num3);
        doc = save(doc).toMutable();
        Log.i(TAG, "num1 long -> " + doc.getLong("num1"));
        Log.i(TAG, "num2 long -> " + doc.getLong("num2"));
        Log.i(TAG, "num3 long -> " + doc.getLong("num3"));
        assertEquals(num1, doc.getLong("num1"));
        assertEquals(num2, doc.getLong("num2"));
        assertEquals(num3, doc.getLong("num3"));
    }

    //https://forums.couchbase.com/t/long-value-on-document-changed-after-saved-to-db/14259/
    @Test
    public void testLargeLongValue2() throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument("test");
        long num1 = 11989091L;
        long num2 = 231548688L;
        doc.setValue("num1", num1);
        doc.setValue("num2", num2);
        doc = save(doc).toMutable();
        Log.i(TAG, "num1 long -> " + doc.getLong("num1"));
        Log.i(TAG, "num2 long -> " + doc.getLong("num2"));
        assertEquals(num1, doc.getLong("num1"));
        assertEquals(num2, doc.getLong("num2"));
    }

    @Test
    public void testSetNull() throws CouchbaseLiteException {
        MutableDocument mDoc = createMutableDocument("test");
        MutableDictionary mDict = new MutableDictionary();
        mDict.setValue("obj-null", null);
        mDict.setString("string-null", null);
        mDict.setNumber("number-null", null);
        mDict.setDate("date-null", null);
        mDict.setArray("array-null", null);
        mDict.setDictionary("dict-null", null);
        mDoc.setDictionary("dict", mDict);
        save(mDoc, new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("dict"));
                Dictionary d = doc.getDictionary("dict");
                assertNotNull(d);
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
            }
        });
    }

    @Test
    public void testEquals() throws CouchbaseLiteException {

        // mDict1 and mDict2 have exactly same data
        // mDict3 is different
        // mDict4 is different

        MutableDictionary mDict1 = new MutableDictionary();
        mDict1.setValue("key1", 1L);
        mDict1.setValue("key2", "Hello");
        mDict1.setValue("key3", null);

        MutableDictionary mDict2 = new MutableDictionary();
        mDict2.setValue("key1", 1L);
        mDict2.setValue("key2", "Hello");
        mDict2.setValue("key3", null);

        MutableDictionary mDict3 = new MutableDictionary();
        mDict3.setValue("key1", 100L);
        mDict3.setValue("key3", true);

        MutableDictionary mDict4 = new MutableDictionary();
        mDict4.setValue("key1", 100L);

        MutableDictionary mDict5 = new MutableDictionary();
        mDict4.setValue("key1", 100L);
        mDict3.setValue("key3", false);

        MutableDocument mDoc = createMutableDocument("test");
        mDoc.setDictionary("dict1", mDict1);
        mDoc.setDictionary("dict2", mDict2);
        mDoc.setDictionary("dict3", mDict3);
        mDoc.setDictionary("dict4", mDict4);
        mDoc.setDictionary("dict5", mDict5);

        Document doc = save(mDoc);
        Dictionary dict1 = doc.getDictionary("dict1");
        Dictionary dict2 = doc.getDictionary("dict2");
        Dictionary dict3 = doc.getDictionary("dict3");
        Dictionary dict4 = doc.getDictionary("dict4");
        Dictionary dict5 = doc.getDictionary("dict5");

        // compare dict1, dict2, mdict1, and mdict2
        assertTrue(dict1.equals(dict1));
        assertTrue(dict2.equals(dict2));
        assertTrue(dict1.equals(dict2));
        assertTrue(dict2.equals(dict1));
        assertTrue(dict1.equals(dict1.toMutable()));
        assertTrue(dict1.equals(dict2.toMutable()));
        assertTrue(dict1.toMutable().equals(dict1));
        assertTrue(dict2.toMutable().equals(dict1));
        assertTrue(dict1.equals(mDict1));
        assertTrue(dict1.equals(mDict2));
        assertTrue(dict2.equals(mDict1));
        assertTrue(dict2.equals(mDict2));
        assertTrue(mDict1.equals(dict1));
        assertTrue(mDict2.equals(dict1));
        assertTrue(mDict1.equals(dict2));
        assertTrue(mDict2.equals(dict2));
        assertTrue(mDict1.equals(mDict1));
        assertTrue(mDict2.equals(mDict2));
        assertTrue(mDict1.equals(mDict1));
        assertTrue(mDict2.equals(mDict2));

        // compare dict1, dict3, mdict1, and mdict3
        assertTrue(dict3.equals(dict3));
        assertFalse(dict1.equals(dict3));
        assertFalse(dict3.equals(dict1));
        assertFalse(dict1.equals(dict3.toMutable()));
        assertFalse(dict3.toMutable().equals(dict1));
        assertFalse(dict1.equals(mDict3));
        assertFalse(dict3.equals(mDict1));
        assertTrue(dict3.equals(mDict3));
        assertFalse(mDict3.equals(dict1));
        assertFalse(mDict1.equals(dict3));
        assertTrue(mDict3.equals(dict3));
        assertTrue(mDict3.equals(mDict3));
        assertTrue(mDict3.equals(mDict3));

        // compare dict1, dict4, mdict1, and mdict4
        assertTrue(dict4.equals(dict4));
        assertFalse(dict1.equals(dict4));
        assertFalse(dict4.equals(dict1));
        assertFalse(dict1.equals(dict4.toMutable()));
        assertFalse(dict4.toMutable().equals(dict1));
        assertFalse(dict1.equals(mDict4));
        assertFalse(dict4.equals(mDict1));
        assertTrue(dict4.equals(mDict4));
        assertFalse(mDict4.equals(dict1));
        assertFalse(mDict1.equals(dict4));
        assertTrue(mDict4.equals(dict4));
        assertTrue(mDict4.equals(mDict4));
        assertTrue(mDict4.equals(mDict4));

        // compare dict3, dict4, mdict3, and mdict4
        assertFalse(dict3.equals(dict4));
        assertFalse(dict4.equals(dict3));
        assertFalse(dict3.equals(dict4.toMutable()));
        assertFalse(dict4.toMutable().equals(dict3));
        assertFalse(dict3.equals(mDict4));
        assertFalse(dict4.equals(mDict3));
        assertFalse(mDict4.equals(dict3));
        assertFalse(mDict3.equals(dict4));

        // compare dict3, dict5, mdict3, and mdict5
        assertFalse(dict3.equals(dict5));
        assertFalse(dict5.equals(dict3));
        assertFalse(dict3.equals(dict5.toMutable()));
        assertFalse(dict5.toMutable().equals(dict3));
        assertFalse(dict3.equals(mDict5));
        assertFalse(dict5.equals(mDict3));
        assertFalse(mDict5.equals(dict3));
        assertFalse(mDict3.equals(dict5));

        // compare dict5, dict4, mDict5, and mdict4
        assertFalse(dict5.equals(dict4));
        assertFalse(dict4.equals(dict5));
        assertFalse(dict5.equals(dict4.toMutable()));
        assertFalse(dict4.toMutable().equals(dict5));
        assertFalse(dict5.equals(mDict4));
        assertFalse(dict4.equals(mDict5));
        assertFalse(mDict4.equals(dict5));
        assertFalse(mDict5.equals(dict4));

        // against other type
        assertFalse(dict3.equals(null));
        assertFalse(dict3.equals(new Object()));
        assertFalse(dict3.equals(1));
        assertFalse(dict3.equals(new HashMap<>()));
        assertFalse(dict3.equals(new MutableDictionary()));
        assertFalse(dict3.equals(new MutableArray()));
        assertFalse(dict3.equals(doc));
        assertFalse(dict3.equals(mDoc));
    }

    @Test
    public void testHashCode() throws CouchbaseLiteException {

        // mDict1 and mDict2 have exactly same data
        // mDict3 is different
        // mDict4 is different

        MutableDictionary mDict1 = new MutableDictionary();
        mDict1.setValue("key1", 1L);
        mDict1.setValue("key2", "Hello");
        mDict1.setValue("key3", null);

        MutableDictionary mDict2 = new MutableDictionary();
        mDict2.setValue("key1", 1L);
        mDict2.setValue("key2", "Hello");
        mDict2.setValue("key3", null);

        MutableDictionary mDict3 = new MutableDictionary();
        mDict3.setValue("key1", 100L);
        mDict3.setValue("key3", true);

        MutableDictionary mDict4 = new MutableDictionary();
        mDict4.setValue("key1", 100L);

        MutableDictionary mDict5 = new MutableDictionary();
        mDict4.setValue("key1", 100L);
        mDict3.setValue("key3", false);

        MutableDocument mDoc = createMutableDocument("test");
        mDoc.setDictionary("dict1", mDict1);
        mDoc.setDictionary("dict2", mDict2);
        mDoc.setDictionary("dict3", mDict3);
        mDoc.setDictionary("dict4", mDict4);
        mDoc.setDictionary("dict5", mDict5);

        Document doc = save(mDoc);
        Dictionary dict1 = doc.getDictionary("dict1");
        Dictionary dict2 = doc.getDictionary("dict2");
        Dictionary dict3 = doc.getDictionary("dict3");
        Dictionary dict4 = doc.getDictionary("dict4");
        Dictionary dict5 = doc.getDictionary("dict5");

        assertEquals(dict1.hashCode(), dict1.hashCode());
        assertEquals(dict1.hashCode(), dict2.hashCode());
        assertEquals(dict2.hashCode(), dict1.hashCode());
        assertEquals(dict1.hashCode(), dict1.toMutable().hashCode());
        assertEquals(dict1.hashCode(), dict2.toMutable().hashCode());
        assertEquals(dict1.hashCode(), mDict1.hashCode());
        assertEquals(dict1.hashCode(), mDict2.hashCode());
        assertEquals(dict2.hashCode(), mDict1.hashCode());
        assertEquals(dict2.hashCode(), mDict2.hashCode());

        assertFalse(dict3.hashCode() == dict1.hashCode());
        assertFalse(dict3.hashCode() == dict2.hashCode());
        assertFalse(dict3.hashCode() == dict1.toMutable().hashCode());
        assertFalse(dict3.hashCode() == dict2.toMutable().hashCode());
        assertFalse(dict3.hashCode() == mDict1.hashCode());
        assertFalse(dict3.hashCode() == mDict2.hashCode());
        assertFalse(mDict3.hashCode() == dict1.hashCode());
        assertFalse(mDict3.hashCode() == dict2.hashCode());
        assertFalse(mDict3.hashCode() == dict1.toMutable().hashCode());
        assertFalse(mDict3.hashCode() == dict2.toMutable().hashCode());
        assertFalse(mDict3.hashCode() == mDict1.hashCode());
        assertFalse(mDict3.hashCode() == mDict2.hashCode());

        assertFalse(dict3.hashCode() == 0);
        assertFalse(dict3.hashCode() == new Object().hashCode());
        assertFalse(dict3.hashCode() == new Integer(1).hashCode());
        assertFalse(dict3.hashCode() == new HashMap<>().hashCode());
        assertFalse(dict3.hashCode() == new MutableDictionary().hashCode());
        assertFalse(dict3.hashCode() == new MutableArray().hashCode());
        assertFalse(mDict3.hashCode() == doc.hashCode());
        assertFalse(mDict3.hashCode() == mDoc.hashCode());
        assertFalse(mDict3.hashCode() == dict1.toMutable().hashCode());
        assertFalse(mDict3.hashCode() == dict2.toMutable().hashCode());
        assertFalse(mDict3.hashCode() == mDict1.hashCode());
        assertFalse(mDict3.hashCode() == mDict2.hashCode());
    }

    @Test
    public void testGetDictionary() throws CouchbaseLiteException {
        MutableDictionary mNestedDict = new MutableDictionary();
        mNestedDict.setValue("key1", 1L);
        mNestedDict.setValue("key2", "Hello");
        mNestedDict.setValue("key3", null);

        MutableDictionary mDict = new MutableDictionary();
        mDict.setValue("key1", 1L);
        mDict.setValue("key2", "Hello");
        mDict.setValue("key3", null);
        mDict.setValue("nestedDict", mNestedDict);

        MutableDocument mDoc = createMutableDocument("test");
        mDoc.setDictionary("dict", mDict);

        Document doc = save(mDoc);
        Dictionary dict = doc.getDictionary("dict");

        assertNotNull(dict);
        assertNull(dict.getDictionary("not-exists"));
        assertNotNull(dict.getDictionary("nestedDict"));

        Dictionary nestedDict = dict.getDictionary("nestedDict");
        assertTrue(nestedDict.equals(mNestedDict));
        assertTrue(dict.equals(mDict));
    }

    @Test
    public void testGetArray() throws CouchbaseLiteException {
        MutableArray mNestedArray = new MutableArray();
        mNestedArray.addValue(1L);
        mNestedArray.addValue("Hello");
        mNestedArray.addValue(null);

        MutableArray mArray = new MutableArray();
        mArray.addValue(1L);
        mArray.addValue("Hello");
        mArray.addValue(null);
        mArray.addValue(mNestedArray);

        MutableDocument mDoc = createMutableDocument("test");
        mDoc.setValue("array", mArray);

        Document doc = save(mDoc);
        Array array = doc.getArray("array");

        assertNotNull(array);
        assertNull(array.getArray(0));
        assertNull(array.getArray(1));
        assertNull(array.getArray(2));
        assertNotNull(array.getArray(3));

        thrown.expect(IndexOutOfBoundsException.class);
        assertNull(array.getArray(4));

        Array nestedArray = array.getArray(3);
        assertTrue(nestedArray.equals(mNestedArray));
        assertTrue(array.equals(mArray));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1518
    @Test
    public void testSetValueWithDictionary() throws CouchbaseLiteException {
        MutableDictionary mDict = new MutableDictionary();
        mDict.setString("hello", "world");

        MutableDocument mDoc = createMutableDocument("doc1");
        mDoc.setValue("dict", mDict);
        Document doc = save(mDoc);

        Dictionary dict = doc.getDictionary("dict");

        mDoc = doc.toMutable();
        mDoc.setValue("dict2", dict);
        doc = save(mDoc);

        dict = doc.getDictionary("dict2");
        assertEquals(1, dict.count());
        assertEquals("world", dict.getString("hello"));
    }

    @Test
    public void testSetValueWithArray() throws CouchbaseLiteException {
        MutableArray mArray = new MutableArray();
        mArray.addString("hello");
        mArray.addString("world");

        MutableDocument mDoc = createMutableDocument("doc1");
        mDoc.setValue("array", mArray);
        Document doc = save(mDoc);

        Array array = doc.getArray("array");

        mDoc = doc.toMutable();
        mDoc.setValue("array2", array);
        doc = save(mDoc);

        array = doc.getArray("array2");
        assertEquals(2, array.count());
        assertEquals("hello", array.getString(0));
        assertEquals("world", array.getString(1));
    }
}
