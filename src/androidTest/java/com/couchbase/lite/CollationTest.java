package com.couchbase.lite;

import com.couchbase.lite.util.Log;
import com.couchbase.touchdb.RevCollator;
import com.couchbase.touchdb.TDCollateJSON;

import junit.framework.Assert;

import org.codehaus.jackson.map.ObjectMapper;

public class CollationTest extends LiteTestCase {

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
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "true", "false"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "false", "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "null", "17"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "123", "1"));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "123", "0123.0"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "123", "\"123\""));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"a\"", "\"A\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"A\"", "\"aa\""));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "\"B\"", "\"aa\""));


    }


    public void testCollateASCII() {
        int mode = kTDCollateJSON_ASCII;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "true", "false"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "false", "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "null", "17"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "123", "1"));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "123", "0123.0"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "123", "\"123\""));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"B\"", "\"a\""));
    }

    public void testCollateRaw() {
        int mode = kTDCollateJSON_Raw;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "false", "17"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "false", "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "null", "true"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "[\"A\"]", "\"A\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "[\"b\"]", "[\"b\",\"c\",\"a\"]"));
    }

    public void testCollateArrays() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "[]", "\"foo\""));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "[]", "[]"));
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, "[true]", "[true]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "[false]", "[null]"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "[]", "[null]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "[123]", "[45]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "[123]", "[45,67]"));
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "[123.4,\"wow\"]", "[123.40,789]"));
    }

    public void testCollateNestedArray() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, "[[]]", "[]"));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, "[1,[2,3],4]", "[1,[2,3.1],4,5,6]"));
    }

    public void testCollateUnicodeStrings() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(0, TDCollateJSON.testCollateJSONWrapper(mode, encode("fr�d"), encode("fr�d")));
        // Assert.assertEquals(1, TDCollateJSON.testCollateJSONWrapper(mode, encode("�m�"), encode("omo")));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, encode("\t"), encode(" ")));
        Assert.assertEquals(-1, TDCollateJSON.testCollateJSONWrapper(mode, encode("\001"), encode(" ")));

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

    public void testCollateRevIds() {
        Assert.assertEquals(RevCollator.testCollateRevIds("1-foo", "1-foo"), 0);
        Assert.assertEquals(RevCollator.testCollateRevIds("2-bar", "1-foo"), 1);
        Assert.assertEquals(RevCollator.testCollateRevIds("1-foo", "2-bar"), -1);
        // Multi-digit:
        Assert.assertEquals(RevCollator.testCollateRevIds("123-bar", "456-foo"), -1);
        Assert.assertEquals(RevCollator.testCollateRevIds("456-foo", "123-bar"), 1);
        Assert.assertEquals(RevCollator.testCollateRevIds("456-foo", "456-foo"), 0);
        Assert.assertEquals(RevCollator.testCollateRevIds("456-foo", "456-foofoo"), -1);
        // Different numbers of digits:
        Assert.assertEquals(RevCollator.testCollateRevIds("89-foo", "123-bar"), -1);
        Assert.assertEquals(RevCollator.testCollateRevIds("123-bar", "89-foo"), 1);
        // Edge cases:
        Assert.assertEquals(RevCollator.testCollateRevIds("123-", "89-"), 1);
        Assert.assertEquals(RevCollator.testCollateRevIds("123-a", "123-a"), 0);
        // Invalid rev IDs:
        Assert.assertEquals(RevCollator.testCollateRevIds("-a", "-b"), -1);
        Assert.assertEquals(RevCollator.testCollateRevIds("-", "-"), 0);
        Assert.assertEquals(RevCollator.testCollateRevIds("", ""), 0);
        Assert.assertEquals(RevCollator.testCollateRevIds("", "-b"), -1);
        Assert.assertEquals(RevCollator.testCollateRevIds("bogus", "yo"), -1);
        Assert.assertEquals(RevCollator.testCollateRevIds("bogus-x", "yo-y"), -1);
    }

}
