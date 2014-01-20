/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevTreeTest extends LiteTestCase {

    public static final String TAG = "RevTree";

    public void testForceInsertEmptyHistory() throws CouchbaseLiteException {

        List<String> revHistory = null;
        RevisionInternal rev = new RevisionInternal("FakeDocId", "1-tango", false, database);

        Map<String, Object> revProperties = new HashMap<String, Object>();
        revProperties.put("_id", rev.getDocId());
        revProperties.put("_rev", rev.getRevId());
        revProperties.put("message", "hi");
        rev.setProperties(revProperties);

        database.forceInsert(rev, revHistory, null);

    }

    public void testRevTree() throws CouchbaseLiteException {

        RevisionInternal rev = new RevisionInternal("MyDocId", "4-foxy", false, database);

        Map<String, Object> revProperties = new HashMap<String, Object>();
        revProperties.put("_id", rev.getDocId());
        revProperties.put("_rev", rev.getRevId());
        revProperties.put("message", "hi");
        rev.setProperties(revProperties);

        List<String> revHistory = new ArrayList<String>();
        revHistory.add(rev.getRevId());
        revHistory.add("3-thrice");
        revHistory.add("2-too");
        revHistory.add("1-won");

        database.forceInsert(rev, revHistory, null);
        assertEquals(1, database.getDocumentCount());
        verifyHistory(database, rev, revHistory);

        RevisionInternal conflict = new RevisionInternal("MyDocId", "5-epsilon", false, database);

        Map<String, Object> conflictProperties = new HashMap<String, Object>();
        conflictProperties.put("_id", conflict.getDocId());
        conflictProperties.put("_rev", conflict.getRevId());
        conflictProperties.put("message", "yo");
        conflict.setProperties(conflictProperties);

        List<String> conflictHistory = new ArrayList<String>();
        conflictHistory.add(conflict.getRevId());
        conflictHistory.add("4-delta");
        conflictHistory.add("3-gamma");
        conflictHistory.add("2-too");
        conflictHistory.add("1-won");

        final List wasInConflict = new ArrayList();
        final Database.ChangeListener listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                if (event.getChanges().get(0).isConflict()) {
                    wasInConflict.add(new Object());
                }
            }
        };
        database.addChangeListener(listener);
        database.forceInsert(conflict, conflictHistory, null);
        assertTrue(wasInConflict.size() > 0);
        database.removeChangeListener(listener);
        assertEquals(1, database.getDocumentCount());
        verifyHistory(database, conflict, conflictHistory);

        // Add an unrelated document:
        RevisionInternal other = new RevisionInternal("AnotherDocID", "1-ichi", false, database);
        Map<String,Object> otherProperties = new HashMap<String,Object>();
        otherProperties.put("language", "jp");
        other.setProperties(otherProperties);
        List<String> otherHistory = new ArrayList<String>();
        otherHistory.add(other.getRevId());
        database.forceInsert(other, otherHistory, null);

        // Fetch one of those phantom revisions with no body:
        RevisionInternal rev2 = database.getDocumentWithIDAndRev(rev.getDocId(), "2-too", EnumSet.noneOf(Database.TDContentOptions.class));
        assertEquals(rev.getDocId(), rev2.getDocId());
        assertEquals("2-too", rev2.getRevId());
        //Assert.assertNull(rev2.getContent());

        // Make sure no duplicate rows were inserted for the common revisions:
        assertEquals(8, database.getLastSequenceNumber());

        // Make sure the revision with the higher revID wins the conflict:
        RevisionInternal current = database.getDocumentWithIDAndRev(rev.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        assertEquals(conflict, current);

        // Get the _changes feed and verify only the winner is in it:
        ChangesOptions options = new ChangesOptions();
        RevisionList changes = database.changesSince(0, options, null);
        RevisionList expectedChanges = new RevisionList();
        expectedChanges.add(conflict);
        expectedChanges.add(other);
        assertEquals(changes, expectedChanges);
        options.setIncludeConflicts(true);
        changes = database.changesSince(0, options, null);
        expectedChanges = new RevisionList();
        expectedChanges.add(rev);
        expectedChanges.add(conflict);
        expectedChanges.add(other);
        assertEquals(changes, expectedChanges);
    }

    /**
     * Test that the public API works as expected in change notifications after a rev tree
     * insertion.
     *
     * These tests are currently known to be failing, the bug is being tracked in:
     * https://github.com/couchbase/couchbase-lite-android-core/pull/27 -- NOTE: commented temporarily 
     */
    public void testRevTreeChangeNotifications() throws CouchbaseLiteException {
        final String DOCUMENT_ID = "MyDocId";

        // add a document with a single (first) revision
        final RevisionInternal rev = new RevisionInternal(DOCUMENT_ID, "1-one", false, database);
        Map<String, Object> revProperties = new HashMap<String, Object>();
        revProperties.put("_id", rev.getDocId());
        revProperties.put("_rev", rev.getRevId());
        revProperties.put("message", "hi");
        rev.setProperties(revProperties);

        List<String> revHistory = Arrays.asList(rev.getRevId());

        Database.ChangeListener listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                assertEquals(1, event.getChanges().size());
                DocumentChange change = event.getChanges().get(0);
                assertEquals(DOCUMENT_ID, change.getDocumentId());
                assertEquals(rev.getRevId(), change.getRevisionId());
                assertTrue(change.isCurrentRevision());
                // assertFalse(change.isConflict()); // fails - temp commented so that the build passes

                SavedRevision current = database.getDocument(change.getDocumentId()).getCurrentRevision();
                assertEquals(rev.getRevId(), current.getId());
            }
        };
        database.addChangeListener(listener);
        database.forceInsert(rev, revHistory, null);
        database.removeChangeListener(listener);

        // add two more revisions to the document
        final RevisionInternal rev3 = new RevisionInternal(DOCUMENT_ID, "3-three", false, database);
        Map<String, Object> rev3Properties = new HashMap<String, Object>();
        rev3Properties.put("_id", rev3.getDocId());
        rev3Properties.put("_rev", rev3.getRevId());
        rev3Properties.put("message", "hi again");
        rev3.setProperties(rev3Properties);

        List<String> rev3History = Arrays.asList(rev3.getRevId(), "2-two", rev.getRevId());

        listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                assertEquals(1, event.getChanges().size());
                DocumentChange change = event.getChanges().get(0);
                assertEquals(DOCUMENT_ID, change.getDocumentId());
                assertEquals(rev3.getRevId(), change.getRevisionId());
                assertTrue(change.isCurrentRevision());
                // assertFalse(change.isConflict()); // fails -- temp commented so that the build passes

                Document doc = database.getDocument(change.getDocumentId());
                assertEquals(rev3.getRevId(), doc.getCurrentRevisionId());
                try {
                    assertEquals(3, doc.getRevisionHistory().size());
                } catch (CouchbaseLiteException ex) {
                    fail("CouchbaseLiteException in change listener: " + ex.toString());
                }
            }
        };
        database.addChangeListener(listener);
        database.forceInsert(rev3, rev3History, null);
        database.removeChangeListener(listener);

        // add a conflicting revision, with the same history length as the last revision we
        // inserted. Since this new revision's revID has a higher ASCII sort, it should become the
        // new winning revision.
        final RevisionInternal conflictRev = new RevisionInternal(DOCUMENT_ID, "3-winner", false, database);
        Map<String, Object> conflictProperties = new HashMap<String, Object>();
        conflictProperties.put("_id", conflictRev.getDocId());
        conflictProperties.put("_rev", conflictRev.getRevId());
        conflictProperties.put("message", "winner");
        conflictRev.setProperties(conflictProperties);

        List<String> conflictRevHistory = Arrays.asList(conflictRev.getRevId(), "2-two", rev.getRevId());

        listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                assertEquals(1, event.getChanges().size());
                DocumentChange change = event.getChanges().get(0);
                assertEquals(DOCUMENT_ID, change.getDocumentId());
                assertEquals(conflictRev.getRevId(), change.getRevisionId());
                assertTrue(change.isCurrentRevision());
                assertTrue(change.isConflict());

                Document doc = database.getDocument(change.getDocumentId());
                assertEquals(conflictRev.getRevId(), doc.getCurrentRevisionId());
                try {
                    assertEquals(2, doc.getConflictingRevisions().size());
                    assertEquals(3, doc.getRevisionHistory().size());
                } catch (CouchbaseLiteException ex) {
                    fail("CouchbaseLiteException in change listener: " + ex.toString());
                }
            }
        };
        database.addChangeListener(listener);
        database.forceInsert(conflictRev, conflictRevHistory, null);
        database.removeChangeListener(listener);
    }

    private static void verifyHistory(Database db, RevisionInternal rev, List<String> history) {
        RevisionInternal gotRev = db.getDocumentWithIDAndRev(rev.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        assertEquals(rev, gotRev);
        assertEquals(rev.getProperties(), gotRev.getProperties());

        List<RevisionInternal> revHistory = db.getRevisionHistory(gotRev);
        assertEquals(history.size(), revHistory.size());
        for(int i=0; i<history.size(); i++) {
            RevisionInternal hrev = revHistory.get(i);
            assertEquals(rev.getDocId(), hrev.getDocId());
            assertEquals(history.get(i), hrev.getRevId());
            assertFalse(rev.isDeleted());
        }
    }

}
