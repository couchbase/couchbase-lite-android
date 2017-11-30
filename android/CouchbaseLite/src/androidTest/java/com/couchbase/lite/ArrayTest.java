package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArrayTest extends BaseTest {

    static final String kArrayTestDate = "2007-06-30T08:34:09.001Z";
    static final String kArrayTestBlob = "i'm blob";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

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

        MutableDocument doc = createDocument("doc1");
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

        MutableDocument doc = createDocument("doc1");
        doc.setValue("array", array);
        assertEquals(array, doc.getArray("array"));

        Document savedDoc = save(doc);
        assertEquals(data, savedDoc.getArray("array").toList());
    }

    @Test
    public void testSetArrayList() throws CouchbaseLiteException {
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        MutableArray array = new MutableArray();
        array.set(data);
        assertEquals(3, array.count());
        assertEquals(data, array.toList());

        MutableDocument doc = createDocument("doc1");
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
        array.set(data);

        assertEquals(data.size(), array.count());
        assertEquals(data, array.toList());
    }

    @Test
    public void testNull() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        array.addValue(null);
        MutableDocument doc = createDocument("doc1");
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
        MutableArray array = new MutableArray();

        // Add objects of all types:
        populateData(array);

        MutableDocument doc = createDocument("doc1");
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
                MutableDictionary subdict = (MutableDictionary) a.getValue(9);
                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                assertEquals(expectedMap, subdict.toMap());

                // array
                MutableArray subarray = (MutableArray) a.getValue(10);
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
    public void testAddObjectsToExistingArray() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);

        // Save
        MutableDocument doc = createDocument("doc1");
        doc.setValue("array", array);
        doc = save(doc).toMutable();

        // Get an existing array:
        array = doc.getArray("array");
        assertNotNull(array);
        assertEquals(12, array.count());

        // Update:
        populateData(array);
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
                MutableDictionary subdict = (MutableDictionary) a.getValue(12 + 9);
                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                assertEquals(expectedMap, subdict.toMap());

                // array
                MutableArray subarray = (MutableArray) a.getValue(12 + 10);
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

        MutableDocument doc = createDocument("doc1");
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
                MutableDictionary subdict = (MutableDictionary) a.getValue(9);
                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                assertEquals(expectedMap, subdict.toMap());

                // array
                MutableArray subarray = (MutableArray) a.getValue(10);
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
        MutableDocument mDoc = createDocument("doc1");
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
        MutableArray array = new MutableArray();
        populateData(array);

        for (int i = array.count() - 1; i >= 0; i--) {
            array.remove(i);
        }

        MutableDocument doc = createDocument("doc1");
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(0, a.count());
                assertEquals(new ArrayList<Object>(), a.toList());
            }
        });
    }

    @Test
    public void testRemoveExistingArray() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);

        MutableDocument doc = createDocument("doc1");
        doc.setValue("array", array);
        doc = save(doc).toMutable();
        array = doc.getArray("array");

        for (int i = array.count() - 1; i >= 0; i--) {
            array.remove(i);
        }

        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(0, a.count());
                assertEquals(new ArrayList<Object>(), a.toList());
            }
        });
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
        MutableArray array = new MutableArray();
        populateData(array);

        MutableDocument doc = createDocument("doc1");
        save(doc, "array", array, new Validator<Array>() {
            @Override
            public void validate(Array a) {
                assertEquals(12, a.count());
            }
        });
    }

    @Test
    public void testGetString() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetNumber() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetInteger() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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
    }

    @Test
    public void testGetLong() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetFloat() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetDouble() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

        MutableDocument doc = createDocument("doc1");
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

        MutableDocument doc = createDocument("doc1");
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
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetDate() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetMap() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

    @Test
    public void testGetArray() throws CouchbaseLiteException {
        MutableArray array = new MutableArray();
        populateData(array);
        assertEquals(12, array.count());

        MutableDocument doc = createDocument("doc1");
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

        MutableDocument doc = createDocument("doc1");
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
        MutableDocument doc = createDocument("doc1");
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
        MutableDocument doc = createDocument("doc1");
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

        MutableDocument doc = createDocument("doc1");
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

        MutableDocument doc = createDocument("doc1");
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
        MutableDocument mDoc = createDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addValue(null);
        mArray.addString(null);
        mArray.addNumber(null);
        mArray.addDate(null);
        mArray.addArray(null);
        mArray.addDictionary(null);
        mDoc.setArray("array", mArray);
        Document doc = save(mDoc, new BaseTest.Validator<Document>() {
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
}
