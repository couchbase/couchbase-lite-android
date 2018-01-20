package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EncryptionKeyTest extends BaseTest {
    @Test
    public void testDerivePBKDF2SHA256Key() {
        EncryptionKey key = new EncryptionKey("hello world!");
        assertNotNull(key.getKey());
        assertEquals(32, key.getKey().length);
        Log.i(TAG, "key -> " + bytesToHex(key.getKey()));
    }

    static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
