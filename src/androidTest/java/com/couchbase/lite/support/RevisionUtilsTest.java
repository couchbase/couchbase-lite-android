package com.couchbase.lite.support;

import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

/**
 * Created by hideki on 10/5/16.
 */

public class RevisionUtilsTest  extends LiteTestCase {
    public static final String TAG = "RevisionUtilsTest";

    public void testConvertFromMapToSortedMap() throws Exception {
        Map<String, Object> src = new HashMap<String, Object>();
        src.put("foo", "bar");
        src.put("what", "rev2a");
        Map<String, Object> nest = new HashMap<String, Object>();
        nest.put("foo", "bar");
        nest.put("what", "rev2a");
        src.put("nest", nest);
        SortedMap<String, Object> dest = RevisionUtils.convert(src);
        assertNotNull(dest);
        assertEquals("foo", dest.firstKey());
        assertEquals("what", dest.lastKey());
        assertEquals("foo", ((SortedMap) dest.get("nest")).firstKey());
        assertEquals("what", ((SortedMap) dest.get("nest")).lastKey());
    }

    public void testAsCanonicalJSON(){
        Map<String, Object> src = new HashMap<String, Object>();
        src.put("foo", "bar");
        src.put("what", "rev2a");
        Map<String, Object> nest = new HashMap<String, Object>();
        nest.put("foo", "bar");
        nest.put("what", "rev2a");
        src.put("nest", nest);
        String json = new String(RevisionUtils.asCanonicalJSON(src));
        assertEquals("{\"foo\":\"bar\",\"nest\":{\"foo\":\"bar\",\"what\":\"rev2a\"},\"what\":\"rev2a\"}", json);
        Log.e(TAG,"testAsCanonicalJSON() json=[%s]",json);
    }
}
