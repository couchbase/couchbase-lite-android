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

package com.couchbase.touchdb.testapp.tests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import android.test.AndroidTestCase;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class CRUDOperations extends AndroidTestCase {

    public static final String TAG = "CRUDOperations";

    public void testCRUDOperations() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        //create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = db.putRevision(rev1, null, status);

        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        Log.v(TAG, "Created " + rev1);
        Assert.assertTrue(rev1.getDocId().length() >= 10);
        Assert.assertTrue(rev1.getRevId().startsWith("1-"));

        //read it back
        TDRevision readRev = db.getDocumentWithID(rev1.getDocId());
        Assert.assertNotNull(readRev);
        Map<String,Object> readRevProps = readRev.getProperties();
        Assert.assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated");
        body = new TDBody(documentProperties);
        TDRevision rev2 = new TDRevision(body);
        TDRevision rev2input = rev2;
        rev2 = db.putRevision(rev2, rev1.getRevId(), status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        Log.v(TAG, "Updated " + rev1);
        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertTrue(rev2.getRevId().startsWith("2-"));

        //read it back
        readRev = db.getDocumentWithID(rev2.getDocId());
        Assert.assertNotNull(readRev);
        Assert.assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        db.putRevision(rev2input, rev1.getRevId(), status);
        Assert.assertEquals(TDStatus.CONFLICT, status.getCode());

        // Delete it:
        TDRevision revD = new TDRevision(rev2.getDocId(), null, true);
        revD = db.putRevision(revD, rev2.getRevId(), status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals(revD.getDocId(), rev2.getDocId());
        Assert.assertTrue(revD.getRevId().startsWith("3-"));

        // Read it back (should fail):
        readRev = db.getDocumentWithID(revD.getDocId());
        Assert.assertNull(readRev);

        // Get Changes feed
        List<TDRevision> changes = db.changesSince(0, null);
        Assert.assertTrue(changes.size() == 1);

        // Get Revision History
        List<TDRevision> history = db.getRevisionHistory(revD);
        Assert.assertEquals(revD, history.get(0));
        Assert.assertEquals(rev2, history.get(1));
        Assert.assertEquals(rev1, history.get(2));

        Log.v(TAG, "Tests complete, closing database");
        db.close();
    }


    private static Map<String,Object> userProperties(Map<String,Object> properties) {
        Map<String,Object> result = new HashMap<String,Object>();

        for (String key : properties.keySet()) {
            if(!key.startsWith("_")) {
                result.put(key, properties.get(key));
            }
        }

        return result;
    }
}
