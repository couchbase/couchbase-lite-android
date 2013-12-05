package com.couchbase.lite.testapp.tests;

import android.test.AndroidTestCase;

import com.couchbase.lite.Database;
import com.couchbase.lite.internal.RevisionInternal;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Revisions extends AndroidTestCase {

    public void testParseRevID() {
        int num;
        String suffix;

        num = Database.parseRevIDNumber("1-utiopturoewpt");
        Assert.assertEquals(1, num);
        suffix = Database.parseRevIDSuffix("1-utiopturoewpt");
        Assert.assertEquals("utiopturoewpt", suffix);

        num = Database.parseRevIDNumber("321-fdjfdsj-e");
        Assert.assertEquals(321, num);
        suffix = Database.parseRevIDSuffix("321-fdjfdsj-e");
        Assert.assertEquals("fdjfdsj-e", suffix);

        num = Database.parseRevIDNumber("0-fdjfdsj-e");
        suffix = Database.parseRevIDSuffix("0-fdjfdsj-e");
        Assert.assertTrue(num == 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("-4-fdjfdsj-e");
        suffix = Database.parseRevIDSuffix("-4-fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("5_fdjfdsj-e");
        suffix = Database.parseRevIDSuffix("5_fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber(" 5-fdjfdsj-e");
        suffix = Database.parseRevIDSuffix(" 5-fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("7 -foo");
        suffix = Database.parseRevIDSuffix("7 -foo");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("7-");
        suffix = Database.parseRevIDSuffix("7-");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("7");
        suffix = Database.parseRevIDSuffix("7");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("eiuwtiu");
        suffix = Database.parseRevIDSuffix("eiuwtiu");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = Database.parseRevIDNumber("");
        suffix = Database.parseRevIDSuffix("");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
    }

    public void testCBLCompareRevIDs() {

        // Single Digit
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("1-foo", "1-foo") == 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("2-bar", "1-foo") > 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("1-foo", "2-bar") < 0);

        // Multi-digit:
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("123-bar", "456-foo") < 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("456-foo", "123-bar") > 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("456-foo", "456-foo") == 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("456-foo", "456-foofoo") < 0);

        // Different numbers of digits:
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("89-foo", "123-bar") < 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("123-bar", "89-foo") > 0);

        // Edge cases:
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("123-", "89-") > 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("123-a", "123-a") == 0);

        // Invalid rev IDs:
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("-a", "-b") < 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("-", "-") == 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("", "") == 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("", "-b") < 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("bogus", "yo") < 0);
        Assert.assertTrue(RevisionInternal.CBLCollateRevIDs("bogus-x", "yo-y") < 0);

    }

    public void testMakeRevisionHistoryDict() {
        List<RevisionInternal> revs = new ArrayList<RevisionInternal>();
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

        Map<String,Object> historyDict = Database.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);


        revs = new ArrayList<RevisionInternal>();
        revs.add(mkrev("4-jkl"));
        revs.add(mkrev("2-def"));

        expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("4-jkl");
        expectedSuffixes.add("2-def");
        expectedHistoryDict = new HashMap<String,Object>();
        expectedHistoryDict.put("ids", expectedSuffixes);

        historyDict = Database.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);


        revs = new ArrayList<RevisionInternal>();
        revs.add(mkrev("12345"));
        revs.add(mkrev("6789"));

        expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("12345");
        expectedSuffixes.add("6789");
        expectedHistoryDict = new HashMap<String,Object>();
        expectedHistoryDict.put("ids", expectedSuffixes);

        historyDict = Database.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);

    }

    private static RevisionInternal mkrev(String revID) {
        return new RevisionInternal("docid", revID, false, null);
    }

}
