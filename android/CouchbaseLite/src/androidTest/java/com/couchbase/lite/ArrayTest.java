package com.couchbase.lite;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class ArrayTest extends BaseTest{
    private void arrayOfAllTypes(){

    }
    private void populateData(Array array){

    }
    private void save(Document doc, String key, Array array){

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
}
