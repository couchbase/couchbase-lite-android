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
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDQueryOptions;
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

    private List<TDRevision> putDocs(TDDatabase db) {
        List<TDRevision> result = new ArrayList<TDRevision>();

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("_id", "22222");
        dict2.put("key", "two");
        result.add(putDoc(db, dict2));

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("_id", "44444");
        dict4.put("key", "four");
        result.add(putDoc(db, dict4));

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("_id", "11111");
        dict1.put("key", "one");
        result.add(putDoc(db, dict1));

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("_id", "33333");
        dict3.put("key", "three");
        result.add(putDoc(db, dict3));

        Map<String,Object> dict5 = new HashMap<String,Object>();
        dict5.put("_id", "55555");
        dict5.put("key", "five");
        result.add(putDoc(db, dict5));

        return result;
    }

    public TDView createView(TDDatabase db) {
        TDView view = db.getViewNamed("aview");
        view.setMapBlock(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                emitter.emit(document.get("key"), null);
            }
        }, "1");
        return view;
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

        TDView view = createView(db);

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

    public Map<String, Object> createExpectedQueryResult(List<Object> rows, int offset) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("total_rows", rows.size());
        result.put("offset", offset);
        return result;
    }

    public void testViewQuery() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        putDocs(db);
        TDView view = createView(db);

        TDStatus updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        // Query all rows:
        TDQueryOptions options = new TDQueryOptions();
        TDStatus status = new TDStatus();
        Map<String, Object> query = view.queryWithOptions(options);

        List<Object> expectedRows = new ArrayList<Object>();

        Map<String,Object> dict5 = new HashMap<String,Object>();
        dict5.put("id", "55555");
        dict5.put("key", "five");
        expectedRows.add(dict5);

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("id", "44444");
        dict4.put("key", "four");
        expectedRows.add(dict4);

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("id", "11111");
        dict1.put("key", "one");
        expectedRows.add(dict1);

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("id", "33333");
        dict3.put("key", "three");
        expectedRows.add(dict3);

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("id", "22222");
        dict2.put("key", "two");
        expectedRows.add(dict2);

        Map<String,Object> expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Start/end key query:
        options = new TDQueryOptions();
        options.setStartKey("a");
        options.setEndKey("one");

        status = new TDStatus();
        query = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);
        expectedRows.add(dict1);

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        status = new TDStatus();
        query = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Reversed:
        options.setDescending(true);
        options.setStartKey("o");
        options.setEndKey("five");
        options.setInclusiveEnd(true);

        status = new TDStatus();
        query = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict5);

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Reversed, no inclusive end:
        options.setInclusiveEnd(false);

        status = new TDStatus();
        query = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

    }

    public void testAllDocsQuery() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        List<TDRevision> docs = putDocs(db);

        List<Map<String,Object>> expectedRow = new ArrayList<Map<String,Object>>();
        for (TDRevision rev : docs) {
            Map<String,Object> value = new HashMap<String, Object>();
            value.put("rev", rev.getRevId());

            Map<String, Object> row = new HashMap<String, Object>();
            row.put("id", rev.getDocId());
            row.put("key", rev.getDocId());
            row.put("value", value);
            expectedRow.add(row);
        }

        TDQueryOptions options = new TDQueryOptions();
        Map<String,Object> query = db.getAllDocs(options);

        List<Object>expectedRows = new ArrayList<Object>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));

        Map<String,Object> expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Start/end key query:
        options = new TDQueryOptions();
        options.setStartKey("2");
        options.setEndKey("44444");

        query = db.getAllDocs(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        query = db.getAllDocs(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

    }

}
