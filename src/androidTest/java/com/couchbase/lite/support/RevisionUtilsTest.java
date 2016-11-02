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
package com.couchbase.lite.support;

import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Created by hideki on 10/5/16.
 */
public class RevisionUtilsTest extends LiteTestCase {
    public static final String TAG = "RevisionUtilsTest";

    // RevisionUtils.asCanonicalJSON() does not gurantee key order now.
    // This is covered by DeepClone now. This is performance purpose.
    public void testAsCanonicalJSON() {
        Map<String, Object> src = new HashMap<String, Object>();
        src.put("foo", "bar");
        src.put("what", "rev2a");
        Map<String, Object> nest = new HashMap<String, Object>();
        nest.put("foo", "bar");
        nest.put("what", "rev2a");
        nest.put("x\"Y\"z", "rev2b");
        nest.put("x'Y'z", "rev2b");
        nest.put("x\\Y\\z", "rev2c");
        src.put("nest", nest);
        List<Object> list = new ArrayList<Object>();
        list.add("start");
        Map<String, Object> mapInList = new HashMap<String, Object>();
        mapInList.put("foo", "bar");
        mapInList.put("what", "rev2a");
        list.add(mapInList);
        list.add("end");
        src.put("list", list);
        String json = new String(RevisionUtils.asCanonicalJSON(src));
        assertEquals("{\"foo\":\"bar\",\"list\":[\"start\",{\"foo\":\"bar\",\"what\":\"rev2a\"},\"end\"],\"nest\":{\"foo\":\"bar\",\"what\":\"rev2a\",\"x\\\"Y\\\"z\":\"rev2b\",\"x'Y'z\":\"rev2b\",\"x\\\\Y\\\\z\":\"rev2c\"},\"what\":\"rev2a\"}", json);
        Log.i(TAG, "testAsCanonicalJSON() json=[%s]", json);
    }
}
