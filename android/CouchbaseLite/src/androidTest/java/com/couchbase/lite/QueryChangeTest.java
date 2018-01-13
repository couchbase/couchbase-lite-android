package com.couchbase.lite;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class QueryChangeTest {
    @Test
    public void testQueryChangeTest() {
        QueryChange change = new QueryChange(null, null, null);
        assertNull(change.getQuery());
        assertNull(change.getResults());
        assertNull(change.getError());
    }
}
