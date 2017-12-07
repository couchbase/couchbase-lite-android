package com.couchbase.lite.internal.query;

import org.junit.Test;

import static org.junit.Assert.fail;

public class LiveQueryTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new LiveQuery(null);
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }
}
