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

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.TDViewMapBlock;
import com.couchbase.touchdb.TDViewMapEmitBlock;

public class Views extends AndroidTestCase {

    public static final String TAG = "Views";

    public void testViewCreation() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        TDView view = db.getViewNamed("aview");
        Assert.assertNotNull(view);
        Assert.assertEquals(db, view.getDb());
        Assert.assertEquals("aview", view.getName());
        Assert.assertNull(view.getMapBlock());

        boolean changed = view.setMapBlock(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                //no-op
            }
        }, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, db.getAllViews().size());
        Assert.assertEquals(view, db.getAllViews().get(0));

        changed = view.setMapBlock(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                //no-op
            }
        }, "1");

        Assert.assertFalse(changed);

        changed = view.setMapBlock(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                //no-op
            }
        }, "2");

        Assert.assertTrue(changed);

        db.close();
    }

    private TDRevision putDoc(TDDatabase db, Map<String,Object> props) {
        TDRevision rev = new TDRevision(props);
        TDStatus status = new TDStatus();
        rev = db.putRevision(rev, null, status);
        Assert.assertTrue(status.isSuccessful());
        return rev;
    }

    public void testViewIndex() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("key", "one");
        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("key", "two");
        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("key", "three");
        Map<String,Object> dictX = new HashMap<String,Object>();
        dictX.put("clef", "quatre");

        TDRevision rev1 = putDoc(db, dict1);
        TDRevision rev2 = putDoc(db, dict2);
        TDRevision rev3 = putDoc(db, dict3);
        putDoc(db, dictX);

        TDView view = db.getViewNamed("aview");
        view.setMapBlock(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                emitter.emit(document.get("key"), null);
            }
        }, "1");

        Assert.assertEquals(1, view.getViewId());

        TDStatus updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        List<Map<String,Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(1, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"two\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(2, dumpResult.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(3, dumpResult.get(1).get("seq"));

        //no-op reindex
        updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        // Now add a doc and update a doc:
        TDRevision threeUpdated = new TDRevision(rev3.getDocId(), rev3.getRevId(), false);
        Map<String,Object> newdict3 = new HashMap<String,Object>();
        newdict3.put("key", "3hree");
        threeUpdated.setProperties(newdict3);
        TDStatus status = new TDStatus();
        rev3 = db.putRevision(threeUpdated, rev3.getRevId(), status);
        Assert.assertTrue(status.isSuccessful());

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("key", "four");
        TDRevision rev4 = putDoc(db, dict4);

        TDRevision twoDeleted = new TDRevision(rev2.getDocId(), rev2.getRevId(), true);
        db.putRevision(twoDeleted, rev2.getRevId(), status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(1, dumpResult.get(2).get("seq"));
        Assert.assertEquals("\"3hree\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(5, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(6, dumpResult.get(1).get("seq"));

        // Now do a real query:
        Map<String,Object> query = view.queryWithOptions(null);
        Assert.assertEquals(3, query.get("total_rows"));
        Assert.assertEquals(0, query.get("offset"));
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows = (List<Map<String,Object>>)query.get("rows");
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals("one", rows.get(2).get("key"));
        Assert.assertEquals(rev1.getDocId(), rows.get(2).get("id"));
        Assert.assertEquals("3hree", rows.get(0).get("key"));
        Assert.assertEquals(rev3.getDocId(), rows.get(0).get("id"));
        Assert.assertEquals("four", rows.get(1).get("key"));
        Assert.assertEquals(rev4.getDocId(), rows.get(1).get("id"));

        view.removeIndex();

        db.close();
    }

}
