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

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CRUDOperationsTest extends LiteTestCase implements Database.ChangeListener {

    public static final String TAG = "CRUDOperations";

    public void testCRUDOperations() throws CouchbaseLiteException {

        database.addChangeListener(this);

        String privateUUID = database.privateUUID();
        String publicUUID = database.publicUUID();
        Log.v(TAG, "DB private UUID = '" + privateUUID + "', public UUID = '" + publicUUID + "'");
        assertTrue(privateUUID.length() >= 20);
        assertTrue(publicUUID.length() >= 20);

        //create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        Body body = new Body(documentProperties);
        RevisionInternal rev1 = new RevisionInternal(body, database);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);

        Log.v(TAG, "Created " + rev1);
        assertTrue(rev1.getDocId().length() >= 10);
        assertTrue(rev1.getRevId().startsWith("1-"));

        //read it back
        RevisionInternal readRev = database.getDocumentWithIDAndRev(rev1.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        assertNotNull(readRev);
        Map<String,Object> readRevProps = readRev.getProperties();
        assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated!");
        body = new Body(documentProperties);
        RevisionInternal rev2 = new RevisionInternal(body, database);
        RevisionInternal rev2input = rev2;
        rev2 = database.putRevision(rev2, rev1.getRevId(), false, status);
        Log.v(TAG, "Updated " + rev1);
        assertEquals(rev1.getDocId(), rev2.getDocId());
        assertTrue(rev2.getRevId().startsWith("2-"));

        //read it back
        readRev = database.getDocumentWithIDAndRev(rev2.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        assertNotNull(readRev);
        assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        boolean gotExpectedError = false;
        try {
            database.putRevision(rev2input, rev1.getRevId(), false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = e.getCBLStatus().getCode() == Status.CONFLICT;
        }
        assertTrue(gotExpectedError);

        // Check the changes feed, with and without filters:
        RevisionList changes = database.changesSince(0, null, null);
        Log.v(TAG, "Changes = " + changes);
        assertEquals(1, changes.size());

        changes = database.changesSince(0, null, new ReplicationFilter() {

            @Override
            public boolean filter(SavedRevision revision, Map<String, Object> params) {
                return "updated!".equals(revision.getProperties().get("status"));
            }

        });
        assertEquals(1, changes.size());

        changes = database.changesSince(0, null, new ReplicationFilter() {

            @Override
            public boolean filter(SavedRevision revision, Map<String, Object> params) {
                return "not updated!".equals(revision.getProperties().get("status"));
            }

        });
        assertEquals(0, changes.size());


        // Delete it:
        RevisionInternal revD = new RevisionInternal(rev2.getDocId(), null, true, database);
        RevisionInternal revResult = null;
        gotExpectedError = false;
        try {
            revResult = database.putRevision(revD, null, false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = e.getCBLStatus().getCode() == Status.CONFLICT;
        }
        assertTrue(gotExpectedError);

        assertNull(revResult);
        revD = database.putRevision(revD, rev2.getRevId(), false, status);
        assertEquals(Status.OK, status.getCode());
        assertEquals(revD.getDocId(), rev2.getDocId());
        assertTrue(revD.getRevId().startsWith("3-"));

        // Delete nonexistent doc:
        RevisionInternal revFake = new RevisionInternal("fake", null, true, database);
        gotExpectedError = false;
        try {
            database.putRevision(revFake, null, false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = e.getCBLStatus().getCode() == Status.NOT_FOUND;
        }
        assertTrue(gotExpectedError);

        // Read it back (should fail):
        readRev = database.getDocumentWithIDAndRev(revD.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        assertNull(readRev);

        // Get Changes feed
        changes = database.changesSince(0, null, null);
        assertTrue(changes.size() == 1);

        // Get Revision History
        List<RevisionInternal> history = database.getRevisionHistory(revD);
        assertEquals(revD, history.get(0));
        assertEquals(rev2, history.get(1));
        assertEquals(rev1, history.get(2));
    }


    @Override
    public void changed(Database.ChangeEvent event) {
        List<DocumentChange> changes = event.getChanges();
        for (DocumentChange change : changes) {

            RevisionInternal rev = change.getRevisionInternal();
            assertNotNull(rev);
            assertNotNull(rev.getDocId());
            assertNotNull(rev.getRevId());
            assertEquals(rev.getDocId(), rev.getProperties().get("_id"));
            assertEquals(rev.getRevId(), rev.getProperties().get("_rev"));
        }


    }
}
