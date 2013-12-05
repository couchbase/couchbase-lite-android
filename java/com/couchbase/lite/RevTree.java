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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevTree extends CBLiteTestCase {

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

        database.forceInsert(conflict, conflictHistory, null);
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
