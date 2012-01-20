package com.couchbase.touchdb.testapp.tests;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDCollateJSON;

public class Collation extends AndroidTestCase {

    private static final int kTDCollateJSON_Unicode = 0;
    private static final int kTDCollateJSON_Raw = 1;
    private static final int kTDCollateJSON_ASCII = 2;

    public void testCollateScalars() {

        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "true", 0, "false"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "false", 0, "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "null", 0, "17"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "123", 0, "1"));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "123", 0, "0123.0"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "123", 0, "\"123\""));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\"", 0, "\"123\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\"", 0, "\"1235\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\"", 0, "\"1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"12\\q34\"", 0, "\"12q34\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"\\q1234\"", 0, "\"q1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\\q\"", 0, "\"1234q\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"a\"", 0, "\"A\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"A\"", 0, "\"aa\""));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "\"B\"", 0, "\"aa\""));


    }

    public void testCollateASCII() {
        int mode = kTDCollateJSON_ASCII;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "true", 0, "false"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "false", 0, "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "null", 0, "17"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "123", 0, "1"));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "123", 0, "0123.0"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "123", 0, "\"123\""));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\"", 0, "\"123\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\"", 0, "\"1235\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\"", 0, "\"1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"12\\q34\"", 0, "\"12q34\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"\\q1234\"", 0, "\"q1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\\q\"", 0, "\"1234q\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"A\"", 0, "\"a\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"B\"", 0, "\"a\""));
    }

    public void testCollateRaw() {
        int mode = kTDCollateJSON_Raw;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "false", 0, "17"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "false", 0, "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "null", 0, "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "[\"A\"]", 0, "\"A\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "\"A\"", 0, "\"a\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "[\"b\"]", 0, "[\"b\",\"c\",\"a\"]"));
    }

    public void testCollateArrays() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "[]", 0, "\"foo\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "[]", 0, "[]"));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "[true]", 0, "[true]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "[false]", 0, "[null]"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "[]", 0, "[null]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "[123]", 0, "[45]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "[123]", 0, "[45,67]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "[123.4,\"wow\"]", 0, "[123.40,789]"));
    }

    public void testCollateNestedArray() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, "[[]]", 0, "[]"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, "[1,[2,3],4]", 0, "[1,[2,3.1],4,5,6]"));
    }

}
