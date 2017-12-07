package com.couchbase.lite.internal.bridge;

import com.couchbase.lite.CouchbaseLiteRuntimeException;
import com.couchbase.litecore.LiteCoreException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LiteCoreBridgeTest {
    @Test
    public void testConvertRuntimeException() {
        LiteCoreException orgEx = new LiteCoreException(1, 2, "3");
        CouchbaseLiteRuntimeException ex = LiteCoreBridge.convertRuntimeException(orgEx);
        assertNotNull(ex);
        assertEquals(1, ex.getDomain());
        assertEquals(2, ex.getCode());
        assertEquals("3", ex.getMessage());
        assertEquals(orgEx, ex.getCause());
    }
}
