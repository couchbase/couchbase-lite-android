//
// C4MutableFleeceTest.java
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
package com.couchbase.litecore;

import com.couchbase.litecore.fleece.AllocSlice;
import com.couchbase.litecore.fleece.Encoder;
import com.couchbase.litecore.fleece.FLValue;
import com.couchbase.litecore.fleece.FleeceDict;
import com.couchbase.litecore.fleece.FleeceDocument;
import com.couchbase.litecore.fleece.MRoot;
import com.couchbase.litecore.fleece.MValue;
import com.couchbase.litecore.fleece.MValueDelegate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class C4MutableFleeceTest extends C4BaseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MValue.registerDelegate(new MValueDelegate());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    static AllocSlice encode(Object obj) throws LiteCoreException {
        Encoder enc = new Encoder();
        enc.writeObject(obj);
        return enc.finish();
    }

    static AllocSlice encode(MRoot root) throws LiteCoreException {
        Encoder enc = new Encoder();
        root.encodeTo(enc);
        return enc.finish();
    }

    static List<String> sortedKeys(Map<String, Object> dict) {
        Set<String> keys = dict.keySet();
        ArrayList<String> list = new ArrayList<>(keys);
        Collections.sort(list);
        return list;
    }

    static void verifyDictIterator(Map<String, Object> dict) {
        int count = 0;
        Set<String> keys = new HashSet<>();
        for (String key : dict.keySet()) {
            count++;
            assertNotNull(key);
            keys.add(key);
        }
        assertEquals(dict.size(), keys.size());
        assertEquals(dict.size(), count);
    }

    static String fleece2JSON(AllocSlice fleece) {
        FLValue v = FLValue.fromData(fleece);
        if (v == null)
            return "INVALID_FLEECE";
        return v.toJSON5();
    }

    // TEST_CASE("MValue", "[Mutable]")
    @Test
    public void testMValue() {
        MValue val = new MValue("hi");
        assertEquals("hi", val.asNative(null));
        assertNull(val.getValue());
    }

    // TEST_CASE("MDict", "[Mutable]")
    @Test
    public void testMDict() throws LiteCoreException {
        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32);
        subMap.put("boil", 212);
        map.put("dict", subMap);
        AllocSlice data = encode(map);

        MRoot root = new MRoot(data);
        assertFalse(root.isMutated());
        Object obj = root.asNative();
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> dict = (Map<String, Object>) obj;
        assertNotNull(dict);
        assertEquals(3, dict.size());
        assertTrue(dict.containsKey("greeting"));
        assertFalse(dict.containsKey("x"));
        assertEquals(Arrays.asList("array", "dict", "greeting"), sortedKeys(dict));
        assertEquals("hi", dict.get("greeting"));
        assertNull(dict.get("x"));

        obj = dict.get("dict");
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> nested = (Map<String, Object>) obj;
        assertEquals(sortedKeys(nested), Arrays.asList("boil", "melt"));
        Map<String, Object> expected = new HashMap<>();
        expected.put("melt", 32L);
        expected.put("boil", 212L);
        assertEquals(expected, nested);
        assertEquals(32L, nested.get("melt"));
        assertEquals(212L, nested.get("boil"));
        assertNull(nested.get("freeze"));
        assertFalse(root.isMutated());

        verifyDictIterator(dict);

        nested.put("freeze", Arrays.asList(32L, "Fahrenheit"));
        assertTrue(root.isMutated());
        assertEquals(32L, nested.remove("melt"));
        expected.clear();
        expected.put("freeze", Arrays.asList(32L, "Fahrenheit"));
        expected.put("boil", 212L);
        assertEquals(expected, nested);

        verifyDictIterator(dict);

        assertEquals("{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                fleece2JSON(encode(dict)));
        assertEquals("{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                fleece2JSON(encode(root)));
    }

    // TEST_CASE("MArray", "[Mutable]")
    @Test
    public void testMArray() throws LiteCoreException {
        List<Object> list = Arrays.asList("hi", Arrays.asList("boo", false), 42);
        AllocSlice data = encode(list);

        MRoot root = new MRoot(data);
        assertFalse(root.isMutated());
        Object obj = root.asNative();
        assertNotNull(obj);
        assertTrue(obj instanceof List);
        List<Object> array = (List<Object>) obj;
        assertNotNull(array);
        assertEquals(3, array.size());
        assertEquals("hi", array.get(0));
        assertEquals(42L, array.get(2));
        assertNotNull(array.get(1));
        obj = array.get(1);
        assertTrue(obj instanceof List);
        assertEquals(Arrays.asList("boo", false), (List<Object>) obj);
        array.set(0, Arrays.asList(3.14, 2.17));
        array.add(2, "NEW");
        assertEquals(Arrays.asList(3.14, 2.17), array.get(0));
        assertEquals(Arrays.asList("boo", false), array.get(1));
        assertEquals("NEW", array.get(2));
        assertEquals(42L, array.get(3));
        assertEquals(4, array.size());

        List<Object> expected = new ArrayList<>();
        expected.add(Arrays.asList(3.14, 2.17));
        expected.add(Arrays.asList("boo", false));
        expected.add("NEW");
        expected.add(42L);
        assertEquals(expected, array);

        obj = (List<Object>) array.get(1);
        assertTrue(obj instanceof List);
        List<Object> nested = (List<Object>) obj;
        nested.set(1, true);

        assertEquals("[[3.14,2.17],[\"boo\",true],\"NEW\",42]",
                fleece2JSON(encode(array)));
        assertEquals("[[3.14,2.17],[\"boo\",true],\"NEW\",42]",
                fleece2JSON(encode(root)));
    }

    // TEST_CASE("MArray iteration", "[Mutable]")
    @Test
    public void testMArrayIteration() throws LiteCoreException {
        List<Object> orig = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            orig.add(String.format(Locale.ENGLISH, "This is item number %d", i));
        AllocSlice data = encode(orig);
        MRoot root = new MRoot(data);
        List<Object> array = (List<Object>) root.asNative();

        int i = 0;
        for (Object o : array) {
            assertEquals(orig.get(i), o);
            i++;
        }
    }

    // TEST_CASE("MDict no root", "[Mutable]")
    @Test
    public void testMDictNoRoot() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32);
        subMap.put("boil", 212);

        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);

        AllocSlice data = encode(map);
        Object obj = FleeceDocument.getObject(data, true);
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> dict = (Map<String, Object>) obj;
        assertFalse(((FleeceDict) dict).isMutated());
        assertEquals(Arrays.asList("array", "dict", "greeting"), sortedKeys(dict));
        assertEquals("hi", dict.get("greeting"));
        assertNull(dict.get("x"));
        verifyDictIterator(dict);

        obj = dict.get("dict");
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> nested = (Map<String, Object>) obj;
        assertEquals(sortedKeys(nested), Arrays.asList("boil", "melt"));
        Map<String, Object> expected = new HashMap<>();
        expected.put("melt", 32L);
        expected.put("boil", 212L);
        assertEquals(expected, nested);
        assertEquals(32L, nested.get("melt"));
        assertEquals(212L, nested.get("boil"));
        assertNull(nested.get("freeze"));
        verifyDictIterator(nested);
        assertFalse(((FleeceDict) nested).isMutated());
        assertFalse(((FleeceDict) dict).isMutated());

        nested.put("freeze", Arrays.asList(32L, "Fahrenheit"));
        assertTrue(((FleeceDict) nested).isMutated());
        assertTrue(((FleeceDict) dict).isMutated());
        assertEquals(32L, nested.remove("melt"));
        expected.clear();
        expected.put("freeze", Arrays.asList(32L, "Fahrenheit"));
        expected.put("boil", 212L);
        assertEquals(expected, nested);

        verifyDictIterator(nested);
        verifyDictIterator(dict);

        assertEquals("{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}", fleece2JSON(encode(dict)));
    }

    // TEST_CASE("Adding mutable collections", "[Mutable]")
    @Test
    public void testAddingMutableCollections() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32);
        subMap.put("boil", 212);

        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);

        AllocSlice data = encode(map);

        MRoot root = new MRoot(data);
        assertFalse(root.isMutated());
        Object obj = root.asNative();
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> dict = (Map<String, Object>) obj;

        obj = dict.get("array");
        assertTrue(obj instanceof List);
        List<Object> array = (List<Object>) obj;
        dict.put("new", array);
        array.add(true);

        assertEquals("{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}", fleece2JSON(encode(root)));
        assertEquals("{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}", fleece2JSON(root.encode()));
    }

    @Test
    public void testMRoot() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32L);
        subMap.put("boil", 212L);
        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);
        AllocSlice data = encode(map);

        MRoot root = new MRoot(data);
        assertFalse(root.isMutated());
        Object obj = root.asNative();
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> dict = (Map<String, Object>) obj;
        assertEquals("hi", dict.get("greeting"));
        assertEquals(Arrays.asList("boo", false), dict.get("array"));
        assertEquals(subMap, dict.get("dict"));

        assertEquals("{array:[\"boo\",false],dict:{boil:212,melt:32},greeting:\"hi\"}", fleece2JSON(root.encode()));

        List<Object> array = (List<Object>) dict.get("array");
        dict.put("new", array);
        array.add(true);
        assertEquals("{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}", fleece2JSON(root.encode()));
    }

    @Test
    public void testMRoot2() throws LiteCoreException {
        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        AllocSlice data = encode(map);

        MRoot root = new MRoot(data);
        assertFalse(root.isMutated());
        Object obj = root.asNative();
        assertNotNull(obj);
        assertTrue(obj instanceof Map);
        Map<String, Object> dict = (Map<String, Object>) obj;
        assertEquals("hi", dict.get("greeting"));
        assertEquals("{greeting:\"hi\"}", fleece2JSON(root.encode()));
        assertEquals("{greeting:\"hi\"}", fleece2JSON(encode(root)));

        dict.put("hello", "world");
        assertEquals("hi", dict.get("greeting"));
        assertEquals("world", dict.get("hello"));
        assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encode(dict)));
        assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encode(root.asNative())));
        assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(root.encode()));
        assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encode(root)));
    }
}
