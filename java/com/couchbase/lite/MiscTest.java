package com.couchbase.lite;

import junit.framework.Assert;

public class MiscTest extends LiteTestCase {

    public void testUnquoteString() {

        String testString = "attachment; filename=\"attach\"";
        String expected = "attachment; filename=attach";
        String result = com.couchbase.lite.Misc.unquoteString(testString);
        Assert.assertEquals(expected, result);

    }
    
}
