package com.couchbase.lite;

import com.couchbase.lite.util.Log;

/**
 * Created by hideki on 2/20/15.
 */
public class BlobKeyTest extends LiteTestCase {
    public void testConvertToHex(){
        String src = "Hello World!";
        Log.i(Log.TAG, "SRC => " + src);
        String hex = BlobKey.convertToHex(src.getBytes());
        Log.i(Log.TAG, "HEX => " + hex);
        String dest = new String(BlobKey.convertFromHex(hex));
        Log.i(Log.TAG, "DEST => " + dest);
        assertEquals(src, dest);
        assertEquals(hex.toUpperCase(), hex);
    }
}
