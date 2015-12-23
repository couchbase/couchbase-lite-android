package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.support.RevisionUtils;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RevisionsTest extends LiteTestCaseWithDB {

    public void testParseRevID() {
        int num;
        String suffix;

        num = RevisionUtils.parseRevIDNumber("1-utiopturoewpt");
        Assert.assertEquals(1, num);
        suffix = RevisionUtils.parseRevIDSuffix("1-utiopturoewpt");
        Assert.assertEquals("utiopturoewpt", suffix);

        num = RevisionUtils.parseRevIDNumber("321-fdjfdsj-e");
        Assert.assertEquals(321, num);
        suffix = RevisionUtils.parseRevIDSuffix("321-fdjfdsj-e");
        Assert.assertEquals("fdjfdsj-e", suffix);

        num = RevisionUtils.parseRevIDNumber("0-fdjfdsj-e");
        suffix = RevisionUtils.parseRevIDSuffix("0-fdjfdsj-e");
        Assert.assertTrue(num == 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("-4-fdjfdsj-e");
        suffix = RevisionUtils.parseRevIDSuffix("-4-fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("5_fdjfdsj-e");
        suffix = RevisionUtils.parseRevIDSuffix("5_fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber(" 5-fdjfdsj-e");
        suffix = RevisionUtils.parseRevIDSuffix(" 5-fdjfdsj-e");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("7 -foo");
        suffix = RevisionUtils.parseRevIDSuffix("7 -foo");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("7-");
        suffix = RevisionUtils.parseRevIDSuffix("7-");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("7");
        suffix = RevisionUtils.parseRevIDSuffix("7");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("eiuwtiu");
        suffix = RevisionUtils.parseRevIDSuffix("eiuwtiu");
        Assert.assertTrue(num < 0 || (suffix.length() == 0));
        num = RevisionUtils.parseRevIDNumber("");
        suffix = RevisionUtils.parseRevIDSuffix("");
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
        Map<String, Object> expectedHistoryDict = new HashMap<String, Object>();
        expectedHistoryDict.put("start", 4);
        expectedHistoryDict.put("ids", expectedSuffixes);

        Map<String, Object> historyDict = RevisionUtils.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);


        revs = new ArrayList<RevisionInternal>();
        revs.add(mkrev("4-jkl"));
        revs.add(mkrev("2-def"));

        expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("4-jkl");
        expectedSuffixes.add("2-def");
        expectedHistoryDict = new HashMap<String, Object>();
        expectedHistoryDict.put("ids", expectedSuffixes);

        historyDict = RevisionUtils.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);


        revs = new ArrayList<RevisionInternal>();
        revs.add(mkrev("12345"));
        revs.add(mkrev("6789"));

        expectedSuffixes = new ArrayList<String>();
        expectedSuffixes.add("12345");
        expectedSuffixes.add("6789");
        expectedHistoryDict = new HashMap<String, Object>();
        expectedHistoryDict.put("ids", expectedSuffixes);

        historyDict = RevisionUtils.makeRevisionHistoryDict(revs);
        Assert.assertEquals(expectedHistoryDict, historyDict);

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/164
     */
    public void testRevisionIdDifferentRevisions() throws Exception {

        // two revisions with different json should have different rev-id's
        // because their content will have a different hash (even though
        // they have the same generation number)

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        Document doc = database.createDocument();
        UnsavedRevision newRev = doc.createRevision();
        newRev.setUserProperties(properties);
        SavedRevision rev1 = newRev.save();

        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);

        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);

        assertNotSame(rev2a.getId(), rev2b.getId());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/164
     */
    public void testRevisionIdEquivalentRevisions() throws Exception {

        // This test causes crash with CBL Java on OSX
        // TODO: Github Ticket: https://github.com/couchbase/couchbase-lite-java/issues/55
        if (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {
            // two revisions with the same content and the same json
            // should have the exact same revision id, because their content
            // will have an identical hash

            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("testName", "testCreateRevisions");
            properties.put("tag", 1337);

            Map<String, Object> properties2 = new HashMap<String, Object>();
            properties2.put("testName", "testCreateRevisions");
            properties2.put("tag", 1338);

            Document doc = database.createDocument();
            UnsavedRevision newRev = doc.createRevision();
            newRev.setUserProperties(properties);
            SavedRevision rev1 = newRev.save();

            UnsavedRevision newRev2a = rev1.createRevision();
            newRev2a.setUserProperties(properties2);
            SavedRevision rev2a = newRev2a.save();

            UnsavedRevision newRev2b = rev1.createRevision();
            newRev2b.setUserProperties(properties2);
            SavedRevision rev2b = newRev2b.save(true);

            assertEquals(rev2a.getId(), rev2b.getId());
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/106
     */
    public void testResolveConflict() throws Exception {

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        // Create a conflict on purpose
        Document doc = database.createDocument();

        UnsavedRevision newRev1 = doc.createRevision();
        newRev1.setUserProperties(properties);
        SavedRevision rev1 = newRev1.save();

        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);

        SavedRevision winningRev = null;
        SavedRevision losingRev = null;
        if (doc.getCurrentRevisionId().equals(rev2a.getId())) {
            winningRev = rev2a;
            losingRev = rev2b;
        } else {
            winningRev = rev2b;
            losingRev = rev2a;
        }

        assertEquals(2, doc.getConflictingRevisions().size());
        assertEquals(2, doc.getLeafRevisions().size());

        // let's manually choose the losing rev as the winner.  First, delete winner, which will
        // cause losing rev to be the current revision.
        SavedRevision deleteRevision = winningRev.deleteDocument();

        List<SavedRevision> conflictingRevisions = doc.getConflictingRevisions();
        assertEquals(1, conflictingRevisions.size());
        assertEquals(2, doc.getLeafRevisions().size());

        assertEquals(3, deleteRevision.getGeneration());
        assertEquals(losingRev.getId(), doc.getCurrentRevision().getId());

        // Finally create a new revision rev3 based on losing rev
        SavedRevision rev3 = createRevisionWithRandomProps(losingRev, true);

        assertEquals(rev3.getId(), doc.getCurrentRevisionId());

        List<SavedRevision> conflictingRevisions1 = doc.getConflictingRevisions();
        assertEquals(1, conflictingRevisions1.size());
        assertEquals(2, doc.getLeafRevisions().size());

    }

    public void testCorrectWinningRevisionTiebreaker() throws Exception {

        // Create a conflict on purpose
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);

        // the tiebreaker will happen based on which rev hash has lexicographically higher sort order
        SavedRevision expectedWinner = null;
        if (rev2a.getId().compareTo(rev2b.getId()) > 0) {
            expectedWinner = rev2a;
        } else if (rev2a.getId().compareTo(rev2b.getId()) < 0) {
            expectedWinner = rev2b;
        }

        RevisionInternal revFound = database.getDocument(doc.getId(), null, true);
        assertEquals(expectedWinner.getId(), revFound.getRevID());

    }

    public void testCorrectWinningRevisionLongerBranch() throws Exception {

        // Create a conflict on purpose
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);
        SavedRevision rev3b = createRevisionWithRandomProps(rev2b, true);

        // rev3b should be picked as the winner since it has a longer branch
        SavedRevision expectedWinner = rev3b;

        RevisionInternal revFound = database.getDocument(doc.getId(), null, true);
        assertEquals(expectedWinner.getId(), revFound.getRevID());

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/135
     */
    public void testCorrectWinningRevisionHighRevisionNumber() throws Exception {

        // Create a conflict on purpose
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);
        SavedRevision rev3b = createRevisionWithRandomProps(rev2b, true);
        SavedRevision rev4b = createRevisionWithRandomProps(rev3b, true);
        SavedRevision rev5b = createRevisionWithRandomProps(rev4b, true);
        SavedRevision rev6b = createRevisionWithRandomProps(rev5b, true);
        SavedRevision rev7b = createRevisionWithRandomProps(rev6b, true);
        SavedRevision rev8b = createRevisionWithRandomProps(rev7b, true);
        SavedRevision rev9b = createRevisionWithRandomProps(rev8b, true);
        SavedRevision rev10b = createRevisionWithRandomProps(rev9b, true);

        RevisionInternal revFound = database.getDocument(doc.getId(), null, true);
        assertEquals(rev10b.getId(), revFound.getRevID());

    }

    public void testDocumentChangeListener() throws Exception {

        Document doc = database.createDocument();
        final CountDownLatch documentChanged = new CountDownLatch(1);
        doc.addChangeListener(new Document.ChangeListener() {
            @Override
            public void changed(Document.ChangeEvent event) {
                DocumentChange docChange = event.getChange();
                String msg = "New revision added: %s.  Conflict: %s";
                msg = String.format(msg, docChange.getAddedRevision(), docChange.isConflict());
                Log.d(TAG, msg);
                documentChanged.countDown();
            }
        });
        doc.createRevision().save();
        boolean success = documentChanged.await(30, TimeUnit.SECONDS);
        assertTrue(success);

    }

    public void testRevisionSequence() throws CouchbaseLiteException {
        Document doc = database.createDocument();
        UnsavedRevision unsavedRev = doc.createRevision();

        // An unsaved revision has no sequence number
        assertEquals(0, unsavedRev.getSequence());
        // A new document has no parent and so there is no parent sequence number
        assertNull(unsavedRev.getParentId());
        assertEquals(0, unsavedRev.getParentSequence());

        SavedRevision rev = unsavedRev.save();
        // The first revision of our database must have 1 has sequence number
        assertEquals(1, rev.getSequence());
        // Since it has no parent rev, the parent sequence number is 0
        assertNull(rev.getParentId());
        assertEquals(0, rev.getParentSequence());

        unsavedRev = doc.createRevision();
        assertEquals(0, unsavedRev.getSequence());
        assertEquals(1, unsavedRev.getParentSequence());

        rev = unsavedRev.save();
        assertEquals(2, rev.getSequence());
        assertNotNull(rev.getParentId());
        assertEquals(1, rev.getParentSequence());
    }

    private static RevisionInternal mkrev(String revID) {
        return new RevisionInternal("docid", revID, false);
    }

    // https://github.com/couchbase/couchbase-lite-java-core/issues/878:
    public void failingTestGenerateRevisionID() throws Exception {
        Map <String, Object> properties = new HashMap<String, Object>();
        properties.put("_id", UUID.randomUUID());
        properties.put("foo", "bar");
        byte[] json1 = RevisionUtils.asCanonicalJSON(properties);
        assertNotNull(json1);
        assertEquals(13, json1.length);
        String revID1 = RevisionUtils.generateRevID(json1, false, null);
        assertEquals("1-aaa6c063924b64a141c98820efcc0022", revID1);

        properties = new HashMap<String, Object>(properties);
        properties.put("_rev", revID1);
        properties.put("tag", "1");
        byte[] json2 = RevisionUtils.asCanonicalJSON(properties);
        assertNotNull(json2);
        assertEquals(23, json2.length);
        String revID2 = RevisionUtils.generateRevID(json2, false, revID1);
        assertEquals("2-cb1210b093cbdcdf5a353df2a898c891", revID2);

        properties = new HashMap<String, Object>(properties);
        properties.put("tag", "2");
        byte[] json3 = RevisionUtils.asCanonicalJSON(properties);
        assertNotNull(json3);
        assertEquals(23, json3.length);
        String revID3 = RevisionUtils.generateRevID(json3, false, revID1);
        assertEquals("2-dc83321a829c8ae2849492b05478c9ed", revID3);
    }
}
