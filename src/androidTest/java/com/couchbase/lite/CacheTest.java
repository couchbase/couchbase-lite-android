package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

public class CacheTest extends LiteTestCaseWithDB {

    public void testCache() throws Exception {

        int retainCount = 1;
        Cache cache = new Cache<String, Document>(retainCount);

        Map<String,Object> props = new HashMap<String, Object>();
        props.put("foo", "bar");
        Document doc1 = createDocumentWithProperties(database, props);
        cache.put(doc1.getId(), doc1);

        Map<String,Object> props2 = new HashMap<String, Object>();
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
