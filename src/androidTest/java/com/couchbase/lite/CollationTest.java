package com.couchbase.lite;

import com.couchbase.lite.util.Log;
import com.couchbase.lite.android.SQLiteJsonCollator;
import com.couchbase.lite.android.SQLiteRevCollator;
import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.Assert;

import java.text.Collator;
import java.util.Locale;

public class CollationTest extends LiteTestCaseWithDB {
    public static String TAG = "Collation";

    private static final int kJsonCollator_Unicode = 0;
    private static final int kJsonCollator_Raw = 1;
    private static final int kJsonCollator_ASCII = 2;

    public void runBare() throws Throwable {
        // Run only for SQLite
        super.runBare(false);
    }

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
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "true", "false"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "false", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "null", "17"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "1", "1"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "1"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "0123.0"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "\"123\""));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"123\"", "\"1234\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "00000000000000000000000000000000000000000000000000123"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"a\"", "\"A\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"A\"", "\"aa\""));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"B\"", "\"aa\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"~\"", "\"A\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"_\"", "\"A\""));
    }


    public void testCollateASCII() {
        int mode = kJsonCollator_ASCII;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "true", "false"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "false", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "null", "17"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "1"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "0123.0"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "123", "\"123\""));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"B\"", "\"a\""));
    }

    public void testCollateRaw() {
        int mode = kJsonCollator_Raw;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "false", "17"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "false", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "null", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[\"A\"]", "\"A\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[\"b\"]", "[\"b\",\"c\",\"a\"]"));
    }

    public void testCollateArrays() {
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[]", "\"foo\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[]", "[]"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[true]", "[true]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[false]", "[null]"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[]", "[null]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[123]", "[45]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[123]", "[45,67]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[123.4,\"wow\"]", "[123.40,789]"));
    }

    public void testCollateNestedArray() {
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[[]]", "[]"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, "[1,[2,3],4]", "[1,[2,3.1],4,5,6]"));
    }

    public void testCollateJapaneseStrings() {

        int mode = kJsonCollator_Unicode;

        // en_US
        try {
            Collator c = Collator.getInstance(new Locale("en_US"));
            Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("あ"), encode("い")));
            Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("い"), encode("あ")));
            Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("あ"), encode("あ")));
            Assert.assertEquals(c.compare("カー", "カア"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("カー"), encode("カア")));
            Assert.assertEquals(c.compare("鞍", "倉"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("鞍"), encode("倉")));
            Assert.assertEquals(c.compare("鞍", "蔵"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("鞍"), encode("蔵")));
            Assert.assertEquals(c.compare("倉", "蔵"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("倉"), encode("蔵")));
            Assert.assertEquals(c.compare("倉", "鞍"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("倉"), encode("鞍")));
            Assert.assertEquals(c.compare("蔵", "鞍"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("蔵"), encode("鞍")));
            Assert.assertEquals(c.compare("蔵", "倉"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("蔵"), encode("倉")));
        }finally {
            SQLiteJsonCollator.releaseICU();
            SQLiteJsonCollator.setLocale("en_US");
        }

        // ja
        try{
            Collator c1 = Collator.getInstance(new Locale("ja"));
            SQLiteJsonCollator.releaseICU();
            SQLiteJsonCollator.setLocale("ja");
            Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("あ"), encode("い")));
            Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("い"), encode("あ")));
            Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("あ"), encode("あ")));
            Assert.assertEquals(c1.compare("カー", "カア"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("カー"), encode("カア")));
            Assert.assertEquals(c1.compare("鞍", "倉"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("鞍"), encode("倉")));
            Assert.assertEquals(c1.compare("鞍", "蔵"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("鞍"), encode("蔵")));
            Assert.assertEquals(c1.compare("倉", "蔵"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("倉"), encode("蔵")));
            Assert.assertEquals(c1.compare("倉", "鞍"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("倉"), encode("鞍")));
            Assert.assertEquals(c1.compare("蔵", "鞍"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("蔵"), encode("鞍")));
            Assert.assertEquals(c1.compare("蔵", "倉"), SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("蔵"), encode("倉")));
        }finally {
            SQLiteJsonCollator.releaseICU();
            SQLiteJsonCollator.setLocale("en_US");
        }
    }

    public void testCollateUnicodeStrings() {
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(0, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("fr�d"), encode("fr�d")));
        // Assert.assertEquals(1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("�m�"), encode("omo")));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("\t"), encode(" ")));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollateJSONWrapper(mode, encode("\001"), encode(" ")));

    }

    public void testConvertEscape() {
        Assert.assertEquals('\\', SQLiteJsonCollator.testEscape("\\\\"));
        Assert.assertEquals('\t', SQLiteJsonCollator.testEscape("\\t"));
        Assert.assertEquals('E', SQLiteJsonCollator.testEscape("\\u0045"));
        Assert.assertEquals(1, SQLiteJsonCollator.testEscape("\\u0001"));
        Assert.assertEquals(0, SQLiteJsonCollator.testEscape("\\u0000"));
    }

    public void testDigitToInt() {
        Assert.assertEquals(1, SQLiteJsonCollator.testDigitToInt('1'));
        Assert.assertEquals(7, SQLiteJsonCollator.testDigitToInt('7'));
        Assert.assertEquals(0xc, SQLiteJsonCollator.testDigitToInt('c'));
        Assert.assertEquals(0xc, SQLiteJsonCollator.testDigitToInt('C'));
    }

    public void testCollateRevIds() {
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("1-foo", "1-foo"), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("2-bar", "1-foo"), 1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("1-foo", "2-bar"), -1);
        // Multi-digit:
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("123-bar", "456-foo"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("456-foo", "123-bar"), 1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("456-foo", "456-foo"), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("456-foo", "456-foofoo"), -1);
        // Different numbers of digits:
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("89-foo", "123-bar"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("123-bar", "89-foo"), 1);
        // Edge cases:
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("123-", "89-"), 1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("123-a", "123-a"), 0);
        // Invalid rev IDs:
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("-a", "-b"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("-", "-"), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("", ""), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("", "-b"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("bogus", "yo"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollateRevIds("bogus-x", "yo-y"), -1);
    }

}
