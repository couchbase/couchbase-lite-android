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

package com.couchbase.cblite.testapp.tests;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import junit.framework.Assert;
import android.util.Log;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLFilterBlock;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLRevisionList;
import com.couchbase.cblite.CBLStatus;

public class CRUDOperations extends CBLiteTestCase implements Observer {

    public static final String TAG = "CRUDOperations";

    public void testCRUDOperations() {

        database.addObserver(this);

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
        CBLRevision rev1 = new CBLRevision(body);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);

        Assert.assertEquals(CBLStatus.CREATED, status.getCode());
        Log.v(TAG, "Created " + rev1);
        Assert.assertTrue(rev1.getDocId().length() >= 10);
        Assert.assertTrue(rev1.getRevId().startsWith("1-"));

        //read it back
        CBLRevision readRev = database.getDocumentWithIDAndRev(rev1.getDocId(), null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(readRev);
        Map<String,Object> readRevProps = readRev.getProperties();
        Assert.assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated!");
        body = new CBLBody(documentProperties);
        CBLRevision rev2 = new CBLRevision(body);
        CBLRevision rev2input = rev2;
        rev2 = database.putRevision(rev2, rev1.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());
        Log.v(TAG, "Updated " + rev1);
        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertTrue(rev2.getRevId().startsWith("2-"));

        //read it back
        readRev = database.getDocumentWithIDAndRev(rev2.getDocId(), null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(readRev);
        Assert.assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        database.putRevision(rev2input, rev1.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CONFLICT, status.getCode());

        // Check the changes feed, with and without filters:
        CBLRevisionList changes = database.changesSince(0, null, null);
        Log.v(TAG, "Changes = " + changes);
        Assert.assertEquals(1, changes.size());

        changes = database.changesSince(0, null, new CBLFilterBlock() {

            @Override
            public boolean filter(CBLRevision revision) {
                return "updated!".equals(revision.getProperties().get("status"));
            }

        });
        Assert.assertEquals(1, changes.size());

        changes = database.changesSince(0, null, new CBLFilterBlock() {

            @Override
            public boolean filter(CBLRevision revision) {
                return "not updated!".equals(revision.getProperties().get("status"));
            }

        });
        Assert.assertEquals(0, changes.size());


        // Delete it:
        CBLRevision revD = new CBLRevision(rev2.getDocId(), null, true);
        CBLRevision revResult = database.putRevision(revD, null, false, status);
        Assert.assertNull(revResult);
        Assert.assertEquals(CBLStatus.CONFLICT, status.getCode());
        revD = database.putRevision(revD, rev2.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.OK, status.getCode());
        Assert.assertEquals(revD.getDocId(), rev2.getDocId());
        Assert.assertTrue(revD.getRevId().startsWith("3-"));

        // Delete nonexistent doc:
        CBLRevision revFake = new CBLRevision("fake", null, true);
        database.putRevision(revFake, null, false, status);
        Assert.assertEquals(CBLStatus.NOT_FOUND, status.getCode());

        // Read it back (should fail):
        readRev = database.getDocumentWithIDAndRev(revD.getDocId(), null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNull(readRev);

        // Get Changes feed
        changes = database.changesSince(0, null, null);
        Assert.assertTrue(changes.size() == 1);

        // Get Revision History
        List<CBLRevision> history = database.getRevisionHistory(revD);
        Assert.assertEquals(revD, history.get(0));
        Assert.assertEquals(rev2, history.get(1));
        Assert.assertEquals(rev1, history.get(2));
    }

    @Override
    public void update(Observable observable, Object changeObject) {
        if(observable instanceof CBLDatabase) {
            //make sure we're listening to the right events
            Map<String,Object> changeNotification = (Map<String,Object>)changeObject;

            CBLRevision rev = (CBLRevision)changeNotification.get("rev");
            Assert.assertNotNull(rev);
            Assert.assertNotNull(rev.getDocId());
            Assert.assertNotNull(rev.getRevId());
            Assert.assertEquals(rev.getDocId(), rev.getProperties().get("_id"));
            Assert.assertEquals(rev.getRevId(), rev.getProperties().get("_rev"));
        }
    }
}
