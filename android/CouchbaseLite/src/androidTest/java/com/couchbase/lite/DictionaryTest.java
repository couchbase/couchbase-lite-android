package com.couchbase.lite;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DictionaryTest extends BaseTest {
    @Test
    public void testCreateDictionary() {
        Dictionary address = new Dictionary();
        assertEquals(0, address.count());
        assertEquals(new HashMap<String, Object>(), address.toMap());

        Document doc = createDocument("doc1");
        doc.set("address", address);
        assertEquals(address, doc.getDictionary("address"));

        save(doc);
        doc = db.getDocument("doc1");
        assertEquals(new HashMap<String, Object>(), doc.getDictionary("address").toMap());
    }

    @Test
    public void testCreateDictionaryWithNSDictionary() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");
        Dictionary address = new Dictionary(dict);
        assertEquals(3, address.count());
        assertEquals("1 Main street", address.getObject("street"));
        assertEquals("Mountain View", address.getObject("city"));
        assertEquals("CA", address.getObject("state"));
        assertEquals(dict, address.toMap());

        Document doc1 = createDocument("doc1");
        doc1.set("address", address);
        assertEquals(address, doc1.getDictionary("address"));

        save(doc1);
        doc1 = db.getDocument("doc1");
        assertEquals(dict, doc1.getDictionary("address").toMap());
    }

    @Test
    public void testGetValueFromNewEmptyDictionary() {
        Dictionary dict = new Dictionary();

        assertEquals(0, dict.getInt("key"));
        assertEquals(0.0f, dict.getFloat("key"), 0.0f);
        assertEquals(0.0, dict.getDouble("key"), 0.0);
        assertFalse(dict.getBoolean("key"));
        assertTrue(dict.getBlob("key") == null);
        assertTrue(dict.getDate("key") == null);
        assertTrue(dict.getNumber("key") == null);
        assertTrue(dict.getObject("key") == null);
        assertTrue(dict.getString("key") == null);
        assertTrue(dict.getDictionary("key") == null);
        assertTrue(dict.getArray("key") == null);
        assertEquals(new HashMap<String, Object>(), dict.toMap());

        Document doc = createDocument("doc1");
        doc.set("dict", dict);

        save(doc);
        doc = db.getDocument("doc1");

        dict = doc.getDictionary("dict");

        assertEquals(0, dict.getInt("key"));
        assertEquals(0.0f, dict.getFloat("key"), 0.0f);
        assertEquals(0.0, dict.getDouble("key"), 0.0);
        assertFalse(dict.getBoolean("key"));
        assertTrue(dict.getBlob("key") == null);
        assertTrue(dict.getDate("key") == null);
        assertTrue(dict.getNumber("key") == null);
        assertTrue(dict.getObject("key") == null);
        assertTrue(dict.getString("key") == null);
        assertTrue(dict.getDictionary("key") == null);
        assertTrue(dict.getArray("key") == null);
        assertEquals(new HashMap<String, Object>(), dict.toMap());
    }

    @Test
    public void testSetNestedDictionaries() {
        Document doc = createDocument("doc1");

        Dictionary level1 = new Dictionary();
        level1.set("name", "n1");
        doc.set("level1", level1);

        Dictionary level2 = new Dictionary();
        level2.set("name", "n2");
        doc.set("level2", level2);

        Dictionary level3 = new Dictionary();
        level3.set("name", "n3");
        doc.set("level3", level3);

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

        save(doc);
        doc = db.getDocument("doc1");

        assertTrue(level1 != doc.getDictionary("level1"));
        assertEquals(dict, doc.toMap());
    }

    @Test
    public void testDictionaryArray() {
        Document doc = createDocument("doc1");

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

        doc.set("array", data);

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

        // after save
        save(doc);
        doc = db.getDocument("doc1");

        array = doc.getArray("array");
        assertEquals(4, array.count());

        dict1 = array.getDictionary(0);
        dict2 = array.getDictionary(1);
        dict3 = array.getDictionary(2);
        dict4 = array.getDictionary(3);

        assertEquals("1", dict1.getString("name"));
        assertEquals("2", dict2.getString("name"));
        assertEquals("3", dict3.getString("name"));
        assertEquals("4", dict4.getString("name"));
    }

    @Test
    public void testReplaceDictionary() {
        Document doc = createDocument("doc1");

        Dictionary profile1 = new Dictionary();
        profile1.set("name", "Scott Tiger");
        doc.set("profile", profile1);
        assertEquals(profile1, doc.getDictionary("profile"));

        Dictionary profile2 = new Dictionary();
        profile2.set("name", "Daniel Tiger");
        doc.set("profile", profile2);
        assertEquals(profile2, doc.getDictionary("profile"));

        // Profile1 should be now detached:
        profile1.set("age", 20);
        assertEquals("Scott Tiger", profile1.getObject("name"));
        assertEquals(20, profile1.getObject("age"));

        // Check profile2:
        assertEquals("Daniel Tiger", profile2.getObject("name"));
        assertTrue(null == profile2.getObject("age"));

        // Save:
        save(doc);
        doc = db.getDocument("doc1");

        assertTrue(profile2 != doc.getDictionary("profile"));
        profile2 = doc.getDictionary("profile");
        assertEquals("Daniel Tiger", profile2.getObject("name"));
    }

    @Test
    public void testReplaceDictionaryDifferentType() {
        Document doc = createDocument("doc1");

        Dictionary profile1 = new Dictionary();
        profile1.set("name", "Scott Tiger");
        doc.set("profile", profile1);
        assertEquals(profile1, doc.getDictionary("profile"));

        // Set string value to profile:
        doc.set("profile", "Daniel Tiger");
        assertEquals("Daniel Tiger", doc.getObject("profile"));

        // Profile1 should be now detached:
        profile1.set("age", 20);
        assertEquals("Scott Tiger", profile1.getObject("name"));
        assertEquals(20, profile1.getObject("age"));

        // Check whether the profile value has no change:
        assertEquals("Daniel Tiger", doc.getObject("profile"));

        // Save
        save(doc);
        doc = db.getDocument("doc1");
        assertEquals("Daniel Tiger", doc.getObject("profile"));
    }

    @Test
    public void testRemoveDictionary(){
        Document doc = createDocument("doc1");
        Dictionary profile1 = new Dictionary();
        profile1.set("name", "Scott Tiger");
        doc.set("profile", profile1);
        assertEquals(profile1.toMap(), doc.getDictionary("profile").toMap());
        assertTrue(doc.contains("profile"));

        // Remove profile
        doc.remove("profile");
        assertNull(doc.getObject("profile"));
        assertFalse(doc.contains("profile"));

        // Profile1 should be now detached:
        profile1.set("age", 20);
        assertEquals("Scott Tiger", profile1.getObject("name"));
        assertEquals(20, profile1.getObject("age"));

        // Check whether the profile value has no change:
        assertNull(doc.getObject("profile"));

        // Save:
        doc = save(doc);

        assertNull(doc.getObject("profile"));
        assertFalse(doc.contains("profile"));
    }

    @Test
    public void testEnumeratingKeys() {
        final Dictionary dict = new Dictionary();
        for (int i = 0; i < 20; i++)
            dict.set(String.format(Locale.ENGLISH, "key%d", i), i);
        Map<String, Object> content = dict.toMap();

        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (String key : dict) {
            result.put(key, dict.getObject(key));
            count++;
        }
        assertEquals(content.size(), count);
        assertEquals(content, result);

        // Update:
        dict.remove("key2");
        dict.set("key20", 20);
        dict.set("key21", 21);
        content = dict.toMap();

        result = new HashMap<>();
        count = 0;
        for (String key : dict) {
            result.put(key, dict.getObject(key));
            count++;
        }
        assertEquals(content.size(), count);
        assertEquals(content, result);

        final Map<String, Object> finalContent = content;

        Document doc = createDocument("doc1");
        doc.set("dict", dict);
        save(doc, new Validator<Document>() {
            @Override
            public void validate(Document doc) {
                Map<String, Object> result = new HashMap<>();
                int count = 0;
                Dictionary dictObj = doc.getDictionary("dict");
                for (String key : dictObj) {
                    result.put(key, dict.getObject(key));
                    count++;
                }
                assertEquals(finalContent.size(), count);
                assertEquals(finalContent, result);
            }
        });
    }
}
