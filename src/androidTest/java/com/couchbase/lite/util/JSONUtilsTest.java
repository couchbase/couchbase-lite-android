package com.couchbase.lite.util;

import com.couchbase.lite.LiteTestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by hideki on 7/27/15.
 */
public class JSONUtilsTest extends LiteTestCase {
    public void testEstimate() throws Exception {
        Map<String, Object> obj = new HashMap<String, Object>(); // map:    + 20
        List<Object> value = new ArrayList<Object>();            // array:  + 20
        value.add("1234567890");                                 // string: + 20 + 2 * 10 => +40
        value.add(12345);                                        // number: + 20 + 8 => +28
        obj.put("key", value);                                   // key(string): +20 + 6 => +26
        long size = JSONUtils.estimate(obj);
        assertEquals(134, size);
    }
}
