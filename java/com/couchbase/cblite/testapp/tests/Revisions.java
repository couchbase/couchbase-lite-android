package com.couchbase.cblite.testapp.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLRevision;

public class Revisions extends AndroidTestCase {

    public void testParseRevID() {
        int num;
        String suffix;

        num = CBLDatabase.parseRevIDNumber("1-utiopturoewpt");
        Assert.assertEquals(1, num);
        suffix = CBLDatabase.parseRevIDSuffix("1-utiopturoewpt");
        Assert.assertEquals("utiopturoewpt", suffix);

        num = CBLDatabase.parseRevIDNumber("321-fdjfdsj-e");
        Assert.assertEquals(321, num);
        suffix = CBLDatabase.parseRevIDSuffix("321-fdjfdsj-e");
        Assert.assertEquals("fdjfdsj-e", suffix);

        num = CBLDatabase.parseRevIDNumber("0-fdjfdsj-e");
        suffix = CBLDatabase.parseRevIDSuffix("0-fdjfdsj-e");
        Assert.assertTrue(num == 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("-4-fdjfdsj-e");
        suffix = CBLDatabase.parseRevIDSuffix("-4-fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("5_fdjfdsj-e");
        suffix = CBLDatabase.parseRevIDSuffix("5_fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber(" 5-fdjfdsj-e");
        suffix = CBLDatabase.parseRevIDSuffix(" 5-fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("7 -foo");
        suffix = CBLDatabase.parseRevIDSuffix("7 -foo");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("7-");
        suffix = CBLDatabase.parseRevIDSuffix("7-");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("7");
        suffix = CBLDatabase.parseRevIDSuffix("7");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("eiuwtiu");
        suffix = CBLDatabase.parseRevIDSuffix("eiuwtiu");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = CBLDatabase.parseRevIDNumber("");
        suffix = CBLDatabase.parseRevIDSuffix("");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
    }

    public void testMakeRevisionHistoryDict() {
        List<CBLRevision> revs = new ArrayList<CBLRevision>();
        revs.add(mkrev("4-jkl"));
        revs.add(mkrev("3-ghi"));
        revs.add(mkrev("2-def"));

        List<String> expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("jkl");
        expectedSuffixes.add("ghi");
        expectedSuffixes.add("def");
        Map<String,Object> expectedHistoryDict = new HashMap<String,Object>();
        expectedHistoryDict.put("start", 4);
        expectedHistoryDict.put("ids", expectedSuffixes);

        Map<String,Object> historyDict = CBLDatabase.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);


        revs = new ArrayList<CBLRevision>();
        revs.add(mkrev("4-jkl"));
        revs.add(mkrev("2-def"));

        expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("4-jkl");
        expectedSuffixes.add("2-def");
        expectedHistoryDict = new HashMap<String,Object>();
        expectedHistoryDict.put("ids", expectedSuffixes);

        historyDict = CBLDatabase.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);


        revs = new ArrayList<CBLRevision>();
        revs.add(mkrev("12345"));
        revs.add(mkrev("6789"));

        expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("12345");
        expectedSuffixes.add("6789");
        expectedHistoryDict = new HashMap<String,Object>();
        expectedHistoryDict.put("ids", expectedSuffixes);

        historyDict = CBLDatabase.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);

    }

    private static CBLRevision mkrev(String revID) {
        return new CBLRevision("docid", revID, false, null);
    }

}
