//
// ArrayTest.java
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

import com.couchbase.lite.internal.utils.DateUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArrayTest extends BaseTest {

    static final String kArrayTestDate = "2007-06-30T08:34:09.001Z";
    static final String kArrayTestBlob = "i'm blob";

    private List<Object> arrayOfAllTypes() {
        List<Object> list = new ArrayList<>();
        list.add(true);
        list.add(false);
        list.add("string");
        list.add(0);
        list.add(1);
        list.add(-1);
        list.add(1.1);

        list.add(DateUtils.fromJson(kArrayTestDate));
        list.add(null);

        // Dictionary
        MutableDictionary subdict = new MutableDictionary();
        subdict.setValue("name", "Scott Tiger");
        list.add(subdict);

        // Array
        MutableArray subarray = new MutableArray();
        subarray.addValue("a");
        subarray.addValue("b");
        subarray.addValue("c");
        list.add(subarray);

        // Blob
        byte[] content = kArrayTestBlob.getBytes();
        Blob blob = new Blob("text/plain", content);
        list.add(blob);

        return list;
    }

    private void populateData(MutableArray array) {
        List<Object> data = arrayOfAllTypes();
        for (Object o : data) {
            array.addValue(o);
        }
    }

    private void populateDataByType(MutableArray array) {
        List<Object> data = arrayOfAllTypes();
        for (Object o : data) {
            if (o instanceof Integer)
                array.addInt(((Integer) o).intValue());
            else if (o instanceof Long)
                array.addLong(((Long) o).longValue());
            else if (o instanceof Float)
                array.addFloat(((Float) o).floatValue());
            else if (o instanceof Double)
                array.addDouble(((Double) o).doubleValue());
            else if (o instanceof Number)
                array.addNumber((Number) o);
            else if (o instanceof String)
                array.addString((String) o);
            else if (o instanceof Boolean)
                array.addBoolean(((Boolean) o).booleanValue());
            else if (o instanceof Date)
                array.addDate((Date) o);
            else if (o instanceof Blob)
                array.addBlob((Blob) o);
            else if (o instanceof MutableDictionary)
                array.addDictionary((MutableDictionary) o);
            else if (o instanceof MutableArray)
                array.addArray((MutableArray) o);
            else
                array.addValue(o);
        }
    }

    interface Validator<T> {
        void validate(T array);
    }

    private Document save(MutableDocument mDoc, String key, MutableArray mArray, Validator<Array> validator)
            throws CouchbaseLiteException {
        validator.validate(mArray);
        mDoc.setValue(key, mArray);
        Document doc = save(mDoc);
        Array array = doc.getArray(key);
        validator.validate(array);
        return doc;
    }

    @Test
    public void testCreate() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        assertEquals(0, array.count());
        assertEquals(new ArrayList<>(), array.toList());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        assertEquals(array, doc.getArray("array"));

        Document updatedDoc = save(doc);
        assertEquals(new ArrayList<>(), updatedDoc.getArray("array").toList());
    }

    @Test
    public void testCreateWithList() throws CouchbaseLiteException {
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        MutableArray array = new MutableArray(data);
        assertEquals(3, array.count());
        assertEquals(data, array.toList());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        assertEquals(array, doc.getArray("array"));

        Document savedDoc = save(doc);
        assertEquals(data, savedDoc.getArray("array").toList());
    }

    @Test
    public void testSetList() throws CouchbaseLiteException {
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        MutableArray array = new MutableArray();
        array.setData(data);
        assertEquals(3, array.count());
        assertEquals(data, array.toList());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        assertEquals(array, doc.getArray("array"));

        // save
        Document savedDoc = save(doc);
        assertEquals(data, savedDoc.getArray("array").toList());

        // update
        array = savedDoc.getArray("array").toMutable();
        data = new ArrayList<>();
        data.add("4");
        data.add("5");
        data.add("6");
        array.setData(data);

        assertEquals(data.size(), array.count());
        assertEquals(data, array.toList());
    }

    @Test
    public void testAddNull() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        array.addValue(null);
        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(1, a.count());
                assertEquals(null, a.getValue(0));
            }
        });
    }

    @Test
    public void testAddObjects() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();

            // Add objects of all types:
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);

            MutableDocument doc = new MutableDocument("doc1");
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(12, a.count());

                    assertEquals(true, a.getValue(0));
                    assertEquals(false, a.getValue(1));
                    assertEquals("string", a.getValue(2));
                    assertEquals(0, ((Number) a.getValue(3)).intValue());
                    assertEquals(1, ((Number) a.getValue(4)).intValue());
                    assertEquals(-1, ((Number) a.getValue(5)).intValue());
                    assertEquals(1.1, a.getValue(6));
                    assertEquals(kArrayTestDate, a.getValue(7));
                    assertEquals(null, a.getValue(8));

                    // dictionary
                    Dictionary dict = (Dictionary) a.getValue(9);
                    MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                            (MutableDictionary) dict : dict.toMutable();

                    Map<String, Object> expectedMap = new HashMap<>();
                    expectedMap.put("name", "Scott Tiger");
                    assertEquals(expectedMap, subdict.toMap());

                    // array
                    Array array = (Array) a.getValue(10);
                    MutableArray subarray = array instanceof MutableArray ?
                            (MutableArray) array : array.toMutable();

                    List<Object> expected = new ArrayList<>();
                    expected.add("a");
                    expected.add("b");
                    expected.add("c");
                    assertEquals(expected, subarray.toList());

                    // blob
                    Blob blob = (Blob) a.getValue(11);
                    assertTrue(Arrays.equals(kArrayTestBlob.getBytes(), blob.getContent()));
                    assertEquals(kArrayTestBlob, new String(blob.getContent()));
                }
            });
        }
    }

    @Test
    public void testAddObjectsToExistingArray() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);

            // Save
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            doc.setValue("array", array);
            doc = save(doc).toMutable();

            // Get an existing array:
            array = doc.getArray("array");
            assertNotNull(array);
            assertEquals(12, array.count());

            // Update:
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(24, array.count());

            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(24, a.count());

                    assertEquals(true, a.getValue(12 + 0));
                    assertEquals(false, a.getValue(12 + 1));
                    assertEquals("string", a.getValue(12 + 2));
                    assertEquals(0, ((Number) a.getValue(12 + 3)).intValue());
                    assertEquals(1, ((Number) a.getValue(12 + 4)).intValue());
                    assertEquals(-1, ((Number) a.getValue(12 + 5)).intValue());
                    assertEquals(1.1, a.getValue(12 + 6));
                    assertEquals(kArrayTestDate, a.getValue(12 + 7));
                    assertEquals(null, a.getValue(12 + 8));

                    // dictionary
                    Dictionary dict = (Dictionary) a.getValue(12 + 9);
                    MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                            (MutableDictionary) dict : dict.toMutable();
                    Map<String, Object> expectedMap = new HashMap<>();
                    expectedMap.put("name", "Scott Tiger");
                    assertEquals(expectedMap, subdict.toMap());

                    // array
                    Array array = (Array) a.getValue(12 + 10);
                    MutableArray subarray = array instanceof MutableArray ?
                            (MutableArray) array : array.toMutable();
                    List<Object> expected = new ArrayList<>();
                    expected.add("a");
                    expected.add("b");
                    expected.add("c");
                    assertEquals(expected, subarray.toList());

                    // blob
                    Blob blob = (Blob) a.getValue(12 + 11);
                    assertTrue(Arrays.equals(kArrayTestBlob.getBytes(), blob.getContent()));
                    assertEquals(kArrayTestBlob, new String(blob.getContent()));
                }
            });
        }
    }

    @Test
    public void testSetObject() throws CouchbaseLiteException {
        List<Object> data = arrayOfAllTypes();

        // Prepare CBLArray with NSNull placeholders:
        MutableArray array = new MutableArray();
        for (int i = 0; i < data.size(); i++)
            array.addValue(null);

        // Set object at index:
        for (int i = 0; i < data.size(); i++)
            array.setValue(i, data.get(i));

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(12, a.count());

                assertEquals(true, a.getValue(0));
                assertEquals(false, a.getValue(1));
                assertEquals("string", a.getValue(2));
                assertEquals(0, ((Number) a.getValue(3)).intValue());
                assertEquals(1, ((Number) a.getValue(4)).intValue());
                assertEquals(-1, ((Number) a.getValue(5)).intValue());
                assertEquals(1.1, a.getValue(6));
                assertEquals(kArrayTestDate, a.getValue(7));
                assertEquals(null, a.getValue(8));

                // dictionary
                Dictionary dict = (Dictionary) a.getValue(9);
                MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                        (MutableDictionary) dict : dict.toMutable();
                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                assertEquals(expectedMap, subdict.toMap());

                // array
                Array array = (Array) a.getValue(10);
                MutableArray subarray = array instanceof MutableArray ?
                        (MutableArray) array : array.toMutable();
                List<Object> expected = new ArrayList<>();
                expected.add("a");
                expected.add("b");
                expected.add("c");
                assertEquals(expected, subarray.toList());

                // blob
                Blob blob = (Blob) a.getValue(11);
                assertTrue(Arrays.equals(kArrayTestBlob.getBytes(), blob.getContent()));
                assertEquals(kArrayTestBlob, new String(blob.getContent()));
            }
        });
    }

    @Test
    public void testSetObjectToExistingArray() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);

            // Save
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            doc.setArray("array", array);
            doc = save(doc).toMutable();
            MutableArray gotArray = doc.getArray("array");

            List<Object> data = arrayOfAllTypes();
            assertEquals(data.size(), gotArray.count());

            // reverse the array
            for (int j = 0; j < data.size(); j++)
                gotArray.setValue(j, data.get(data.size() - j - 1));

            save(doc, "array", gotArray, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(12, a.count());

                    assertEquals(true, a.getValue(11));
                    assertEquals(false, a.getValue(10));
                    assertEquals("string", a.getValue(9));
                    assertEquals(0, ((Number) a.getValue(8)).intValue());
                    assertEquals(1, ((Number) a.getValue(7)).intValue());
                    assertEquals(-1, ((Number) a.getValue(6)).intValue());
                    assertEquals(1.1, a.getValue(5));
                    assertEquals(kArrayTestDate, a.getValue(4));
                    assertEquals(null, a.getValue(3));

                    // dictionary
                    Dictionary dict = (Dictionary) a.getValue(2);
                    MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                            (MutableDictionary) dict : dict.toMutable();

                    Map<String, Object> expectedMap = new HashMap<>();
                    expectedMap.put("name", "Scott Tiger");
                    assertEquals(expectedMap, subdict.toMap());

                    // array
                    Array array = (Array) a.getValue(1);
                    MutableArray subarray = (array instanceof MutableArray) ?
                            (MutableArray) array : array.toMutable();

                    List<Object> expected = new ArrayList<>();
                    expected.add("a");
                    expected.add("b");
                    expected.add("c");
                    assertEquals(expected, subarray.toList());

                    // blob
                    Blob blob = (Blob) a.getValue(0);
                    assertTrue(Arrays.equals(kArrayTestBlob.getBytes(), blob.getContent()));
                    assertEquals(kArrayTestBlob, new String(blob.getContent()));
                }
            });
        }
    }

    @Test
    public void testSetObjectOutOfBound() {
        MutableArray array = new MutableArray();
        array.addValue("a");

        thrown.expect(IndexOutOfBoundsException.class);
        array.setValue(-1, "b");

        thrown.expect(IndexOutOfBoundsException.class);
        array.setValue(1, "b");
    }

    @Test
    public void testInsertObject() {
        MutableArray array = new MutableArray();

        array.insertValue(0, "a");
        assertEquals(1, array.count());
        assertEquals("a", array.getValue(0));

        array.insertValue(0, "c");
        assertEquals(2, array.count());
        assertEquals("c", array.getValue(0));
        assertEquals("a", array.getValue(1));

        array.insertValue(1, "d");
        assertEquals(3, array.count());
        assertEquals("c", array.getValue(0));
        assertEquals("d", array.getValue(1));
        assertEquals("a", array.getValue(2));

        array.insertValue(2, "e");
        assertEquals(4, array.count());
        assertEquals("c", array.getValue(0));
        assertEquals("d", array.getValue(1));
        assertEquals("e", array.getValue(2));
        assertEquals("a", array.getValue(3));

        array.insertValue(4, "f");
        assertEquals(5, array.count());
        assertEquals("c", array.getValue(0));
        assertEquals("d", array.getValue(1));
        assertEquals("e", array.getValue(2));
        assertEquals("a", array.getValue(3));
        assertEquals("f", array.getValue(4));
    }

    @Test
    public void testInsertObjectToExistingArray() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("array", new MutableArray());
        Document doc = save(mDoc);
        mDoc = doc.toMutable();

        MutableArray mArray = mDoc.getArray("array");
        assertNotNull(mArray);
        mArray.insertValue(0, "a");
        doc = save(mDoc, "array", mArray, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(1, a.count());
                assertEquals("a", a.getValue(0));
            }
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        assertNotNull(mArray);
        mArray.insertValue(0, "c");
        doc = save(mDoc, "array", mArray, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(2, a.count());
                assertEquals("c", a.getValue(0));
                assertEquals("a", a.getValue(1));
            }
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        assertNotNull(mArray);
        mArray.insertValue(1, "d");
        doc = save(mDoc, "array", mArray, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(3, a.count());
                assertEquals("c", a.getValue(0));
                assertEquals("d", a.getValue(1));
                assertEquals("a", a.getValue(2));
            }
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        assertNotNull(mArray);
        mArray.insertValue(2, "e");
        doc = save(mDoc, "array", mArray, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(4, a.count());
                assertEquals("c", a.getValue(0));
                assertEquals("d", a.getValue(1));
                assertEquals("e", a.getValue(2));
                assertEquals("a", a.getValue(3));
            }
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        assertNotNull(mArray);
        mArray.insertValue(4, "f");
        doc = save(mDoc, "array", mArray, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(5, a.count());
                assertEquals("c", a.getValue(0));
                assertEquals("d", a.getValue(1));
                assertEquals("e", a.getValue(2));
                assertEquals("a", a.getValue(3));
                assertEquals("f", a.getValue(4));
            }
        });
    }

    @Test
    public void testInsertObjectOutOfBound() {
        MutableArray array = new MutableArray();
        array.addValue("a");

        thrown.expect(IndexOutOfBoundsException.class);
        array.insertValue(-1, "b");

        thrown.expect(IndexOutOfBoundsException.class);
        array.insertValue(2, "b");
    }

    @Test
    public void testRemove() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);

            for (int j = array.count() - 1; j >= 0; j--) {
                array.remove(j);
            }

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(0, a.count());
                    assertEquals(new ArrayList<Object>(), a.toList());
                }
            });
        }
    }

    @Test
    public void testRemoveExistingArray() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            doc.setValue("array", array);
            doc = save(doc).toMutable();
            array = doc.getArray("array");

            for (int j = array.count() - 1; j >= 0; j--) {
                array.remove(j);
            }

            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(0, a.count());
                    assertEquals(new ArrayList<Object>(), a.toList());
                }
            });
        }
    }

    @Test
    public void testRemoveOutOfBound() {
        MutableArray array = new MutableArray();
        array.addValue("a");

        thrown.expect(IndexOutOfBoundsException.class);
        array.remove(-1);

        thrown.expect(IndexOutOfBoundsException.class);
        array.remove(1);
    }

    @Test
    public void testCount() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(12, a.count());
                }
            });
        }
    }

    @Test
    public void testGetString() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertNull(a.getString(0));
                    assertNull(a.getString(1));
                    assertEquals("string", a.getString(2));
                    assertNull(a.getString(3));
                    assertNull(a.getString(4));
                    assertNull(a.getString(5));
                    assertNull(a.getString(6));
                    assertEquals(kArrayTestDate, a.getString(7));
                    assertNull(a.getString(8));
                    assertNull(a.getString(9));
                    assertNull(a.getString(10));
                    assertNull(a.getString(11));
                }
            });
        }
    }

    @Test
    public void testGetNumber() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(1, a.getNumber(0).intValue());
                    assertEquals(0, a.getNumber(1).intValue());
                    assertNull(a.getNumber(2));
                    assertEquals(0, a.getNumber(3).intValue());
                    assertEquals(1, a.getNumber(4).intValue());
                    assertEquals(-1, a.getNumber(5).intValue());
                    assertEquals(1.1, a.getNumber(6));
                    assertNull(a.getNumber(7));
                    assertNull(a.getNumber(8));
                    assertNull(a.getNumber(9));
                    assertNull(a.getNumber(10));
                    assertNull(a.getNumber(11));
                }
            });
        }
    }

    @Test
    public void testGetInteger() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(1, a.getInt(0));
                    assertEquals(0, a.getInt(1));
                    assertEquals(0, a.getInt(2));
                    assertEquals(0, a.getInt(3));
                    assertEquals(1, a.getInt(4));
                    assertEquals(-1, a.getInt(5));
                    assertEquals(1, a.getInt(6));
                    assertEquals(0, a.getInt(7));
                    assertEquals(0, a.getInt(8));
                    assertEquals(0, a.getInt(9));
                    assertEquals(0, a.getInt(10));
                    assertEquals(0, a.getInt(11));
                }
            });
            break;
        }
    }

    @Test
    public void testGetLong() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(1, a.getLong(0));
                    assertEquals(0, a.getLong(1));
                    assertEquals(0, a.getLong(2));
                    assertEquals(0, a.getLong(3));
                    assertEquals(1, a.getLong(4));
                    assertEquals(-1, a.getLong(5));
                    assertEquals(1, a.getLong(6));
                    assertEquals(0, a.getLong(7));
                    assertEquals(0, a.getLong(8));
                    assertEquals(0, a.getLong(9));
                    assertEquals(0, a.getLong(10));
                    assertEquals(0, a.getLong(11));
                }
            });
        }
    }

    @Test
    public void testGetFloat() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(1.0f, a.getFloat(0), 0.0f);
                    assertEquals(0.0f, a.getFloat(1), 0.0f);
                    assertEquals(0.0f, a.getFloat(2), 0.0f);
                    assertEquals(0.0f, a.getFloat(3), 0.0f);
                    assertEquals(1.0f, a.getFloat(4), 0.0f);
                    assertEquals(-1.0f, a.getFloat(5), 0.0f);
                    assertEquals(1.1f, a.getFloat(6), 0.0f);
                    assertEquals(0.0f, a.getFloat(7), 0.0f);
                    assertEquals(0.0f, a.getFloat(8), 0.0f);
                    assertEquals(0.0f, a.getFloat(9), 0.0f);
                    assertEquals(0.0f, a.getFloat(10), 0.0f);
                    assertEquals(0.0f, a.getFloat(11), 0.0f);
                }
            });
        }
    }

    @Test
    public void testGetDouble() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(1.0, a.getDouble(0), 0.0);
                    assertEquals(0.0, a.getDouble(1), 0.0);
                    assertEquals(0.0, a.getDouble(2), 0.0);
                    assertEquals(0.0, a.getDouble(3), 0.0);
                    assertEquals(1.0, a.getDouble(4), 0.0);
                    assertEquals(-1.0, a.getDouble(5), 0.0);
                    assertEquals(1.1, a.getDouble(6), 0.0);
                    assertEquals(0.0, a.getDouble(7), 0.0);
                    assertEquals(0.0, a.getDouble(8), 0.0);
                    assertEquals(0.0, a.getDouble(9), 0.0);
                    assertEquals(0.0, a.getDouble(10), 0.0);
                    assertEquals(0.0, a.getDouble(11), 0.0);
                }
            });
        }
    }

    @Test
    public void testSetGetMinMaxNumbers() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        array.addValue(Integer.MIN_VALUE);
        array.addValue(Integer.MAX_VALUE);
        array.addValue(Long.MIN_VALUE);
        array.addValue(Long.MAX_VALUE);
        array.addValue(Float.MIN_VALUE);
        array.addValue(Float.MAX_VALUE);
        array.addValue(Double.MIN_VALUE);
        array.addValue(Double.MAX_VALUE);

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(Integer.MIN_VALUE, a.getNumber(0).intValue());
                assertEquals(Integer.MAX_VALUE, a.getNumber(1).intValue());
                assertEquals(Integer.MIN_VALUE, ((Number) a.getValue(0)).intValue());
                assertEquals(Integer.MAX_VALUE, ((Number) a.getValue(1)).intValue());
                assertEquals(Integer.MIN_VALUE, a.getInt(0));
                assertEquals(Integer.MAX_VALUE, a.getInt(1));

                assertEquals(Long.MIN_VALUE, a.getNumber(2));
                assertEquals(Long.MAX_VALUE, a.getNumber(3));
                assertEquals(Long.MIN_VALUE, a.getValue(2));
                assertEquals(Long.MAX_VALUE, a.getValue(3));
                assertEquals(Long.MIN_VALUE, a.getLong(2));
                assertEquals(Long.MAX_VALUE, a.getLong(3));

                assertEquals(Float.MIN_VALUE, a.getNumber(4));
                assertEquals(Float.MAX_VALUE, a.getNumber(5));
                assertEquals(Float.MIN_VALUE, a.getValue(4));
                assertEquals(Float.MAX_VALUE, a.getValue(5));
                assertEquals(Float.MIN_VALUE, a.getFloat(4), 0.0f);
                assertEquals(Float.MAX_VALUE, a.getFloat(5), 0.0f);

                assertEquals(Double.MIN_VALUE, a.getNumber(6));
                assertEquals(Double.MAX_VALUE, a.getNumber(7));
                assertEquals(Double.MIN_VALUE, a.getValue(6));
                assertEquals(Double.MAX_VALUE, a.getValue(7));
                assertEquals(Double.MIN_VALUE, a.getDouble(6), 0.0f);
                assertEquals(Double.MAX_VALUE, a.getDouble(7), 0.0f);
            }
        });
    }

    @Test
    public void testSetGetFloatNumbers() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        array.addValue(1.00);
        array.addValue(1.49);
        array.addValue(1.50);
        array.addValue(1.51);
        array.addValue(1.99);

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                // NOTE: Number which has no floating part is stored as Integer.
                //       This causes type difference between before and after storing data
                //       into the database.
                assertEquals(1.00, ((Number) a.getValue(0)).doubleValue(), 0.0);
                assertEquals(1.00, a.getNumber(0).doubleValue(), 0.0);
                assertEquals(1, a.getInt(0));
                assertEquals(1L, a.getLong(0));
                assertEquals(1.00F, a.getFloat(0), 0.0F);
                assertEquals(1.00, a.getDouble(0), 0.0);

                assertEquals(1.49, a.getValue(1));
                assertEquals(1.49, a.getNumber(1));
                assertEquals(1, a.getInt(1));
                assertEquals(1L, a.getLong(1));
                assertEquals(1.49F, a.getFloat(1), 0.0F);
                assertEquals(1.49, a.getDouble(1), 0.0);

                assertEquals(1.50, ((Number) a.getValue(2)).doubleValue(), 0.0);
                assertEquals(1.50, a.getNumber(2).doubleValue(), 0.0);
                assertEquals(1, a.getInt(2));
                assertEquals(1L, a.getLong(2));
                assertEquals(1.50F, a.getFloat(2), 0.0F);
                assertEquals(1.50, a.getDouble(2), 0.0);

                assertEquals(1.51, a.getValue(3));
                assertEquals(1.51, a.getNumber(3));
                assertEquals(1, a.getInt(3));
                assertEquals(1L, a.getLong(3));
                assertEquals(1.51F, a.getFloat(3), 0.0F);
                assertEquals(1.51, a.getDouble(3), 0.0);

                assertEquals(1.99, a.getValue(4));
                assertEquals(1.99, a.getNumber(4));
                assertEquals(1, a.getInt(4));
                assertEquals(1L, a.getLong(4));
                assertEquals(1.99F, a.getFloat(4), 0.0F);
                assertEquals(1.99, a.getDouble(4), 0.0);
            }
        });
    }

    @Test
    public void testGetBoolean() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertEquals(true, a.getBoolean(0));
                    assertEquals(false, a.getBoolean(1));
                    assertEquals(true, a.getBoolean(2));
                    assertEquals(false, a.getBoolean(3));
                    assertEquals(true, a.getBoolean(4));
                    assertEquals(true, a.getBoolean(5));
                    assertEquals(true, a.getBoolean(6));
                    assertEquals(true, a.getBoolean(7));
                    assertEquals(false, a.getBoolean(8));
                    assertEquals(true, a.getBoolean(9));
                    assertEquals(true, a.getBoolean(10));
                    assertEquals(true, a.getBoolean(11));
                }
            });
        }
    }

    @Test
    public void testGetDate() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertNull(a.getDate(0));
                    assertNull(a.getDate(1));
                    assertNull(a.getDate(2));
                    assertNull(a.getDate(3));
                    assertNull(a.getDate(4));
                    assertNull(a.getDate(5));
                    assertNull(a.getDate(6));
                    assertEquals(kArrayTestDate, DateUtils.toJson(a.getDate(7)));
                    assertNull(a.getDate(8));
                    assertNull(a.getDate(9));
                    assertNull(a.getDate(10));
                    assertNull(a.getDate(11));
                }
            });
        }
    }

    @Test
    public void testGetMap() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertNull(a.getDictionary(0));
                    assertNull(a.getDictionary(1));
                    assertNull(a.getDictionary(2));
                    assertNull(a.getDictionary(3));
                    assertNull(a.getDictionary(4));
                    assertNull(a.getDictionary(5));
                    assertNull(a.getDictionary(6));
                    assertNull(a.getDictionary(7));
                    assertNull(a.getDictionary(8));
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", "Scott Tiger");
                    assertEquals(map, a.getDictionary(9).toMap());
                    assertNull(a.getDictionary(10));
                    assertNull(a.getDictionary(11));
                }
            });
        }
    }

    @Test
    public void testGetArray() throws CouchbaseLiteException {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0)
                populateData(array);
            else
                populateDataByType(array);
            assertEquals(12, array.count());

            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            MutableDocument doc = new MutableDocument(docID);
            save(doc, "array", array, new Validator<Array>() {
                @Override
                public void validate(Array a) {
                    assertNull(a.getArray(0));
                    assertNull(a.getArray(1));
                    assertNull(a.getArray(2));
                    assertNull(a.getArray(3));
                    assertNull(a.getArray(4));
                    assertNull(a.getArray(5));
                    assertNull(a.getArray(6));
                    assertNull(a.getArray(7));
                    assertNull(a.getArray(9));
                    assertEquals(Arrays.asList("a", "b", "c"), a.getArray(10).toList());
                    assertNull(a.getDictionary(11));
                }
            });
        }
    }

    @Test
    public void testSetNestedArray() throws CouchbaseLiteException {
        MutableArray array1 = new MutableArray();
        MutableArray array2 = new MutableArray();
        MutableArray array3 = new MutableArray();

        array1.addValue(array2);
        array2.addValue(array3);
        array3.addValue("a");
        array3.addValue("b");
        array3.addValue("c");

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array1, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                Array a1 = a;
                assertEquals(1, a1.count());
                Array a2 = a1.getArray(0);
                assertEquals(1, a2.count());
                Array a3 = a2.getArray(0);
                assertEquals(3, a3.count());
                assertEquals("a", a3.getValue(0));
                assertEquals("b", a3.getValue(1));
                assertEquals("c", a3.getValue(2));
            }
        });
    }

    @Test
    public void testReplaceArray() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray array1 = new MutableArray();
        array1.addValue("a");
        array1.addValue("b");
        array1.addValue("c");
        assertEquals(3, array1.count());
        assertEquals(Arrays.asList("a", "b", "c"), array1.toList());
        doc.setValue("array", array1);

        MutableArray array2 = new MutableArray();
        array2.addValue("x");
        array2.addValue("y");
        array2.addValue("z");
        assertEquals(3, array2.count());
        assertEquals(Arrays.asList("x", "y", "z"), array2.toList());

        // Replace:
        doc.setValue("array", array2);

        // array1 shoud be now detached:
        array1.addValue("d");
        assertEquals(4, array1.count());
        assertEquals(Arrays.asList("a", "b", "c", "d"), array1.toList());

        // Check array2:
        assertEquals(3, array2.count());
        assertEquals(Arrays.asList("x", "y", "z"), array2.toList());

        // Save:
        doc = save(doc).toMutable();

        // Check current array:
        assertTrue(doc.getArray("array") != array2);
        array2 = doc.getArray("array");
        assertEquals(3, array2.count());
        assertEquals(Arrays.asList("x", "y", "z"), array2.toList());
    }

    @Test
    public void testReplaceArrayDifferentType() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray array1 = new MutableArray();
        array1.addValue("a");
        array1.addValue("b");
        array1.addValue("c");
        assertEquals(3, array1.count());
        assertEquals(Arrays.asList("a", "b", "c"), array1.toList());
        doc.setValue("array", array1);

        // Replace:
        doc.setValue("array", "Daniel Tiger");

        // array1 shoud be now detached:
        array1.addValue("d");
        assertEquals(4, array1.count());
        assertEquals(Arrays.asList("a", "b", "c", "d"), array1.toList());

        // Save:
        doc = save(doc).toMutable();
        assertEquals("Daniel Tiger", doc.getString("array"));
    }

    @Test
    public void testEnumeratingArray() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        for (int i = 0; i < 20; i++) {
            array.addValue(i);
        }
        List<Object> content = array.toList();

        List<Object> result = new ArrayList<>();
        int counter = 0;
        for (Object item : array) {
            assertNotNull(item);
            result.add(item);
            counter++;
        }
        assertEquals(content, result);
        assertEquals(array.count(), counter);

        // Update:
        array.remove(1);
        array.addValue(20);
        array.addValue(21);
        content = array.toList();

        result = new ArrayList<>();
        for (Object item : array) {
            assertNotNull(item);
            result.add(item);
        }
        assertEquals(content, result);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);

        final List<Object> c = content;
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array array) {
                List<Object> r = new ArrayList<Object>();
                for (Object item : array) {
                    assertNotNull(item);
                    r.add(item);
                }
                assertEquals(c.toString(), r.toString());
            }
        });
    }

    // TODO: MArray has isMuated() method, but unable to check mutated to mutated.
    // @Test
    public void testArrayEnumerationWithDataModification() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        for (int i = 0; i < 2; i++)
            array.addValue(i);

        Iterator<Object> itr = array.iterator();
        int count = 0;
        try {
            while (itr.hasNext()) {
                itr.next();
                if (count++ == 0)
                    array.addValue(2);
            }
            fail("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException e) {

        }
        assertEquals(3, array.count());
        assertEquals(Arrays.asList(0, 1, 2).toString(), array.toList().toString());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        doc = save(doc).toMutable();
        array = doc.getArray("array");

        itr = array.iterator();
        count = 0;
        try {
            while (itr.hasNext()) {
                itr.next();
                if (count++ == 0)
                    array.addValue(3);
            }
            fail("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException e) {

        }
        assertEquals(4, array.count());
        assertEquals(Arrays.asList(0, 1, 2, 3).toString(), array.toList().toString());
    }

    @Test
    public void testSetNull() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addValue(null);
        mArray.addString(null);
        mArray.addNumber(null);
        mArray.addDate(null);
        mArray.addArray(null);
        mArray.addDictionary(null);
        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(6, array.count());
                assertNull(array.getValue(0));
                assertNull(array.getValue(1));
                assertNull(array.getValue(2));
                assertNull(array.getValue(3));
                assertNull(array.getValue(4));
                assertNull(array.getValue(5));
            }
        });
    }

    @Test
    public void testEquals() throws CouchbaseLiteException {

        // mArray1 and mArray2 have exactly same data
        // mArray3 is different
        // mArray4 is different
        // mArray5 is different

        MutableArray mArray1 = new MutableArray();
        mArray1.addValue(1L);
        mArray1.addValue("Hello");
        mArray1.addValue(null);

        MutableArray mArray2 = new MutableArray();
        mArray2.addValue(1L);
        mArray2.addValue("Hello");
        mArray2.addValue(null);

        MutableArray mArray3 = new MutableArray();
        mArray3.addValue(100L);
        mArray3.addValue(true);

        MutableArray mArray4 = new MutableArray();
        mArray4.addValue(100L);

        MutableArray mArray5 = new MutableArray();
        mArray4.addValue(100L);
        mArray3.addValue(false);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setArray("array1", mArray1);
        mDoc.setArray("array2", mArray2);
        mDoc.setArray("array3", mArray3);
        mDoc.setArray("array4", mArray4);
        mDoc.setArray("array5", mArray5);

        Document doc = save(mDoc);
        Array array1 = doc.getArray("array1");
        Array array2 = doc.getArray("array2");
        Array array3 = doc.getArray("array3");
        Array array4 = doc.getArray("array4");
        Array array5 = doc.getArray("array5");

        // compare array1, array2, marray1, and marray2
        assertTrue(array1.equals(array1));
        assertTrue(array2.equals(array2));
        assertTrue(array1.equals(array2));
        assertTrue(array2.equals(array1));
        assertTrue(array1.equals(array1.toMutable()));
        assertTrue(array1.equals(array2.toMutable()));
        assertTrue(array1.toMutable().equals(array1));
        assertTrue(array2.toMutable().equals(array1));
        assertTrue(array1.equals(mArray1));
        assertTrue(array1.equals(mArray2));
        assertTrue(array2.equals(mArray1));
        assertTrue(array2.equals(mArray2));
        assertTrue(mArray1.equals(array1));
        assertTrue(mArray2.equals(array1));
        assertTrue(mArray1.equals(array2));
        assertTrue(mArray2.equals(array2));
        assertTrue(mArray1.equals(mArray1));
        assertTrue(mArray2.equals(mArray2));
        assertTrue(mArray1.equals(mArray1));
        assertTrue(mArray2.equals(mArray2));

        // compare array1, array3, marray1, and marray3
        assertTrue(array3.equals(array3));
        assertFalse(array1.equals(array3));
        assertFalse(array3.equals(array1));
        assertFalse(array1.equals(array3.toMutable()));
        assertFalse(array3.toMutable().equals(array1));
        assertFalse(array1.equals(mArray3));
        assertFalse(array3.equals(mArray1));
        assertTrue(array3.equals(mArray3));
        assertFalse(mArray3.equals(array1));
        assertFalse(mArray1.equals(array3));
        assertTrue(mArray3.equals(array3));
        assertTrue(mArray3.equals(mArray3));
        assertTrue(mArray3.equals(mArray3));

        // compare array1, array4, marray1, and marray4
        assertTrue(array4.equals(array4));
        assertFalse(array1.equals(array4));
        assertFalse(array4.equals(array1));
        assertFalse(array1.equals(array4.toMutable()));
        assertFalse(array4.toMutable().equals(array1));
        assertFalse(array1.equals(mArray4));
        assertFalse(array4.equals(mArray1));
        assertTrue(array4.equals(mArray4));
        assertFalse(mArray4.equals(array1));
        assertFalse(mArray1.equals(array4));
        assertTrue(mArray4.equals(array4));
        assertTrue(mArray4.equals(mArray4));
        assertTrue(mArray4.equals(mArray4));

        // compare array3, array4, marray3, and marray4
        assertFalse(array3.equals(array4));
        assertFalse(array4.equals(array3));
        assertFalse(array3.equals(array4.toMutable()));
        assertFalse(array4.toMutable().equals(array3));
        assertFalse(array3.equals(mArray4));
        assertFalse(array4.equals(mArray3));
        assertFalse(mArray4.equals(array3));
        assertFalse(mArray3.equals(array4));

        // compare array3, array5, marray3, and marray5
        assertFalse(array3.equals(array5));
        assertFalse(array5.equals(array3));
        assertFalse(array3.equals(array5.toMutable()));
        assertFalse(array5.toMutable().equals(array3));
        assertFalse(array3.equals(mArray5));
        assertFalse(array5.equals(mArray3));
        assertFalse(mArray5.equals(array3));
        assertFalse(mArray3.equals(array5));

        // compare array5, array4, mArray5, and marray4
        assertFalse(array5.equals(array4));
        assertFalse(array4.equals(array5));
        assertFalse(array5.equals(array4.toMutable()));
        assertFalse(array4.toMutable().equals(array5));
        assertFalse(array5.equals(mArray4));
        assertFalse(array4.equals(mArray5));
        assertFalse(mArray4.equals(array5));
        assertFalse(mArray5.equals(array4));

        // against other type
        assertFalse(array3.equals(null));
        assertFalse(array3.equals(new Object()));
        assertFalse(array3.equals(1));
        assertFalse(array3.equals(new HashMap<>()));
        assertFalse(array3.equals(new MutableDictionary()));
        assertFalse(array3.equals(new MutableArray()));
        assertFalse(array3.equals(doc));
        assertFalse(array3.equals(mDoc));
    }

    @Test
    public void testHashCode() throws CouchbaseLiteException {

        // mArray1 and mArray2 have exactly same data
        // mArray3 is different
        // mArray4 is different
        // mArray5 is different

        MutableArray mArray1 = new MutableArray();
        mArray1.addValue(1L);
        mArray1.addValue("Hello");
        mArray1.addValue(null);

        MutableArray mArray2 = new MutableArray();
        mArray2.addValue(1L);
        mArray2.addValue("Hello");
        mArray2.addValue(null);

        MutableArray mArray3 = new MutableArray();
        mArray3.addValue(100L);
        mArray3.addValue(true);

        MutableArray mArray4 = new MutableArray();
        mArray4.addValue(100L);

        MutableArray mArray5 = new MutableArray();
        mArray4.addValue(100L);
        mArray3.addValue(false);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setArray("array1", mArray1);
        mDoc.setArray("array2", mArray2);
        mDoc.setArray("array3", mArray3);
        mDoc.setArray("array4", mArray4);
        mDoc.setArray("array5", mArray5);

        Document doc = save(mDoc);
        Array array1 = doc.getArray("array1");
        Array array2 = doc.getArray("array2");
        Array array3 = doc.getArray("array3");
        Array array4 = doc.getArray("array4");
        Array array5 = doc.getArray("array5");

        assertEquals(array1.hashCode(), array1.hashCode());
        assertEquals(array1.hashCode(), array2.hashCode());
        assertEquals(array2.hashCode(), array1.hashCode());
        assertEquals(array1.hashCode(), array1.toMutable().hashCode());
        assertEquals(array1.hashCode(), array2.toMutable().hashCode());
        assertEquals(array1.hashCode(), mArray1.hashCode());
        assertEquals(array1.hashCode(), mArray2.hashCode());
        assertEquals(array2.hashCode(), mArray1.hashCode());
        assertEquals(array2.hashCode(), mArray2.hashCode());

        assertFalse(array3.hashCode() == array1.hashCode());
        assertFalse(array3.hashCode() == array2.hashCode());
        assertFalse(array3.hashCode() == array1.toMutable().hashCode());
        assertFalse(array3.hashCode() == array2.toMutable().hashCode());
        assertFalse(array3.hashCode() == mArray1.hashCode());
        assertFalse(array3.hashCode() == mArray2.hashCode());
        assertFalse(mArray3.hashCode() == array1.hashCode());
        assertFalse(mArray3.hashCode() == array2.hashCode());
        assertFalse(mArray3.hashCode() == array1.toMutable().hashCode());
        assertFalse(mArray3.hashCode() == array2.toMutable().hashCode());
        assertFalse(mArray3.hashCode() == mArray1.hashCode());
        assertFalse(mArray3.hashCode() == mArray2.hashCode());

        assertFalse(array1.hashCode() == array4.hashCode());
        assertFalse(array1.hashCode() == array5.hashCode());
        assertFalse(array2.hashCode() == array4.hashCode());
        assertFalse(array2.hashCode() == array5.hashCode());
        assertFalse(array3.hashCode() == array4.hashCode());
        assertFalse(array3.hashCode() == array5.hashCode());

        assertFalse(array3.hashCode() == 0);
        assertFalse(array3.hashCode() == new Object().hashCode());
        assertFalse(array3.hashCode() == new Integer(1).hashCode());
        assertFalse(array3.hashCode() == new HashMap<>().hashCode());
        assertFalse(array3.hashCode() == new MutableDictionary().hashCode());
        assertFalse(array3.hashCode() == new MutableArray().hashCode());
        assertFalse(mArray3.hashCode() == doc.hashCode());
        assertFalse(mArray3.hashCode() == mDoc.hashCode());
        assertFalse(mArray3.hashCode() == array1.toMutable().hashCode());
        assertFalse(mArray3.hashCode() == array2.toMutable().hashCode());
        assertFalse(mArray3.hashCode() == mArray1.hashCode());
        assertFalse(mArray3.hashCode() == mArray2.hashCode());
    }


    @Test
    public void testGetDictionary() throws CouchbaseLiteException {
        MutableDictionary mNestedDict = new MutableDictionary();
        mNestedDict.setValue("key1", 1L);
        mNestedDict.setValue("key2", "Hello");
        mNestedDict.setValue("key3", null);

        MutableArray mArray = new MutableArray();
        mArray.addValue(1L);
        mArray.addValue("Hello");
        mArray.addValue(null);
        mArray.addValue(mNestedDict);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setArray("array", mArray);

        Document doc = save(mDoc);
        Array array = doc.getArray("array");

        assertNotNull(array);
        assertNull(array.getDictionary(0));
        assertNull(array.getDictionary(1));
        assertNull(array.getDictionary(2));
        assertNotNull(array.getDictionary(3));

        thrown.expect(IndexOutOfBoundsException.class);
        assertNull(array.getDictionary(4));

        Dictionary nestedDict = array.getDictionary(3);
        assertTrue(nestedDict.equals(mNestedDict));
        assertTrue(array.equals(mArray));
    }

    @Test
    public void testGetArray2() throws CouchbaseLiteException {
        MutableArray mNestedArray = new MutableArray();
        mNestedArray.addValue(1L);
        mNestedArray.addValue("Hello");
        mNestedArray.addValue(null);

        MutableArray mArray = new MutableArray();
        mArray.addValue(1L);
        mArray.addValue("Hello");
        mArray.addValue(null);
        mArray.addValue(mNestedArray);

        MutableDocument mDoc = new MutableDocument("test");
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

    @Test
    public void testAddInt() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addInt(0);
        mArray.addInt(Integer.MAX_VALUE);
        mArray.addInt(Integer.MIN_VALUE);
        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());
                assertEquals(0, array.getInt(0));
                assertEquals(Integer.MAX_VALUE, array.getInt(1));
                assertEquals(Integer.MIN_VALUE, array.getInt(2));
            }
        });
    }

    @Test
    public void testSetInt() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addInt(0);
        mArray.addInt(Integer.MAX_VALUE);
        mArray.addInt(Integer.MIN_VALUE);

        mArray.setInt(0, Integer.MAX_VALUE);
        mArray.setInt(1, Integer.MIN_VALUE);
        mArray.setInt(2, 0);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());
                assertEquals(0, array.getInt(2));
                assertEquals(Integer.MAX_VALUE, array.getInt(0));
                assertEquals(Integer.MIN_VALUE, array.getInt(1));
            }
        });
    }

    @Test
    public void testInsertInt() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addInt(10); // will be pushed 3 times.
        mArray.insertInt(0, 0);
        mArray.insertInt(1, Integer.MAX_VALUE);
        mArray.insertInt(2, Integer.MIN_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals(0, array.getInt(0));
                assertEquals(Integer.MAX_VALUE, array.getInt(1));
                assertEquals(Integer.MIN_VALUE, array.getInt(2));
                assertEquals(10, array.getInt(3));
            }
        });
    }

    @Test
    public void testAddLong() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addLong(0);
        mArray.addLong(Long.MAX_VALUE);
        mArray.addLong(Long.MIN_VALUE);
        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());
                assertEquals(0, array.getLong(0));
                assertEquals(Long.MAX_VALUE, array.getLong(1));
                assertEquals(Long.MIN_VALUE, array.getLong(2));
            }
        });
    }

    @Test
    public void testSetLong() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addLong(0);
        mArray.addLong(Long.MAX_VALUE);
        mArray.addLong(Long.MIN_VALUE);
        mArray.setLong(0, Long.MAX_VALUE);
        mArray.setLong(1, Long.MIN_VALUE);
        mArray.setLong(2, 0);
        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());
                assertEquals(0, array.getLong(2));
                assertEquals(Long.MAX_VALUE, array.getLong(0));
                assertEquals(Long.MIN_VALUE, array.getLong(1));
            }
        });
    }

    @Test
    public void testInsertLong() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addLong(10); // will be pushed 3 times.
        mArray.insertLong(0, 0);
        mArray.insertLong(1, Long.MAX_VALUE);
        mArray.insertLong(2, Long.MIN_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals(0, array.getLong(0));
                assertEquals(Long.MAX_VALUE, array.getLong(1));
                assertEquals(Long.MIN_VALUE, array.getLong(2));
                assertEquals(10, array.getLong(3));
            }
        });
    }

    @Test
    public void testAddFloat() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addFloat(0.0F);
        mArray.addFloat(Float.MAX_VALUE);
        mArray.addFloat(Float.MIN_VALUE);
        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());
                assertEquals(0.0F, array.getFloat(0), 0.0F);
                assertEquals(Float.MAX_VALUE, array.getFloat(1), 0.0F);
                assertEquals(Float.MIN_VALUE, array.getFloat(2), 0.0F);
            }
        });
    }

    @Test
    public void testSetFloat() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addFloat(0);
        mArray.addFloat(Float.MAX_VALUE);
        mArray.addFloat(Float.MIN_VALUE);

        mArray.setFloat(0, Float.MAX_VALUE);
        mArray.setFloat(1, Float.MIN_VALUE);
        mArray.setFloat(2, 0);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());

                assertEquals(0.0F, array.getLong(2), 0.0F);
                assertEquals(Float.MAX_VALUE, array.getFloat(0), 0.0F);
                assertEquals(Float.MIN_VALUE, array.getFloat(1), 0.0f);
            }
        });
    }

    @Test
    public void testInsertFloat() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addFloat(10F); // will be pushed 3 times.
        mArray.insertFloat(0, 0F);
        mArray.insertFloat(1, Float.MAX_VALUE);
        mArray.insertFloat(2, Float.MIN_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals(0F, array.getFloat(0), 0F);
                assertEquals(Float.MAX_VALUE, array.getFloat(1), 0F);
                assertEquals(Float.MIN_VALUE, array.getFloat(2), 0F);
                assertEquals(10F, array.getFloat(3), 0F);
            }
        });
    }

    @Test
    public void testAddDouble() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addDouble(0.0);
        mArray.addDouble(Double.MAX_VALUE);
        mArray.addDouble(Double.MIN_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());
                assertEquals(0.0, array.getDouble(0), 0.0);
                assertEquals(Double.MAX_VALUE, array.getDouble(1), 0.0);
                assertEquals(Double.MIN_VALUE, array.getDouble(2), 0.0);
            }
        });
    }

    @Test
    public void testSetDouble() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addDouble(0);
        mArray.addDouble(Double.MAX_VALUE);
        mArray.addDouble(Double.MIN_VALUE);

        mArray.setDouble(0, Double.MAX_VALUE);
        mArray.setDouble(1, Double.MIN_VALUE);
        mArray.setDouble(2, 0.0);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());

                assertEquals(0.0, array.getDouble(2), 0.0);
                assertEquals(Double.MAX_VALUE, array.getDouble(0), 0.0);
                assertEquals(Double.MIN_VALUE, array.getDouble(1), 0.0);
            }
        });
    }

    @Test
    public void testInsertDouble() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addDouble(10.0); // will be pushed 3 times.
        mArray.insertDouble(0, 0.0);
        mArray.insertDouble(1, Double.MAX_VALUE);
        mArray.insertDouble(2, Double.MIN_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals(0.0, array.getDouble(0), 0.0);
                assertEquals(Double.MAX_VALUE, array.getDouble(1), 0.0);
                assertEquals(Double.MIN_VALUE, array.getDouble(2), 0.0);
                assertEquals(10.0, array.getDouble(3), 0.0);
            }
        });
    }

    @Test
    public void testAddNumber() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addNumber(Integer.MAX_VALUE);
        mArray.addNumber(Long.MAX_VALUE);
        mArray.addNumber(Double.MAX_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());

                assertEquals(Integer.MAX_VALUE, array.getNumber(0).intValue());
                assertEquals(Long.MAX_VALUE, array.getNumber(1).longValue());
                assertEquals(Double.MAX_VALUE, array.getNumber(2).doubleValue(), 0.0);
            }
        });
    }

    @Test
    public void testSetNumber() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addNumber(Integer.MAX_VALUE);
        mArray.addNumber(Long.MAX_VALUE);
        mArray.addNumber(Double.MAX_VALUE);

        mArray.setNumber(0, Long.MAX_VALUE);
        mArray.setNumber(1, Double.MAX_VALUE);
        mArray.setNumber(2, Integer.MAX_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());

                assertEquals(Integer.MAX_VALUE, array.getNumber(2).intValue());
                assertEquals(Long.MAX_VALUE, array.getNumber(0).longValue());
                assertEquals(Double.MAX_VALUE, array.getNumber(1).doubleValue(), 0.0);
            }
        });
    }

    @Test
    public void testInsertNumber() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addNumber(10L); // will be pushed 3 times.
        mArray.insertNumber(0, Integer.MAX_VALUE);
        mArray.insertNumber(1, Long.MAX_VALUE);
        mArray.insertNumber(2, Double.MAX_VALUE);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals(Integer.MAX_VALUE, array.getInt(0));
                assertEquals(Long.MAX_VALUE, array.getLong(1));
                assertEquals(Double.MAX_VALUE, array.getDouble(2), 0.0);
                assertEquals(10L, array.getNumber(3).longValue());
            }
        });
    }

    @Test
    public void testAddString() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addString("");
        mArray.addString("Hello");
        mArray.addString("World");

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());

                assertEquals("", array.getString(0));
                assertEquals("Hello", array.getString(1));
                assertEquals("World", array.getString(2));
            }
        });
    }

    @Test
    public void testSetString() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addString("");
        mArray.addString("Hello");
        mArray.addString("World");

        mArray.setString(0, "Hello");
        mArray.setString(1, "World");
        mArray.setString(2, "");

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(3, array.count());

                assertEquals("", array.getString(2));
                assertEquals("Hello", array.getString(0));
                assertEquals("World", array.getString(1));
            }
        });
    }

    @Test
    public void testInsertString() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addString(""); // will be pushed 3 times.
        mArray.insertString(0, "Hello");
        mArray.insertString(1, "World");
        mArray.insertString(2, "!");

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals("Hello", array.getString(0));
                assertEquals("World", array.getString(1));
                assertEquals("!", array.getString(2));
                assertEquals("", array.getString(3));
            }
        });
    }

    @Test
    public void testAddBoolean() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addBoolean(true);
        mArray.addBoolean(false);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(2, array.count());

                assertEquals(true, array.getBoolean(0));
                assertEquals(false, array.getBoolean(1));
            }
        });
    }

    @Test
    public void testSetBoolean() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addBoolean(true);
        mArray.addBoolean(false);

        mArray.setBoolean(0, false);
        mArray.setBoolean(1, true);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(2, array.count());

                assertEquals(true, array.getBoolean(1));
                assertEquals(false, array.getBoolean(0));
            }
        });
    }

    @Test
    public void testInsertBoolean() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addBoolean(false); // will be pushed 2 times
        mArray.addBoolean(true); // will be pushed 2 times.
        mArray.insertBoolean(0, true);
        mArray.insertBoolean(1, false);

        mDoc.setArray("array", mArray);
        save(mDoc, new BaseTest.Validator<Document>() {
            @Override
            public void validate(Document doc) {
                assertEquals(1, doc.count());
                assertTrue(doc.contains("array"));
                Array array = doc.getArray("array");
                assertNotNull(array);
                assertEquals(4, array.count());
                assertEquals(true, array.getBoolean(0));
                assertEquals(false, array.getBoolean(1));
                assertEquals(false, array.getBoolean(2));
                assertEquals(true, array.getBoolean(3));
            }
        });
    }

}
