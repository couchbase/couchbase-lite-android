package com.couchbase.lite.query;


import com.couchbase.lite.Expression;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FullTextFunctionTest {
    @Test
    public void testRank() {
        Expression expr = FullTextFunction.rank("abc");
        assertNotNull(expr);
        Object obj = expr.asJSON();
        assertNotNull(obj);
        assertTrue(obj instanceof List);
        assertEquals((List<Object>) Arrays.asList((Object) "RANK()", (Object) "abc"), (List<Object>) obj);
    }
}
