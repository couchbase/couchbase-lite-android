/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.support;

import com.couchbase.lite.LiteTestCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonDocumentTest extends LiteTestCase {

    public void testJsonObject() throws Exception {
        Map<String, Object> dict = new HashMap<String, Object>();
        dict.put("id","01234567890");
        dict.put("foo","bar");
        dict.put("int",5);
        dict.put("double",3.5);
        dict.put("bool",true);
        dict.put("date",new Date().toString());
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(dict);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(dict, jsdoc.jsonObject());
    }

    public void testJsonArray() throws Exception {
        List<Object> array = new ArrayList<Object>();
        array.add("01234567890");
        array.add("bar");
        array.add(5);
        array.add(3.5);
        array.add(true);
        array.add(new Date().toString());
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(array);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(array, jsdoc.jsonObject());
    }

    public void testStringFragment() throws Exception {
        String fragment = "01234567890";
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(fragment);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(fragment, jsdoc.jsonObject());
    }

    public void testBooleanFragment() throws Exception {
        Boolean fragment = true;
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(fragment);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(fragment, jsdoc.jsonObject());
    }

    public void testIntegerFragment() throws Exception {
        Integer fragment = 5;
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(fragment);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(fragment, jsdoc.jsonObject());
    }

    public void testDoubleFragment() throws Exception {
        Double fragment = 3.5;
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(fragment);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(fragment, jsdoc.jsonObject());
    }

    public void testDateFragment() throws Exception {
        Date fragment = new Date();
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(fragment);
        JsonDocument jsdoc = new JsonDocument(json);
        assertEquals(fragment, new Date((Long)jsdoc.jsonObject()));
    }
}
