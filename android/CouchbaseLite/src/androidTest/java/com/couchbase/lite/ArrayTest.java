package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArrayTest extends BaseTest{

    static final String kArrayTestDate = "2007-06-30T08:34:09.001Z";
    static final String kArrayTestBlob = "i'm blob";

    private List<Object> arrayOfAllTypes(){
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

        Dictionary subdict = new Dictionary();
        subdict.set("name", "Scott Tiger");
        list.add(subdict);

        Array subarray = new Array();
        subarray.add("a");
        subarray.add("b");
        subarray.add("c");
        list.add(subarray);

        // Blob
        byte[] content = kArrayTestBlob.getBytes();
        Blob blob = new Blob("text/plain", content);
        list.add(blob);

        return list;
    }
    private void populateData(Array array){
        List<Object> data = arrayOfAllTypes();
        for(Object o : data) {
            array.add(o);
        }
    }

    interface Validator<T>{
        void validate(T array);
    }

    private void save(Document doc, String key, Array array, Validator<Array> validator){
        validator.validate(array);

        doc.set(key, array);
        db.save(doc);
        doc = db.getDocument(doc.getId());
        array = doc.getArray(key);

        validator.validate(array);
    }

    @Test
    public void testCreate(){
        Array array = new Array();
        assertEquals(0, array.count());
        assertEquals(new ArrayList<>(), array.toList());

        Document doc = new Document("doc1");
        doc.set("array", array);
        assertEquals(array, doc.getArray("array"));

        db.save(doc);
        doc = db.getDocument("doc1");
        assertEquals(new ArrayList<>(), doc.getArray("array").toList());
    }

    @Test
    public void testCreateWithList(){
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        Array array = new Array(data);
        assertEquals(3, array.count());
        assertEquals(data, array.toList());

        Document doc = new Document("doc1");
        doc.set("array", array);
        assertEquals(array, doc.getArray("array"));

        db.save(doc);
        doc = db.getDocument("doc1");
        assertEquals(data, doc.getArray("array").toList());
    }

    @Test
    public void testSetNSArray(){
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        Array array = new Array();
        array.set(data);
        assertEquals(3, array.count());
        assertEquals(data, array.toList());

        Document doc = new Document("doc1");
        doc.set("array", array);
        assertEquals(array, doc.getArray("array"));

        // save
        db.save(doc);
        doc = db.getDocument("doc1");
        assertEquals(data, doc.getArray("array").toList());

        // update
        array = doc.getArray("array");
        data = new ArrayList<>();
        data.add("4");
        data.add("5");
        data.add("6");
        array.set(data);

        assertEquals(data.size(), array.count());
        assertEquals(data, array.toList());
    }

    @Test
    public void testAddObjects(){
        Array array = new Array();

        // Add objects of all types:
        populateData(array);

        Document doc = new Document("doc1");
        save(doc, "array", array, new Validator<Array>(){
            @Override
            public void validate(Array a) {
                assertEquals(12, a.count());

                assertEquals(true, a.getObject(0));
                assertEquals(false, a.getObject(1));
                assertEquals("string", a.getObject(2));
                assertEquals(0, a.getObject(3));
                assertEquals(1, a.getObject(4));
                assertEquals(-1, a.getObject(5));
                assertEquals(1.1, a.getObject(6));
                assertEquals(kArrayTestDate, a.getObject(7));
                assertEquals(null, a.getObject(8));

                // dictionary
                Dictionary subdict = (Dictionary) a.getObject(9);
                Map<String,Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                assertEquals(expectedMap, subdict.toMap());

                // array
                Array subarray = (Array) a.getObject(10);
                List<Object> expected = new ArrayList<>();
                expected.add("a");
                expected.add("b");
                expected.add("c");
                assertEquals(expected, subarray.toList());

                // blob
                Blob blob = (Blob)a.getObject(11);
                assertTrue(Arrays.equals(kArrayTestBlob.getBytes(), blob.getContent()));
                assertEquals(kArrayTestBlob, new String(blob.getContent()));
            }
        });
    }

    @Test
    public void testAddObjectsToExistingArray(){
        Array array = new Array();
        populateData(array);

        // Save
        Document doc = new Document("doc1");
        doc.set("array", array);
        db.save(doc);
        doc = db.getDocument("doc1");

        // Get an existing array:
        array = doc.getArray("array");
        assertNotNull(array);
        assertEquals(12, array.count());

        // Update:
        populateData(array);
        assertEquals(24, array.count());

        save(doc, "array", array, new Validator<Array>(){
            @Override
            public void validate(Array a) {
                assertEquals(24, a.count());

                assertEquals(true, a.getObject(12+0));
                assertEquals(false, a.getObject(12+1));
                assertEquals("string", a.getObject(12+2));
                assertEquals(0, a.getObject(12+3));
                assertEquals(1, a.getObject(12+4));
                assertEquals(-1, a.getObject(12+5));
                assertEquals(1.1, a.getObject(12+6));
                assertEquals(kArrayTestDate, a.getObject(12+7));
                assertEquals(null, a.getObject(12+8));

                // dictionary
                Dictionary subdict = (Dictionary) a.getObject(12+9);
                Map<String,Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                assertEquals(expectedMap, subdict.toMap());

                // array
                Array subarray = (Array) a.getObject(12+10);
                List<Object> expected = new ArrayList<>();
                expected.add("a");
                expected.add("b");
                expected.add("c");
                assertEquals(expected, subarray.toList());

                // blob
                Blob blob = (Blob)a.getObject(12+11);
                assertTrue(Arrays.equals(kArrayTestBlob.getBytes(), blob.getContent()));
                assertEquals(kArrayTestBlob, new String(blob.getContent()));
            }
        });
    }
}
