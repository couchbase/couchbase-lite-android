package com.couchbase.lite;

import com.couchbase.lite.storage.SQLiteJsonCollator;
import com.couchbase.lite.storage.SQLiteRevCollator;
import com.couchbase.lite.util.Log;
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
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "true", "false"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "false", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "null", "17"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "1", "1"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "123", "1"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "123", "0123.0"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "123", "\"123\""));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"123\"", "\"1234\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "123", "00000000000000000000000000000000000000000000000000123"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"a\"", "\"A\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"A\"", "\"aa\""));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "\"B\"", "\"aa\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"~\"", "\"A\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"_\"", "\"A\""));
    }
    
    public void testCollateASCII() {
        int mode = kJsonCollator_ASCII;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "true", "false"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "false", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "null", "17"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "123", "1"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "123", "0123.0"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "123", "\"123\""));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"B\"", "\"a\""));
    }

    public void testCollateRaw() {
        int mode = kJsonCollator_Raw;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "false", "17"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "false", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "null", "true"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "[\"A\"]", "\"A\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "[\"b\"]", "[\"b\",\"c\",\"a\"]"));
    }

    public void testCollateArrays() {
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "[]", "\"foo\""));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "[]", "[]"));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, "[true]", "[true]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "[false]", "[null]"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "[]", "[null]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "[123]", "[45]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "[123]", "[45,67]"));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "[123.4,\"wow\"]", "[123.40,789]"));
    }

    public void testCollateNestedArray() {
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, "[[]]", "[]"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, "[1,[2,3],4]", "[1,[2,3.1],4,5,6]"));
    }

    public void testCollateJapaneseStrings() {
        int mode = kJsonCollator_Unicode;

        // en_US
        Collator c = Collator.getInstance(new Locale("en_US"));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, encode("あ"), encode("い")));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, encode("い"), encode("あ")));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, encode("あ"), encode("あ")));
        Assert.assertEquals(c.compare("カー", "カア"), SQLiteJsonCollator.testCollate(mode, encode("カー"), encode("カア")));
        Assert.assertEquals(c.compare("鞍", "倉"), SQLiteJsonCollator.testCollate(mode, encode("鞍"), encode("倉")));
        Assert.assertEquals(c.compare("鞍", "蔵"), SQLiteJsonCollator.testCollate(mode, encode("鞍"), encode("蔵")));
        Assert.assertEquals(c.compare("倉", "蔵"), SQLiteJsonCollator.testCollate(mode, encode("倉"), encode("蔵")));
        Assert.assertEquals(c.compare("倉", "鞍"), SQLiteJsonCollator.testCollate(mode, encode("倉"), encode("鞍")));
        Assert.assertEquals(c.compare("蔵", "鞍"), SQLiteJsonCollator.testCollate(mode, encode("蔵"), encode("鞍")));
        Assert.assertEquals(c.compare("蔵", "倉"), SQLiteJsonCollator.testCollate(mode, encode("蔵"), encode("倉")));

        // ja
        Locale locale = new Locale("ja");
        String localeStr = locale.toString();
        Collator c1 = Collator.getInstance(locale);
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, localeStr, encode("あ"), encode("い")));
        Assert.assertEquals(1, SQLiteJsonCollator.testCollate(mode, localeStr, encode("い"), encode("あ")));
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, localeStr, encode("あ"), encode("あ")));
        Assert.assertEquals(c1.compare("カー", "カア"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("カー"), encode("カア")));
        Assert.assertEquals(c1.compare("鞍", "倉"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("鞍"), encode("倉")));
        Assert.assertEquals(c1.compare("鞍", "蔵"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("鞍"), encode("蔵")));
        Assert.assertEquals(c1.compare("倉", "蔵"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("倉"), encode("蔵")));
        Assert.assertEquals(c1.compare("倉", "鞍"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("倉"), encode("鞍")));
        Assert.assertEquals(c1.compare("蔵", "鞍"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("蔵"), encode("鞍")));
        Assert.assertEquals(c1.compare("蔵", "倉"), SQLiteJsonCollator.testCollate(mode, localeStr, encode("蔵"), encode("倉")));
    }

    public void testCollateUnicodeStrings() {
        int mode = kJsonCollator_Unicode;
        Assert.assertEquals(0, SQLiteJsonCollator.testCollate(mode, encode("fr�d"), encode("fr�d")));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, encode("\t"), encode(" ")));
        Assert.assertEquals(-1, SQLiteJsonCollator.testCollate(mode, encode("\001"), encode(" ")));
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
        Assert.assertEquals(SQLiteRevCollator.testCollate("1-foo", "1-foo"), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollate("2-bar", "1-foo"), 1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("1-foo", "2-bar"), -1);
        // Multi-digit:
        Assert.assertEquals(SQLiteRevCollator.testCollate("123-bar", "456-foo"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("456-foo", "123-bar"), 1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("456-foo", "456-foo"), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollate("456-foo", "456-foofoo"), -1);
        // Different numbers of digits:
        Assert.assertEquals(SQLiteRevCollator.testCollate("89-foo", "123-bar"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("123-bar", "89-foo"), 1);
        // Edge cases:
        Assert.assertEquals(SQLiteRevCollator.testCollate("123-", "89-"), 1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("123-a", "123-a"), 0);
        // Invalid rev IDs:
        Assert.assertEquals(SQLiteRevCollator.testCollate("-a", "-b"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("-", "-"), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollate("", ""), 0);
        Assert.assertEquals(SQLiteRevCollator.testCollate("", "-b"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("bogus", "yo"), -1);
        Assert.assertEquals(SQLiteRevCollator.testCollate("bogus-x", "yo-y"), -1);
    }
}
