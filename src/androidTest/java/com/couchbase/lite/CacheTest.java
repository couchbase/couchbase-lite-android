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
package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

public class CacheTest extends LiteTestCaseWithDB {
    public void testCache() throws Exception {
        int retainCount = 1;
        Cache cache = new Cache<String, Document>(retainCount);
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("foo", "bar");
        Document doc1 = createDocumentWithProperties(database, props);
        cache.put(doc1.getId(), doc1);
        Map<String, Object> props2 = new HashMap<String, Object>();
        props2.put("foo2", "bar2");
        Document doc2 = createDocumentWithProperties(database, props2);
        cache.put(doc2.getId(), doc2);
        assertNotNull(cache.get(doc1.getId()));
        assertNotNull(cache.get(doc2.getId()));
        cache.remove(doc1.getId());
        assertNull(cache.get(doc1.getId()));
        cache.clear();
        assertNull(cache.get(doc2.getId()));
    }
}
