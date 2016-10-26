//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//

package com.couchbase.lite.util;

import com.couchbase.lite.LiteTestCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeepCloneTest extends LiteTestCase {

    // make sure deepClone should clone nested object
    public void testDeepCloneWithJsonParser() throws Exception {
        Map<String, Object> map1 = new HashMap<String, Object>();
        Object[] objs = {new String("a"), new String("b")};
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("hello", "world");
        List<Object> list = new ArrayList<Object>();
        list.add("a");
        list.add("b");
        int[] ints = {1, 2};
        map1.put("objs", objs);
        map1.put("ints", ints);
        map1.put("map", map);
        map1.put("list", list);
        Map<String, Object> deepMap1 = DeepClone.deepClone(map1);
        ObjectMapper mapper = new ObjectMapper();
        String str1 = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(map1);
        String str2 = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(deepMap1);
        assertEquals(str1, str2);
        assertTrue(str1.equals(str2));
        ((Map) deepMap1.get("map")).put("extra", "hey!");
        str1 = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(map1);
        str2 = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(deepMap1);
        assertFalse(str1.equals(str2));
    }
}
