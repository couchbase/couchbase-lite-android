package com.couchbase.cblite.testapp.tests;

import junit.framework.Assert;

import org.codehaus.jackson.map.ObjectMapper;

import android.test.AndroidTestCase;
import android.util.Log;

import com.couchbase.touchdb.TDCollateJSON;

public class Collation extends AndroidTestCase {

    public static String TAG = "Collation";

    private static final int kTDCollateJSON_Unicode = 0;
    private static final int kTDCollateJSON_Raw = 1;
    private static final int kTDCollateJSON_ASCII = 2;

    // create the same JSON encoding used by TouchDB
    // this lets us test comparisons as they would be encoded
    public String encode(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            byte[] bytes = mapper.writeValueAsBytes(obj);
            String result = new String(bytes);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding JSON", e);
            return null;
        }
    }

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
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"12\\/34\"", 0, "\"12/34\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"\\/1234\"", 0, "\"/1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\\/\"", 0, "\"1234/\""));
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
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"12\\/34\"", 0, "\"12/34\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"\\/1234\"", 0, "\"/1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, "\"1234\\/\"", 0, "\"1234/\""));
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

    public void testCollateUnicodeStrings() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(0, TDCollateJSON.testCollateJSON(mode, 0, encode("fréd"), 0, encode("fréd")));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSON(mode, 0, encode("ømø"), 0, encode("omo")));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, encode("\t"), 0, encode(" ")));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSON(mode, 0, encode("\001"), 0, encode(" ")));

    }

    public void testConvertEscape() {
        Assert.assertEquals('\\', TDCollateJSON.testEscape("\\\\"));
        Assert.assertEquals('\t', TDCollateJSON.testEscape("\\t"));
        Assert.assertEquals('E', TDCollateJSON.testEscape("\\u0045"));
        Assert.assertEquals(1, TDCollateJSON.testEscape("\\u0001"));
        Assert.assertEquals(0, TDCollateJSON.testEscape("\\u0000"));
    }

    public void testDigitToInt() {
        Assert.assertEquals(1, TDCollateJSON.testDigitToInt('1'));
        Assert.assertEquals(7, TDCollateJSON.testDigitToInt('7'));
        Assert.assertEquals(0xc, TDCollateJSON.testDigitToInt('c'));
        Assert.assertEquals(0xc, TDCollateJSON.testDigitToInt('C'));
    }

}
