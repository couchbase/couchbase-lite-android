/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 * <p/>
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevTreeTest extends LiteTestCaseWithDB {

    public static final String TAG = "RevTree";

    public void testForceInsertEmptyHistory() throws CouchbaseLiteException {

        List<String> revHistory = null;
        RevisionInternal rev = new RevisionInternal("FakeDocId", "1-1111", false);

        Map<String, Object> revProperties = new HashMap<String, Object>();
        revProperties.put("_id", rev.getDocID());
        revProperties.put("_rev", rev.getRevID());
        revProperties.put("message", "hi");
        rev.setProperties(revProperties);

        database.forceInsert(rev, revHistory, null);
    }

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test06_RevTree
     */
    public void testRevTree() throws CouchbaseLiteException {

        RevisionInternal rev = new RevisionInternal("MyDocId", "4-4444", false);

        Map<String, Object> revProperties = new HashMap<String, Object>();
        revProperties.put("_id", rev.getDocID());
        revProperties.put("_rev", rev.getRevID());
        revProperties.put("message", "hi");
        rev.setProperties(revProperties);

        List<String> revHistory = new ArrayList<String>();
        revHistory.add(rev.getRevID());
        revHistory.add("3-3333");
        revHistory.add("2-2222");
        revHistory.add("1-1111");

        database.forceInsert(rev, revHistory, null);
        assertEquals(1, database.getDocumentCount());
        verifyHistory(database, rev, revHistory);

        RevisionInternal conflict = new RevisionInternal("MyDocId", "5-5555", false);

        Map<String, Object> conflictProperties = new HashMap<String, Object>();
        conflictProperties.put("_id", conflict.getDocID());
        conflictProperties.put("_rev", conflict.getRevID());
        conflictProperties.put("message", "yo");
        conflict.setProperties(conflictProperties);

        List<String> conflictHistory = new ArrayList<String>();
        conflictHistory.add(conflict.getRevID());
        conflictHistory.add("4-4545");
        conflictHistory.add("3-3030");
        conflictHistory.add("2-2222");
        conflictHistory.add("1-1111");

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
        RevisionInternal other = new RevisionInternal("AnotherDocID", "1-1010", false);
        Map<String, Object> otherProperties = new HashMap<String, Object>();
        otherProperties.put("language", "jp");
        other.setProperties(otherProperties);
        List<String> otherHistory = new ArrayList<String>();
        otherHistory.add(other.getRevID());
        database.forceInsert(other, otherHistory, null);

        // Fetch one of those phantom revisions with no body:
        RevisionInternal rev2 = database.getDocument(rev.getDocID(), "2-2222", true);
        assertNull(rev2); // NOT FOUND

        // Make sure no duplicate rows were inserted for the common revisions:
        assertEquals(isSQLiteDB()?8:3, database.getLastSequenceNumber());


        // Make sure the revision with the higher revID wins the conflict:
        RevisionInternal current = database.getDocument(rev.getDocID(), null, true);
        assertEquals(conflict, current);

        // Get the _changes feed and verify only the winner is in it:
        ChangesOptions options = new ChangesOptions();
        RevisionList changes = database.changesSince(0, options, null, null);
        RevisionList expectedChanges = new RevisionList();
        expectedChanges.add(conflict);
        expectedChanges.add(other);
        assertEquals(expectedChanges, changes);

        options.setIncludeConflicts(true);
        changes = database.changesSince(0, options, null, null);
        expectedChanges = new RevisionList();
        if(isSQLiteDB()) {
            expectedChanges.add(rev);
            expectedChanges.add(conflict);
            expectedChanges.add(other);
        }
        else{
            expectedChanges.add(conflict);
            expectedChanges.add(rev);
            expectedChanges.add(other);
        }
        assertEquals(expectedChanges, changes);
    }

    /**
     * Test that the public API works as expected in change notifications after a rev tree
     * insertion.  See https://github.com/couchbase/couchbase-lite-android-core/pull/27
     */
    public void testRevTreeChangeNotifications() throws CouchbaseLiteException {
        final String DOCUMENT_ID = "MyDocId";

        // add a document with a single (first) revision
        final RevisionInternal rev = new RevisionInternal(DOCUMENT_ID, "1-1111", false);
        Map<String, Object> revProperties = new HashMap<String, Object>();
        revProperties.put("_id", rev.getDocID());
        revProperties.put("_rev", rev.getRevID());
        revProperties.put("message", "hi");
        rev.setProperties(revProperties);

        List<String> revHistory = Arrays.asList(rev.getRevID());

        Database.ChangeListener listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                assertEquals(1, event.getChanges().size());
                DocumentChange change = event.getChanges().get(0);
                assertEquals(DOCUMENT_ID, change.getDocumentId());
                assertEquals(rev.getRevID(), change.getRevisionId());
                assertTrue(change.isCurrentRevision());
                assertFalse(change.isConflict());

                SavedRevision current = database.getDocument(
                        change.getDocumentId()).getCurrentRevision();
                assertEquals(rev.getRevID(), current.getId());
            }
        };
        database.addChangeListener(listener);
        database.forceInsert(rev, revHistory, null);
        database.removeChangeListener(listener);

        // add two more revisions to the document
        final RevisionInternal rev3 = new RevisionInternal(DOCUMENT_ID, "3-3333", false);
        Map<String, Object> rev3Properties = new HashMap<String, Object>();
        rev3Properties.put("_id", rev3.getDocID());
        rev3Properties.put("_rev", rev3.getRevID());
        rev3Properties.put("message", "hi again");
        rev3.setProperties(rev3Properties);

        List<String> rev3History = Arrays.asList(rev3.getRevID(), "2-2222", rev.getRevID());

        listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                assertEquals(1, event.getChanges().size());
                DocumentChange change = event.getChanges().get(0);
                assertEquals(DOCUMENT_ID, change.getDocumentId());
                assertEquals(rev3.getRevID(), change.getRevisionId());
                assertTrue(change.isCurrentRevision());
                assertFalse(change.isConflict());

                Document doc = database.getDocument(change.getDocumentId());
                assertEquals(rev3.getRevID(), doc.getCurrentRevisionId());
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
        final RevisionInternal conflictRev = new RevisionInternal(DOCUMENT_ID, "3-4444", false);
        Map<String, Object> conflictProperties = new HashMap<String, Object>();
        conflictProperties.put("_id", conflictRev.getDocID());
        conflictProperties.put("_rev", conflictRev.getRevID());
        conflictProperties.put("message", "winner");
        conflictRev.setProperties(conflictProperties);

        List<String> conflictRevHistory = Arrays.asList(conflictRev.getRevID(), "2-2222", rev.getRevID());

        listener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                assertEquals(1, event.getChanges().size());
                DocumentChange change = event.getChanges().get(0);
                assertEquals(DOCUMENT_ID, change.getDocumentId());
                assertEquals(conflictRev.getRevID(), change.getRevisionId());
                assertTrue(change.isCurrentRevision());
                assertTrue(change.isConflict());

                Document doc = database.getDocument(change.getDocumentId());
                assertEquals(conflictRev.getRevID(), doc.getCurrentRevisionId());
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
        RevisionInternal gotRev = db.getDocument(rev.getDocID(), null, true);
        assertEquals(rev, gotRev);
        assertEquals(rev.getProperties(), gotRev.getProperties());

        List<RevisionInternal> revHistory = db.getRevisionHistory(gotRev);
        assertEquals(history.size(), revHistory.size());
        for (int i = 0; i < history.size(); i++) {
            RevisionInternal hrev = revHistory.get(i);
            assertEquals(rev.getDocID(), hrev.getDocID());
            assertEquals(history.get(i), hrev.getRevID());
            assertFalse(rev.isDeleted());
        }
    }
}
