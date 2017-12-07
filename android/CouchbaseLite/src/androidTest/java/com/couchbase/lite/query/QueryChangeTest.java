package com.couchbase.lite.query;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class QueryChangeTest {
    @Test
    public void testQueryChangeTest() {
        QueryChange change = new QueryChange(null, null, null);
        assertNull(change.getQuery());
        assertNull(change.getRows());
        assertNull(change.getError());
    }
}
