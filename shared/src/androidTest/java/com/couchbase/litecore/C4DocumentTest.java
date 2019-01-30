//
// C4DocumentTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.litecore;

import android.util.Log;

import com.couchbase.lite.Array;
import com.couchbase.litecore.utils.StopWatch;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.C4RevisionFlags.kRevKeepBody;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBadDocID;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorConflict;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class C4DocumentTest extends C4BaseTest implements C4Constants {
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    // - "Invalid docID"

    @Test
    public void testInvalidDocIDEmpty() throws LiteCoreException {
        _testInvalidDocID("");
    }

    @Test
    public void testInvalidDocIDTooLong() throws LiteCoreException {
        char[] str = new char[241];
        Arrays.fill(str, 'x');
        _testInvalidDocID(new String(str));
    }

    // NOTE: This is not allowed by Java
    //@Test
    public void testInvalidDocIDBadUTF8() throws LiteCoreException {
        //_testInvalidDocID("oops\x00oops");
    }

    @Test
    public void testInvalidDocIDControlCharacter() throws LiteCoreException {
        _testInvalidDocID("oops\noops");
    }

    private void _testInvalidDocID(String docID) throws LiteCoreException {
        db.beginTransaction();
        try {
            db.put(kFleeceBody, docID, 0, false, false,
                    new String[0], true, 0, 0);
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorBadDocID, e.code);
        } finally {
            db.endTransaction(false);
        }
    }

    // - "FleeceDocs"
    @Test
    public void testFleeceDocs() throws LiteCoreException, IOException {
        importJSONLines("names_100.json");
    }

    // - "Document PossibleAncestors"
    @Test
    public void testPossibleAncestors() throws LiteCoreException {
        if (!isRevTrees()) return;

        createRev(kDocID, kRevID, kFleeceBody);
        createRev(kDocID, kRev2ID, kFleeceBody);
        createRev(kDocID, kRev3ID, kFleeceBody);

        C4Document doc = db.get(kDocID, true);
        assertNotNull(doc);

        String newRevID = "3-f00f00";
        assertTrue(doc.selectFirstPossibleAncestorOf(newRevID));
        assertEquals(kRev2ID, doc.getSelectedRevID());
        assertTrue(doc.selectNextPossibleAncestorOf(newRevID));
        assertEquals(kRevID, doc.getSelectedRevID());
        assertFalse(doc.selectNextPossibleAncestorOf(newRevID));

        newRevID = "2-f00f00";
        assertTrue(doc.selectFirstPossibleAncestorOf(newRevID));
        assertEquals(kRevID, doc.getSelectedRevID());
        assertFalse(doc.selectNextPossibleAncestorOf(newRevID));

        newRevID = "1-f00f00";
        assertFalse(doc.selectFirstPossibleAncestorOf(newRevID));

        doc.free();
    }

    // - "Document CreateVersionedDoc"
    @Test
    public void testCreateVersionedDoc() throws LiteCoreException {
        // Try reading doc with mustExist=true, which should fail:
        try {
            C4Document doc = db.get(kDocID, true);
            doc.free();
            fail();
        } catch (LiteCoreException lce) {
            assertEquals(LiteCoreDomain, lce.domain);
            assertEquals(LiteCoreError.kC4ErrorNotFound, lce.code);
        }

        // Now get the doc with mustExist=false, which returns an empty doc:
        C4Document doc = db.get(kDocID, false);
        assertNotNull(doc);
        assertEquals(0, doc.getFlags());
        assertEquals(kDocID, doc.getDocID());
        assertNull(doc.getRevID());
        assertNull(doc.getSelectedRevID());
        doc.free();

        boolean commit = false;
        db.beginTransaction();
        try {
            doc = db.put(kFleeceBody, kDocID, 0, true,
                    false, new String[]{kRevID}, true,
                    0, 0);
            assertNotNull(doc);
            assertEquals(kRevID, doc.getRevID());
            assertEquals(kRevID, doc.getSelectedRevID());
            assertTrue(Arrays.equals(kFleeceBody, doc.getSelectedBody()));
            doc.free();
            commit = true;
        } finally {
            db.endTransaction(commit);
        }

        // Reload the doc:
        doc = db.get(kDocID, true);
        assertNotNull(doc);
        assertEquals(C4DocumentFlags.kDocExists, doc.getFlags());
        assertEquals(kDocID, doc.getDocID());
        assertEquals(kRevID, doc.getRevID());
        assertEquals(kRevID, doc.getSelectedRevID());
        assertEquals(1, doc.getSelectedSequence());
        assertTrue(Arrays.equals(kFleeceBody, doc.getSelectedBody()));

        doc.free();

        // Get the doc by its sequence:
        doc = db.getBySequence(1);
        assertNotNull(doc);
        assertEquals(C4DocumentFlags.kDocExists, doc.getFlags());
        assertEquals(kDocID, doc.getDocID());
        assertEquals(kRevID, doc.getRevID());
        assertEquals(kRevID, doc.getSelectedRevID());
        assertEquals(1, doc.getSelectedSequence());
        assertTrue(Arrays.equals(kFleeceBody, doc.getSelectedBody()));
        doc.free();
    }

    // - "Document CreateMultipleRevisions"
    @Test
    public void testCreateMultipleRevisions() throws LiteCoreException {
        byte[] kFleeceBody2 = json2fleece("{'ok':'go'}");
        byte[] kFleeceBody3 = json2fleece("{'ubu':'roi'}");

        createRev(kDocID, kRevID, kFleeceBody);
        createRev(kDocID, kRev2ID, kFleeceBody2, kRevKeepBody);
        createRev(kDocID, kRev2ID, kFleeceBody2); // test redundant insert

        // Reload the doc:
        C4Document doc = db.get(kDocID, true);
        assertNotNull(doc);
        assertEquals(C4DocumentFlags.kDocExists, doc.getFlags());
        assertEquals(kDocID, doc.getDocID());
        assertEquals(kRev2ID, doc.getRevID());
        assertEquals(kRev2ID, doc.getSelectedRevID());
        assertEquals(2, doc.getSelectedSequence());
        assertTrue(Arrays.equals(kFleeceBody2, doc.getSelectedBody()));

        if (isRevTrees()) {
            // Select 1st revision:
            assertTrue(doc.selectParentRevision());
            assertEquals(kRevID, doc.getSelectedRevID());
            assertEquals(1, doc.getSelectedSequence());
            assertNull(doc.getSelectedBody());
            assertFalse(doc.hasRevisionBody());
            assertFalse(doc.selectParentRevision());
            doc.free();

            // Add a 3rd revision:
            createRev(kDocID, kRev3ID, kFleeceBody3);

            // Revision 2 should keep its body due to the kRevKeepBody flag:
            doc = db.get(kDocID, true);
            assertNotNull(doc);
            assertTrue(doc.selectParentRevision());
            assertEquals(kDocID, doc.getDocID());
            assertEquals(kRev3ID, doc.getRevID());
            assertEquals(kRev2ID, doc.getSelectedRevID());
            assertEquals(2, doc.getSelectedSequence());
            assertTrue(Arrays.equals(kFleeceBody2, doc.getSelectedBody()));
            assertTrue(doc.getSelectedFlags() == kRevKeepBody);
            doc.free();

            // Purge doc
            boolean commit = false;
            db.beginTransaction();
            try {
                doc = db.get(kDocID, true);
                int nPurged = doc.purgeRevision(kRev3ID);
                assertEquals(3, nPurged);
                doc.save(20);
                commit = true;
            } finally {
                db.endTransaction(commit);
            }
        }
        doc.free();
    }

    // - Document Purge
    @Test
    public void testDocumentPurge() {
        // TODO
    }

    // - "Document maxRevTreeDepth"
    @Test
    public void testMaxRevTreeDepth() throws LiteCoreException {
        if (isRevTrees()) {
            // NOTE: c4db_getMaxRevTreeDepth and c4db_setMaxRevTreeDepth are not supported by JNI.
            assertEquals(20, db.getMaxRevTreeDepth());
            db.setMaxRevTreeDepth(30);
            assertEquals(30, db.getMaxRevTreeDepth());
            reopenDB();
            assertEquals(30, db.getMaxRevTreeDepth());
        }

        final int kNumRevs = 10000;
        StopWatch st = new StopWatch();
        C4Document doc = db.get(kDocID, false);
        assertNotNull(doc);
        boolean commit = false;
        db.beginTransaction();
        try {
            for (int i = 0; i < kNumRevs; i++) {
                String[] history = {doc.getRevID()};
                C4Document savedDoc = db.put(kFleeceBody, doc.getDocID(), 0,
                        false, false,
                        history, true, 30, 0);
                assertNotNull(savedDoc);
                doc.free();
                doc = savedDoc;
            }
            commit = true;
        } finally {
            db.endTransaction(commit);
        }
        Log.i(TAG, String.format(Locale.ENGLISH, "Created %d revisions in %.3f ms",
                kNumRevs, st.getElapsedTimeMillis()));

        // Check rev tree depth:
        int nRevs = 0;
        assertTrue(doc.selectCurrentRevision());
        do {
            if (isRevTrees())
                // NOTE: c4rev_getGeneration is not supported.
                ;
            nRevs++;
        } while (doc.selectParentRevision());
        Log.i(TAG, String.format(Locale.ENGLISH, "Document rev tree depth is %d", nRevs));
        if (isRevTrees())
            assertEquals(30, nRevs);
        doc.free();
    }

    // - "Document GetForPut"
    // TODO: c4doc_getForPut() is not directly used from Java. Will implement testGetForPut() later.
    // @Test
    public void testGetForPut() throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            // Creating doc given ID:

            // Creating doc, no ID:

            // Delete with no revID given

            // Adding new rev of nonexistent doc:

            // Adding new rev of existing doc:

            // Adding new rev, with nonexistent parent:

            // Conflict -- try & fail to update non-current rev:

            // Conflict -- force an update of non-current rev:

            // Deleting the doc:

            // Actually delete it:

            // Re-creating the doc (no revID given):
            commit = true;
        } finally {
            db.endTransaction(commit);
        }
    }

    // - "Document Put"
    @Test
    public void testPut() throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            // Creating doc given ID:
            C4Document doc = db.put(kFleeceBody, kDocID, 0,
                    false, false,
                    new String[0], true, 0, 0);
            assertNotNull(doc);
            assertEquals(kDocID, doc.getDocID());
            String kExpectedRevID = isRevTrees() ?
                    "1-d41ffed6be0529153fd3d27b3218d9052e1c2b40" :
                    "1@*";
            assertEquals(kExpectedRevID, doc.getRevID());
            assertEquals(C4DocumentFlags.kDocExists, doc.getFlags());
            assertEquals(kExpectedRevID, doc.getSelectedRevID());
            doc.free();

            // Update doc:
            String[] history = {kExpectedRevID};

            doc = db.put(json2fleece("{'ok':'go'}"), kDocID, 0,
                    false, false, history, true,
                    0, 0);
            assertNotNull(doc);
            // NOTE: With current JNI binding, unable to check commonAncestorIndex value
            String kExpectedRevID2 = isRevTrees() ?
                    "2-e388dff9126ba5a0d93c7af05bc72f3cdf450598" :
                    "2@*";
            assertEquals(kExpectedRevID2, doc.getRevID());
            assertEquals(C4DocumentFlags.kDocExists, doc.getFlags());
            assertEquals(kExpectedRevID2, doc.getSelectedRevID());
            doc.free();

            // Insert existing rev that conflicts:
            String kConflictRevID = isRevTrees() ?
                    "2-deadbeef" :
                    "1@binky";
            String[] history2 = {kConflictRevID, kExpectedRevID};
            doc = db.put(json2fleece("{'from':'elsewhere'}"), kDocID, 0,
                    true, false,
                    history2, true, 0, 1);
            assertNotNull(doc);
            // NOTE: With current JNI binding, unable to check commonAncestorIndex value
            assertEquals(kExpectedRevID2, doc.getRevID());
            assertEquals(C4DocumentFlags.kDocExists | C4DocumentFlags.kDocConflicted,
                    doc.getFlags());
            assertEquals(kConflictRevID, doc.getSelectedRevID());
            doc.free();

            commit = true;
        } finally {
            db.endTransaction(commit);
        }
    }

    // - "Document Update"
    @Test
    public void testDocumentUpdate() throws LiteCoreException {
        C4Document doc = null;

        boolean commit = false;
        db.beginTransaction();
        try {
            doc = db.create(kDocID, kFleeceBody, 0);
            assertNotNull(doc);
            commit = true;
        } finally {
            db.endTransaction(commit);
        }

        String kExpectedRevID = isRevTrees() ? "1-d41ffed6be0529153fd3d27b3218d9052e1c2b40" : "1@*";
        assertEquals(kExpectedRevID, doc.getRevID());
        assertTrue(doc.exists());
        assertEquals(kExpectedRevID, doc.getSelectedRevID());
        assertEquals(kDocID, doc.getDocID());

        // Read the doc into another C4Document:
        C4Document doc2 = db.get(kDocID, false);
        assertNotNull(doc2);
        assertEquals(kExpectedRevID, doc2.getRevID());

        commit = false;
        db.beginTransaction();
        try {
            C4Document updatedDoc = doc.update(json2fleece("{'ok':'go'}"), 0);
            assertNotNull(updatedDoc);
            assertEquals(kExpectedRevID, doc.getSelectedRevID());
            assertEquals(kExpectedRevID, doc.getRevID());
            doc.free();
            doc = updatedDoc;
            commit = true;
        } finally {
            db.endTransaction(commit);
        }

        String kExpectedRev2ID = isRevTrees() ? "2-e388dff9126ba5a0d93c7af05bc72f3cdf450598" : "2@*";
        assertEquals(kExpectedRev2ID, doc.getRevID());
        assertTrue(doc.exists());
        assertEquals(kExpectedRev2ID, doc.getSelectedRevID());
        assertEquals(kDocID, doc.getDocID());

        // Now try to update the other C4Document, which will fail:
        commit = false;
        db.beginTransaction();
        try {
            doc2.update(json2fleece("{'ok':'no way'}"), 0);
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorConflict, e.code);
        } finally {
            db.endTransaction(commit);
        }

        // Try to create a new doc with the same ID, which will fail:
        commit = false;
        db.beginTransaction();
        try {
            db.create(kDocID, json2fleece("{'ok':'no way'}"), 0);
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorConflict, e.code);
        } finally {
            db.endTransaction(commit);
        }

        if (doc != null) doc.free();
        if (doc2 != null) doc2.free();
    }

    // - "Document Conflict"
    interface Verification {
        void verify(C4Document doc) throws LiteCoreException;
    }

    @Test
    public void testDocumentConflictMerge4Win() throws LiteCoreException {
        _testDocumentConflict(new Verification() {
            @Override
            public void verify(C4Document doc) throws LiteCoreException {
                doc.resolveConflict("4-dddd", "3-aaaaaa", "{\"merged\":true}".getBytes(), 0);
                assertTrue(doc.selectCurrentRevision());
                assertEquals("5-940fe7e020dbf8db0f82a5d764870c4b6c88ae99", doc.getSelectedRevID());
                assertTrue(Arrays.equals("{\"merged\":true}".getBytes(), doc.getSelectedBody()));
                assertTrue(doc.selectParentRevision());
                assertEquals("4-dddd", doc.getSelectedRevID());
            }
        });
    }

    @Test
    public void testDocumentConflictMerge3Win() throws LiteCoreException {
        _testDocumentConflict(new Verification() {
            @Override
            public void verify(C4Document doc) throws LiteCoreException {
                doc.resolveConflict("3-aaaaaa", "4-dddd", "{\"merged\":true}".getBytes(), 0);
                assertTrue(doc.selectCurrentRevision());
                assertEquals("4-333ee0677b5f1e1e5064b050d417a31d2455dc30", doc.getSelectedRevID());
                assertTrue(Arrays.equals("{\"merged\":true}".getBytes(), doc.getSelectedBody()));
                assertTrue(doc.selectParentRevision());
                assertEquals("3-aaaaaa", doc.getSelectedRevID());
            }
        });
    }

    private void _testDocumentConflict(Verification verification) throws LiteCoreException {
        if (isVersionVectors())
            return;

        final byte[] kFleeceBody2 = json2fleece("{'ok':'go'}");
        final byte[] kFleeceBody3 = json2fleece("{'ubu':'roi'}");

        createRev(kDocID, kRevID, kFleeceBody);
        createRev(kDocID, kRev2ID, kFleeceBody2, kRevKeepBody);
        createRev(kDocID, "3-aaaaaa", kFleeceBody3);

        boolean commit = false;
        db.beginTransaction();
        try {
            // "Pull" a conflicting revision:
            String[] history = {"4-dddd", "3-ababab", kRev2ID};
            C4Document doc = db.put(kFleeceBody3, kDocID, 0, true,
                    false, history, true, 0, 0);
            assertNotNull(doc);

            // Now check the common ancestor algorithm:
            assertTrue(doc.selectCommonAncestorRevision("3-aaaaaa", "4-dddd"));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("4-dddd", "3-aaaaaa"));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("3-ababab", "3-aaaaaa"));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("3-aaaaaa", "3-ababab"));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision(kRev2ID, "3-aaaaaa"));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("3-aaaaaa", kRev2ID));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision(kRev2ID, kRev2ID));
            assertEquals(kRev2ID, doc.getSelectedRevID());

            verification.verify(doc);

            doc.free();
            commit = true;
        } finally {
            db.endTransaction(commit);
        }
    }

    // - Document Legacy Properties
    @Test
    public void testDocumentLegacyProperties() throws LiteCoreException {
        // NOTE: JNI binding does not cover all requires C APIs.
    }

    // - Document Clobber Remote Rev
    @Test
    public void testDocumentClobberRemoteRev() throws LiteCoreException {
        // NOTE: JNI binding does not cover all requires C APIs.
    }
}
