package com.couchbase.lite.testapp.tests;

import android.test.InstrumentationTestCase;

import com.couchbase.lite.CBLMisc;

import junit.framework.Assert;

public class Misc extends InstrumentationTestCase {

    public void testUnquoteString() {

        String testString = "attachment; filename=\"attach\"";
        String expected = "attachment; filename=attach";
        String result = CBLMisc.unquoteString(testString);
        Assert.assertEquals(expected, result);

    }
    
}
