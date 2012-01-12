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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class RevTree extends AndroidTestCase {

    public static final String TAG = "RevTree";

    public void testRevTree() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        TDRevision rev = new TDRevision("MyDocId", "4-foxy", false);

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

        TDStatus status = db.forceInsert(rev, revHistory, null);
        Assert.assertEquals(201, status.getCode());
        Assert.assertEquals(1, db.getDocumentCount());
        verifyHistory(db, rev, revHistory);

        TDRevision conflict = new TDRevision("MyDocId", "5-epsilon", false);

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

        status = db.forceInsert(conflict, conflictHistory, null);
        Assert.assertEquals(201, status.getCode());
        Assert.assertEquals(1, db.getDocumentCount());
        verifyHistory(db, conflict, conflictHistory);

        // Fetch one of those phantom revisions with no body:
        TDRevision rev2 = db.getDocumentWithIDAndRev(rev.getDocId(), "2-too");
        Assert.assertEquals(rev.getDocId(), rev2.getDocId());
        Assert.assertEquals("2-too", rev2.getRevId());
        Assert.assertNull(rev2.getBody());

        // Make sure no duplicate rows were inserted for the common revisions:
        Assert.assertEquals(7, db.getLastSequence());

        // Make sure the revision with the higher revID wins the conflict:
        TDRevision current = db.getDocumentWithID(rev.getDocId());
        Assert.assertEquals(conflict, current);

        db.close();
    }

    private static void verifyHistory(TDDatabase db, TDRevision rev, List<String> history) {
        TDRevision gotRev = db.getDocumentWithID(rev.getDocId());
        Assert.assertEquals(rev, gotRev);
        Assert.assertEquals(rev.getProperties(), gotRev.getProperties());

        List<TDRevision> revHistory = db.getRevisionHistory(gotRev);
        Assert.assertEquals(history.size(), revHistory.size());
        for(int i=0; i<history.size(); i++) {
            TDRevision hrev = revHistory.get(i);
            Assert.assertEquals(rev.getDocId(), hrev.getDocId());
            Assert.assertEquals(history.get(i), hrev.getRevId());
            Assert.assertFalse(rev.isDeleted());
        }
    }

}
