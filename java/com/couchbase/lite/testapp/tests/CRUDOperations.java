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

package com.couchbase.lite.testapp.tests;

import com.couchbase.lite.Database;
import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.ReplicationFilter;
import com.couchbase.lite.RevisionList;
import com.couchbase.lite.Status;
import com.couchbase.lite.CBLiteException;
import com.couchbase.lite.internal.CBLBody;
import com.couchbase.lite.internal.CBLRevisionInternal;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CRUDOperations extends CBLiteTestCase implements Database.ChangeListener {

    public static final String TAG = "CRUDOperations";

    public void testCRUDOperations() throws CBLiteException {

        database.addChangeListener(this);

        String privateUUID = database.privateUUID();
        String publicUUID = database.publicUUID();
        Log.v(TAG, "DB private UUID = '" + privateUUID + "', public UUID = '" + publicUUID + "'");
        Assert.assertTrue(privateUUID.length() >= 20);
        Assert.assertTrue(publicUUID.length() >= 20);

        //create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        CBLBody body = new CBLBody(documentProperties);
        CBLRevisionInternal rev1 = new CBLRevisionInternal(body, database);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);

        Log.v(TAG, "Created " + rev1);
        Assert.assertTrue(rev1.getDocId().length() >= 10);
        Assert.assertTrue(rev1.getRevId().startsWith("1-"));

        //read it back
        CBLRevisionInternal readRev = database.getDocumentWithIDAndRev(rev1.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        Assert.assertNotNull(readRev);
        Map<String,Object> readRevProps = readRev.getProperties();
        Assert.assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated!");
        body = new CBLBody(documentProperties);
        CBLRevisionInternal rev2 = new CBLRevisionInternal(body, database);
        CBLRevisionInternal rev2input = rev2;
        rev2 = database.putRevision(rev2, rev1.getRevId(), false, status);
        Log.v(TAG, "Updated " + rev1);
        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertTrue(rev2.getRevId().startsWith("2-"));

        //read it back
        readRev = database.getDocumentWithIDAndRev(rev2.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        Assert.assertNotNull(readRev);
        Assert.assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        boolean gotExpectedError = false;
        try {
            database.putRevision(rev2input, rev1.getRevId(), false, status);
        } catch (CBLiteException e) {
            gotExpectedError = e.getCBLStatus().getCode() == Status.CONFLICT;
        }
        Assert.assertTrue(gotExpectedError);

        // Check the changes feed, with and without filters:
        RevisionList changes = database.changesSince(0, null, null);
        Log.v(TAG, "Changes = " + changes);
        Assert.assertEquals(1, changes.size());

        changes = database.changesSince(0, null, new ReplicationFilter() {

            @Override
            public boolean filter(CBLRevisionInternal revision, Map<String, Object> params) {
                return "updated!".equals(revision.getProperties().get("status"));
            }

        });
        Assert.assertEquals(1, changes.size());

        changes = database.changesSince(0, null, new ReplicationFilter() {

            @Override
            public boolean filter(CBLRevisionInternal revision, Map<String, Object> params) {
                return "not updated!".equals(revision.getProperties().get("status"));
            }

        });
        Assert.assertEquals(0, changes.size());


        // Delete it:
        CBLRevisionInternal revD = new CBLRevisionInternal(rev2.getDocId(), null, true, database);
        CBLRevisionInternal revResult = null;
        gotExpectedError = false;
        try {
            revResult = database.putRevision(revD, null, false, status);
        } catch (CBLiteException e) {
            gotExpectedError = e.getCBLStatus().getCode() == Status.CONFLICT;
        }
        Assert.assertTrue(gotExpectedError);

        Assert.assertNull(revResult);
        revD = database.putRevision(revD, rev2.getRevId(), false, status);
        Assert.assertEquals(Status.OK, status.getCode());
        Assert.assertEquals(revD.getDocId(), rev2.getDocId());
        Assert.assertTrue(revD.getRevId().startsWith("3-"));

        // Delete nonexistent doc:
        CBLRevisionInternal revFake = new CBLRevisionInternal("fake", null, true, database);
        gotExpectedError = false;
        try {
            database.putRevision(revFake, null, false, status);
        } catch (CBLiteException e) {
            gotExpectedError = e.getCBLStatus().getCode() == Status.NOT_FOUND;
        }
        Assert.assertTrue(gotExpectedError);

        // Read it back (should fail):
        readRev = database.getDocumentWithIDAndRev(revD.getDocId(), null, EnumSet.noneOf(Database.TDContentOptions.class));
        Assert.assertNull(readRev);

        // Get Changes feed
        changes = database.changesSince(0, null, null);
        Assert.assertTrue(changes.size() == 1);

        // Get Revision History
        List<CBLRevisionInternal> history = database.getRevisionHistory(revD);
        Assert.assertEquals(revD, history.get(0));
        Assert.assertEquals(rev2, history.get(1));
        Assert.assertEquals(rev1, history.get(2));
    }


    @Override
    public void changed(Database.ChangeEvent event) {
        List<DocumentChange> changes = event.getChanges();
        for (DocumentChange change : changes) {

            CBLRevisionInternal rev = change.getRevisionInternal();
            Assert.assertNotNull(rev);
            Assert.assertNotNull(rev.getDocId());
            Assert.assertNotNull(rev.getRevId());
            Assert.assertEquals(rev.getDocId(), rev.getProperties().get("_id"));
            Assert.assertEquals(rev.getRevId(), rev.getProperties().get("_rev"));
        }


    }
}
