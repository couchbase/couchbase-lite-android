package com.couchbase.lite;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DictionaryTest extends BaseTest {
    @Test
    public void testCreateDictionary(){
        Dictionary address = new Dictionary();
        assertEquals(0, address.count());
        assertEquals(new HashMap<String, Object>(), address.toMap());


        Document doc1 = new Document("doc1");
        doc1.set("address", address);
        assertEquals(address, doc1.getDictionary("address"));

        db.save(doc1);
        doc1 = db.getDocument("doc1");
        assertEquals(new HashMap<String, Object>(), doc1.getDictionary("address").toMap());
    }
    @Test
    public void testCreateDictionaryWithNSDictionary(){

        Map<String, Object> dict = new HashMap<>();
        dict.put("street","1 Main street");
        dict.put("city","Mountain View");
        dict.put("state","CA");
        Dictionary address = new Dictionary(dict);
        assertEquals(3, address.count());
        assertEquals("1 Main street", address.getObject("street"));
        assertEquals("Mountain View", address.getObject("city"));
        assertEquals("CA", address.getObject("state"));
        assertEquals(dict, address.toMap());

        Document doc1 = new Document("doc1");
        doc1.set("address", address);
        assertEquals(address, doc1.getDictionary("address"));

        db.save(doc1);
        doc1 = db.getDocument("doc1");
        assertEquals(dict, doc1.getDictionary("address").toMap());
    }
}
