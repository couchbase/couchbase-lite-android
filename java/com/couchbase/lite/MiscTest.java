package com.couchbase.lite;

import android.test.InstrumentationTestCase;

import junit.framework.Assert;

public class MiscTest extends InstrumentationTestCase {

    public void testUnquoteString() {

        String testString = "attachment; filename=\"attach\"";
        String expected = "attachment; filename=attach";
        String result = com.couchbase.lite.Misc.unquoteString(testString);
        Assert.assertEquals(expected, result);

    }
    
}
