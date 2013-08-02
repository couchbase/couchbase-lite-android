package com.couchbase.cblite.testapp.tests;

import android.test.InstrumentationTestCase;

import com.couchbase.cblite.CBLMisc;

import junit.framework.Assert;

public class Misc extends InstrumentationTestCase {

    public void testUnquoteString() {

        String testString = "attachment; filename=\"attach\"";
        String expected = "attachment; filename=attach";
        String result = CBLMisc.unquoteString(testString);
        Assert.assertEquals(expected, result);

    }
    
}
