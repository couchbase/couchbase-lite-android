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
