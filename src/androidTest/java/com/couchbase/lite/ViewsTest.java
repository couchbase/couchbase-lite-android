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

import com.couchbase.lite.View.TDViewCollation;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.store.Store;
import com.couchbase.lite.store.StoreDelegate;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ViewsTest extends LiteTestCaseWithDB {

    public static final String TAG = "ViewsTest";

    public void testQueryDefaultIndexUpdateMode() {
        View view = database.getView("aview");
        Query query = view.createQuery();
        assertEquals(Query.IndexUpdateMode.BEFORE, query.getIndexUpdateMode());
    }

    /**
     * - (void) test01_Create
     */
    public void testViewCreation() {
        Assert.assertNull(database.getExistingView("aview"));

        View view = database.getView("aview");
        Assert.assertNotNull(view);
        Assert.assertEquals(database, view.getDatabase());
        Assert.assertEquals("aview", view.getName());
        Assert.assertNull(view.getMap());
        Assert.assertEquals(view, database.getExistingView("aview"));

        boolean changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, database.getAllViews().size());
        Assert.assertEquals(view, database.getAllViews().get(0));

        changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertFalse(changed);

        changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "2");

        Assert.assertTrue(changed);
    }

    //https://github.com/couchbase/couchbase-lite-java-core/issues/219
    public void testDeleteView() {
        List<View> views = database.getAllViews();
        for (View view : views) {
            view.delete();
        }

        Assert.assertEquals(0, database.getAllViews().size());
        Assert.assertEquals(null, database.getExistingView("viewToDelete"));

        View view = database.getView("viewToDelete");
        Assert.assertNotNull(view);
        Assert.assertEquals(database, view.getDatabase());
        Assert.assertEquals("viewToDelete", view.getName());
        Assert.assertNull(view.getMap());
        Assert.assertEquals(view, database.getExistingView("viewToDelete"));

        // NOTE: Forestdb view storage creates view db when constructor is called.
        //       But SQLite view storage does not create view record when constructor is called.
        if(isUseForestDB())
            Assert.assertEquals(1, database.getAllViews().size());
        else
            Assert.assertEquals(0, database.getAllViews().size());

        boolean changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, database.getAllViews().size());
        Assert.assertEquals(view, database.getAllViews().get(0));

        view.delete();
        //Status status = database.deleteViewNamed("viewToDelete");
        //Assert.assertEquals(Status.OK, status.getCode());
        Assert.assertEquals(0, database.getAllViews().size());

        View nullView = database.getExistingView("viewToDelete");
        Assert.assertNull("cached View is not deleted", nullView);

        view.delete();
        //status = database.deleteViewNamed("viewToDelete");
        //Assert.assertEquals(Status.NOT_FOUND, status.getCode());
    }

    private RevisionInternal putDoc(Database db, Map<String, Object> props)
            throws CouchbaseLiteException {
        RevisionInternal rev = new RevisionInternal(props);
        Status status = new Status();
        rev = db.putRevision(rev, null, false, status);
        Assert.assertTrue(status.isSuccessful());
        return rev;
    }

    private void putDocViaUntitledDoc(Database db, Map<String, Object> props)
            throws CouchbaseLiteException {
        Document document = db.createDocument();
        document.putProperties(props);
    }

    public List<RevisionInternal> putDocs(Database db)
            throws CouchbaseLiteException {
        List<RevisionInternal> result = new ArrayList<RevisionInternal>();

        Map<String, Object> dict2 = new HashMap<String, Object>();
        dict2.put("_id", "22222");
        dict2.put("key", "two");
        result.add(putDoc(db, dict2));

        Map<String, Object> dict4 = new HashMap<String, Object>();
        dict4.put("_id", "44444");
        dict4.put("key", "four");
        result.add(putDoc(db, dict4));

        Map<String, Object> dict1 = new HashMap<String, Object>();
        dict1.put("_id", "11111");
        dict1.put("key", "one");
        result.add(putDoc(db, dict1));

        Map<String, Object> dict3 = new HashMap<String, Object>();
        dict3.put("_id", "33333");
        dict3.put("key", "three");
        result.add(putDoc(db, dict3));

        Map<String, Object> dict5 = new HashMap<String, Object>();
        dict5.put("_id", "55555");
        dict5.put("key", "five");
        result.add(putDoc(db, dict5));

        return result;
    }

    // http://wiki.apache.org/couchdb/Introduction_to_CouchDB_views#Linked_documents
    public List<RevisionInternal> putLinkedDocs(Database db)
            throws CouchbaseLiteException {
        List<RevisionInternal> result = new ArrayList<RevisionInternal>();

        Map<String, Object> dict1 = new HashMap<String, Object>();
        dict1.put("_id", "11111");
        result.add(putDoc(db, dict1));

        Map<String, Object> dict2 = new HashMap<String, Object>();
        dict2.put("_id", "22222");
        dict2.put("value", "hello");
        dict2.put("ancestors", new String[]{"11111"});
        result.add(putDoc(db, dict2));

        Map<String, Object> dict3 = new HashMap<String, Object>();
        dict3.put("_id", "33333");
        dict3.put("value", "world");
        dict3.put("ancestors", new String[]{"22222", "11111"});
        result.add(putDoc(db, dict3));

        return result;
    }

    public void putNDocs(Database db, int n) throws CouchbaseLiteException {
        for (int i = 0; i < n; i++) {
            Map<String, Object> doc = new HashMap<String, Object>();
            doc.put("_id", String.format("%d", i));
            List<String> key = new ArrayList<String>();
            for (int j = 0; j < 256; j++) {
                key.add("key");
            }
            key.add(String.format("key-%d", i));
            doc.put("key", key);
            putDocViaUntitledDoc(db, doc);
        }
    }

    // - (CBLView*) createView
    public static View createView(Database db) {
        return createView(db, "aview");
    }

    // - (CBLView*) createViewNamed: (NSString*)name
    public static View createView(Database db, String name) {
        View view = db.getView(name);
        if (view != null) {
            view.setMapReduce(new Mapper() {
                @Override
                public void map(Map<String, Object> document, Emitter emitter) {
                    assertNotNull(document.get("_id"));
                    assertNotNull(document.get("_rev"));
                    assertNotNull(document.get("_local_seq"));
                    assertTrue(document.get("_local_seq") instanceof Number);
                    if (document.get("key") != null)
                        emitter.emit(document.get("key"), null);
                }
            }, null, "1");
        }
        return view;
    }

    /**
     * in ViewInternal_Tests.m
     * - (void) test02_Index
     */
    public void testViewIndex() throws CouchbaseLiteException {

        int numTimesMapFunctionInvoked = 0;

        Map<String, Object> dict1 = new HashMap<String, Object>();
        dict1.put("key", "one");
        Map<String, Object> dict2 = new HashMap<String, Object>();
        dict2.put("key", "two");
        Map<String, Object> dict3 = new HashMap<String, Object>();
        dict3.put("key", "three");
        Map<String, Object> dictW = new HashMap<String, Object>();
        dictW.put("_id", "_design/foo");
        Map<String, Object> dictX = new HashMap<String, Object>();
        dictX.put("clef", "quatre");

        RevisionInternal rev1 = putDoc(database, dict1);
        RevisionInternal rev2 = putDoc(database, dict2);
        RevisionInternal rev3 = putDoc(database, dict3);
        putDoc(database, dictW);
        putDoc(database, dictX);

        class InstrumentedMapBlock implements Mapper {

            int numTimesInvoked = 0;

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                numTimesInvoked += 1;
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if (document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }

            public int getNumTimesInvoked() {
                return numTimesInvoked;
            }

        }
        View view = database.getView("aview");
        InstrumentedMapBlock mapBlock = new InstrumentedMapBlock();
        view.setMap(mapBlock, "1");

        Assert.assertTrue(view.isStale());

        view.updateIndex();

        List<Map<String, Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(1, ((Number)dumpResult.get(0).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(2, ((Number)dumpResult.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(3, ((Number)dumpResult.get(1).get("seq")).intValue());

        //no-op reindex
        Assert.assertFalse(view.isStale());

        view.updateIndex();

        // Now add a doc and update a doc:
        RevisionInternal threeUpdated = new RevisionInternal(rev3.getDocID(), rev3.getRevID(), false);
        numTimesMapFunctionInvoked = mapBlock.getNumTimesInvoked();

        Map<String, Object> newdict3 = new HashMap<String, Object>();
        newdict3.put("key", "3hree");
        threeUpdated.setProperties(newdict3);
        Status status = new Status();
        rev3 = database.putRevision(threeUpdated, rev3.getRevID(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        view.updateIndex();

        // Make sure the map function was only invoked one more time (for the document that was added)
        Assert.assertEquals(numTimesMapFunctionInvoked + 1, mapBlock.getNumTimesInvoked());

        Map<String, Object> dict4 = new HashMap<String, Object>();
        dict4.put("key", "four");
        RevisionInternal rev4 = putDoc(database, dict4);

        RevisionInternal twoDeleted = new RevisionInternal(rev2.getDocID(), rev2.getRevID(), true);
        database.putRevision(twoDeleted, rev2.getRevID(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        view.updateIndex();

        dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(1, ((Number)dumpResult.get(2).get("seq")).intValue());
        Assert.assertEquals("\"3hree\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(6, ((Number)dumpResult.get(0).get("seq")).intValue());
        Assert.assertEquals("\"four\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(7, ((Number)dumpResult.get(1).get("seq")).intValue());

        // Now do a real query:
        List<QueryRow> rows = view.query(null);
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals("one", rows.get(2).getKey());
        Assert.assertEquals(rev1.getDocID(), rows.get(2).getDocumentId());
        Assert.assertEquals("3hree", rows.get(0).getKey());
        Assert.assertEquals(rev3.getDocID(), rows.get(0).getDocumentId());
        Assert.assertEquals("four", rows.get(1).getKey());
        Assert.assertEquals(rev4.getDocID(), rows.get(1).getDocumentId());

        view.deleteIndex();
    }

    public void testViewIndexSkipsDesignDocs() throws CouchbaseLiteException {
        View view = createView(database);

        Map<String, Object> designDoc = new HashMap<String, Object>();
        designDoc.put("_id", "_design/test");
        designDoc.put("key", "value");
        putDoc(database, designDoc);

        view.updateIndex();
        List<QueryRow> rows = view.query(null);
        assertEquals(0, rows.size());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/214
     */
    public void testViewIndexSkipsConflictingDesignDocs()
            throws CouchbaseLiteException {
        View view = createView(database);

        Map<String, Object> designDoc = new HashMap<String, Object>();
        designDoc.put("_id", "_design/test");
        designDoc.put("key", "value");
        RevisionInternal rev1 = putDoc(database, designDoc);

        designDoc.put("_rev", rev1.getRevID());
        designDoc.put("key", "value2a");
        RevisionInternal rev2a = new RevisionInternal(designDoc);
        database.putRevision(rev2a, rev1.getRevID(), true);
        designDoc.put("key", "value2b");
        RevisionInternal rev2b = new RevisionInternal(designDoc);
        database.putRevision(rev2b, rev1.getRevID(), true);

        view.updateIndex();
        List<QueryRow> rows = view.query(null);
        assertEquals(0, rows.size());
    }

    /**
     * - (void) test08_Query
     */
    public void testViewQuery()
            throws CouchbaseLiteException {

        putDocs(database);
        View view = createView(database);

        view.updateIndex();

        // Query all rows:
        QueryOptions options = new QueryOptions();
        List<QueryRow> rows = view.query(options);

        List<Object> expectedRows = new ArrayList<Object>();

        Map<String, Object> dict5 = new HashMap<String, Object>();
        dict5.put("id", "55555");
        dict5.put("key", "five");
        expectedRows.add(dict5);

        Map<String, Object> dict4 = new HashMap<String, Object>();
        dict4.put("id", "44444");
        dict4.put("key", "four");
        expectedRows.add(dict4);

        Map<String, Object> dict1 = new HashMap<String, Object>();
        dict1.put("id", "11111");
        dict1.put("key", "one");
        expectedRows.add(dict1);

        Map<String, Object> dict3 = new HashMap<String, Object>();
        dict3.put("id", "33333");
        dict3.put("key", "three");
        expectedRows.add(dict3);

        Map<String, Object> dict2 = new HashMap<String, Object>();
        dict2.put("id", "22222");
        dict2.put("key", "two");
        expectedRows.add(dict2);

        Assert.assertEquals(5, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());
        Assert.assertEquals(dict1.get("key"), rows.get(2).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(2).getValue());
        Assert.assertEquals(dict3.get("key"), rows.get(3).getKey());
        Assert.assertEquals(dict3.get("value"), rows.get(3).getValue());
        Assert.assertEquals(dict2.get("key"), rows.get(4).getKey());
        Assert.assertEquals(dict2.get("value"), rows.get(4).getValue());

        // Start/end key query:
        options = new QueryOptions();
        options.setStartKey("a");
        options.setEndKey("one");

        rows = view.query(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);
        expectedRows.add(dict1);

        Assert.assertEquals(3, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());
        Assert.assertEquals(dict1.get("key"), rows.get(2).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(2).getValue());


        // Start/end query without inclusive start:
        options.setInclusiveStart(false);
        options.setStartKey("five");
        rows = view.query(options);
        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict1.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(1).getValue());

        // Start/end query without inclusive end:
        options.setStartKey("a");
        options.setInclusiveStart(true);
        options.setInclusiveEnd(false);

        rows = view.query(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());

        // Reversed:
        options.setDescending(true);
        options.setStartKey("o");
        options.setEndKey("five");
        options.setInclusiveEnd(true);

        rows = view.query(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict5);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict5.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(1).getValue());

        // Reversed, no inclusive end:
        options.setInclusiveEnd(false);

        rows = view.query(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);

        Assert.assertEquals(1, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());

        // Limit:
        options = new QueryOptions();
        options.setLimit(2);
        rows = view.query(options);
        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());

        // Skip rows:
        options = new QueryOptions();
        options.setSkip(2);
        rows = view.query(options);
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals(dict1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict3.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict3.get("value"), rows.get(1).getValue());
        Assert.assertEquals(dict2.get("key"), rows.get(2).getKey());
        Assert.assertEquals(dict2.get("value"), rows.get(2).getValue());

        // Skip + limit:
        options.setLimit(1);
        rows = view.query(options);
        Assert.assertEquals(1, rows.size());
        Assert.assertEquals(dict1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(0).getValue());

        // Specific keys:
        options = new QueryOptions();
        List<Object> keys = new ArrayList<Object>();
        keys.add("two");
        keys.add("four");

        options.setKeys(keys);

        rows = view.query(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict2);
        expectedRows.add(dict4);

        assertNotNull(rows);
        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict2.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict2.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());
    }

    //https://github.com/couchbase/couchbase-lite-android/issues/314
    public void testViewQueryWithDictSentinel() throws CouchbaseLiteException {

        List<String> key1 = new ArrayList<String>();
        key1.add("red");
        key1.add("model1");
        Map<String, Object> dict1 = new HashMap<String, Object>();
        dict1.put("id", "11");
        dict1.put("key", key1);
        putDoc(database, dict1);

        List<String> key2 = new ArrayList<String>();
        key2.add("red");
        key2.add("model2");
        Map<String, Object> dict2 = new HashMap<String, Object>();
        dict2.put("id", "12");
        dict2.put("key", key2);
        putDoc(database, dict2);

        List<String> key3 = new ArrayList<String>();
        key3.add("green");
        key3.add("model1");
        Map<String, Object> dict3 = new HashMap<String, Object>();
        dict3.put("id", "21");
        dict3.put("key", key3);
        putDoc(database, dict3);

        List<String> key4 = new ArrayList<String>();
        key4.add("yellow");
        key4.add("model2");
        Map<String, Object> dict4 = new HashMap<String, Object>();
        dict4.put("id", "31");
        dict4.put("key", key4);
        putDoc(database, dict4);

        View view = createView(database);

        view.updateIndex();

        // Query all rows:
        QueryOptions options = new QueryOptions();
        List<QueryRow> rows = view.query(options);

        Assert.assertEquals(4, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((List) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((List) rows.get(1).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((List) rows.get(2).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"yellow", "model2"}, ((List) rows.get(3).getKey()).toArray()));

        // Start/end key query:
        options = new QueryOptions();
        options.setStartKey("a");
        options.setEndKey(Arrays.asList("red", new HashMap<String, Object>()));
        rows = view.query(options);
        Assert.assertEquals(3, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((List) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((List) rows.get(1).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((List) rows.get(2).getKey()).toArray()));

        // Start/end query without inclusive end:
        options.setEndKey(Arrays.asList("red", "model1"));
        options.setInclusiveEnd(false);
        rows = view.query(options);
        Assert.assertEquals(1, rows.size()); //1
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((List) rows.get(0).getKey()).toArray()));

        // Reversed:
        options = new QueryOptions();
        options.setStartKey(Arrays.asList("red", new HashMap<String, Object>()));
        options.setEndKey(Arrays.asList("green", "model1"));
        options.setDescending(true);
        rows = view.query(options);
        Assert.assertEquals(3, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((List) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((List) rows.get(1).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((List) rows.get(2).getKey()).toArray()));

        // Reversed, no inclusive end:
        options.setInclusiveEnd(false);
        rows = view.query(options);
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((List) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((List) rows.get(1).getKey()).toArray()));

        // Specific keys:
        options = new QueryOptions();
        List<Object> keys = new ArrayList<Object>();
        keys.add(new Object[]{"red", "model1"});
        keys.add(new Object[]{"red", "model2"});
        options.setKeys(keys);
        rows = view.query(options);
        assertNotNull(rows);
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((List) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((List) rows.get(1).getKey()).toArray()));
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/139
     * test based on https://github.com/couchbase/couchbase-lite-ios/blob/master/Source/CBL_View_Tests.m#L358
     */
    public void testViewQueryStartKeyDocID() throws CouchbaseLiteException {

        putDocs(database);
        List<RevisionInternal> result = new ArrayList<RevisionInternal>();
        Map<String, Object> dict = new HashMap<String, Object>();
        dict.put("_id", "11112");
        dict.put("key", "one");
        result.add(putDoc(database, dict));
        View view = createView(database);

        view.updateIndex();
        QueryOptions options = new QueryOptions();
        options.setStartKey("one");
        options.setStartKeyDocId("11112");
        options.setEndKey("three");
        List<QueryRow> rows = view.query(options);

        assertEquals(2, rows.size());
        assertEquals("11112", rows.get(0).getDocumentId());
        assertEquals("one", rows.get(0).getKey());
        assertEquals("33333", rows.get(1).getDocumentId());
        assertEquals("three", rows.get(1).getKey());

        options = new QueryOptions();
        options.setEndKey("one");
        options.setEndKeyDocId("11111");
        rows = view.query(options);

        Log.d(TAG, "rows: " + rows);
        assertEquals(3, rows.size());
        assertEquals("55555", rows.get(0).getDocumentId());
        assertEquals("five", rows.get(0).getKey());
        assertEquals("44444", rows.get(1).getDocumentId());
        assertEquals("four", rows.get(1).getKey());
        assertEquals("11111", rows.get(2).getDocumentId());
        assertEquals("one", rows.get(2).getKey());

        options.setStartKey("one");
        options.setStartKeyDocId("11111");
        rows = view.query(options);
        assertEquals(1, rows.size());
        assertEquals("11111", rows.get(0).getDocumentId());
        assertEquals("one", rows.get(0).getKey());

    }

    /**
     * - (void) test13_NumericKeys in ViewInternal_Tests.m
     * https://github.com/couchbase/couchbase-lite-android/issues/260
     */
    public void testViewNumericKeys() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<String, Object>();
        dict.put("_id", "22222");
        dict.put("referenceNumber", 33547239);
        dict.put("title", "this is the title");
        putDoc(database, dict);

        View view = createView(database);

        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.containsKey("referenceNumber")) {
                    emitter.emit(document.get("referenceNumber"), document);
                }

            }
        }, "1");

        Query query = view.createQuery();
        query.setStartKey(33547239);
        query.setEndKey(33547239);
        QueryEnumerator rows = query.run();
        assertEquals(1, rows.getCount());
        assertEquals(33547239, ((Number)rows.getRow(0).getKey()).intValue());
    }

    public void testAllDocsQuery() throws CouchbaseLiteException {

        List<RevisionInternal> docs = putDocs(database);

        List<QueryRow> expectedRow = new ArrayList<QueryRow>();
        for (RevisionInternal rev : docs) {
            Map<String, Object> value = new HashMap<String, Object>();
            value.put("rev", rev.getRevID());
            value.put("_conflicts", new ArrayList<String>());
            QueryRow queryRow = new QueryRow(rev.getDocID(), 0, rev.getDocID(), value, null, null);
            //queryRow.setDatabase(database);
            expectedRow.add(queryRow);
        }

        QueryOptions options = new QueryOptions();
        Map<String, Object> allDocs = database.getAllDocs(options);

        List<QueryRow> expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));

        Map<String, Object> expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Start/end key query:
        options = new QueryOptions();
        options.setStartKey("2");
        options.setEndKey("44444");

        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Get all documents: with default QueryOptions
        options = new QueryOptions();
        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);

        Assert.assertEquals(expectedQueryResult, allDocs);

        // Get specific documents:
        options = new QueryOptions();
        List<Object> docIds = new ArrayList<Object>();
        QueryRow expected2 = expectedRow.get(2);
        docIds.add(expected2.getDocumentId());
        //docIds.add(expected2.getDocument().getId());
        options.setKeys(docIds);
        allDocs = database.getAllDocs(options);
        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expected2);
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);
    }

    private Map<String, Object> createExpectedQueryResult(List<QueryRow> rows, int offset) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("total_rows", rows.size());
        result.put("offset", offset);
        return result;
    }

    /**
     * TODO: It seems this test is not correct and also LiveQuery is not correctly implemented. Fix this!!
     *
     * NOTE: ChangeNotification should not be fired for 0 match query.
     */
    public void failingTestAllDocumentsLiveQuery() throws CouchbaseLiteException {
        final AtomicInteger changeCount = new AtomicInteger();

        Database db = startDatabase();
        LiveQuery query = db.createAllDocumentsQuery().toLiveQuery();
        query.setStartKey("1");
        query.setEndKey("10");
        query.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                changeCount.incrementAndGet();
            }
        });

        query.start();
        query.waitForRows();
        assertNull(query.getLastError());
        QueryEnumerator rows = query.getRows();
        assertNotNull(rows);
        assertEquals(0, rows.getCount());

        // A change event is sent the first time a query finishes loading
        assertEquals(1, changeCount.get());

        db.getDocument("a").createRevision().save();

        query.waitForRows();
        // A change event is not sent, if the query results remain the same
        assertEquals(1, changeCount.get());
        rows = query.getRows();
        assertNotNull(rows);
        assertEquals(0, rows.getCount());

        db.getDocument("1").createRevision().save();

        // The query must update before sending notifications
        assertEquals(1, changeCount.get());
        query.waitForRows();
        assertEquals(2, changeCount.get());

        rows = query.getRows();
        assertNotNull(rows);
        assertEquals(1, rows.getCount());

        assertNull(query.getLastError());
        query.stop();
    }

    /**
     * in ViewInternal_Tests.m
     * - (void) test16_Reduce
     */
    public void testViewReduce() throws CouchbaseLiteException {

        Map<String, Object> docProperties1 = new HashMap<String, Object>();
        docProperties1.put("_id", "CD");
        docProperties1.put("cost", 8.99);
        putDoc(database, docProperties1);

        Map<String, Object> docProperties2 = new HashMap<String, Object>();
        docProperties2.put("_id", "App");
        docProperties2.put("cost", 1.95);
        putDoc(database, docProperties2);

        Map<String, Object> docProperties3 = new HashMap<String, Object>();
        docProperties3.put("_id", "Dessert");
        docProperties3.put("cost", 6.50);
        putDoc(database, docProperties3);

        View view = database.getView("totaler");
        view.setMapReduce(
                new Mapper() {
                    @Override
                    public void map(Map<String, Object> document, Emitter emitter) {
                        Assert.assertNotNull(document.get("_id"));
                        Assert.assertNotNull(document.get("_rev"));
                        Object cost = document.get("cost");
                        if (cost != null) {
                            emitter.emit(document.get("_id"), cost);
                        }
                    }
                },
                new Reducer() {
                    @Override
                    public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                        return View.totalValues(values);
                    }
                }, "1"
        );

        view.updateIndex();

        List<Map<String, Object>> dumpResult = view.dump();
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"App\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(1.95, dumpResult.get(0).get("value") instanceof String ? Double.parseDouble((String) dumpResult.get(0).get("value")) : dumpResult.get(0).get("value"));
        Assert.assertEquals(2, ((Number) dumpResult.get(0).get("seq")).intValue());
        Assert.assertEquals("\"CD\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(8.99, dumpResult.get(1).get("value") instanceof String ? Double.parseDouble((String) dumpResult.get(1).get("value")) : dumpResult.get(1).get("value"));
        Assert.assertEquals(1, ((Number) dumpResult.get(1).get("seq")).intValue());
        Assert.assertEquals("\"Dessert\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(6.5, dumpResult.get(2).get("value") instanceof String ? Double.parseDouble((String) dumpResult.get(2).get("value")) : dumpResult.get(2).get("value"));
        Assert.assertEquals(3, ((Number) dumpResult.get(2).get("seq")).intValue());

        QueryOptions options = new QueryOptions();
        options.setReduce(true);
        List<QueryRow> reduced = view.query(options);
        Assert.assertEquals(1, reduced.size());
        Object value = reduced.get(0).getValue();
        Number numberValue = (Number) value;
        Assert.assertTrue(Math.abs(numberValue.doubleValue() - 17.44) < 0.001);

    }

    public void testIndexUpdateMode() throws CouchbaseLiteException {

        View view = createView(database);
        Query query = view.createQuery();
        query.setIndexUpdateMode(Query.IndexUpdateMode.BEFORE);
        int numRowsBefore = query.run().getCount();
        assertEquals(0, numRowsBefore);

        // do a query and force re-indexing, number of results should be +4
        putNDocs(database, 1);
        query.setIndexUpdateMode(Query.IndexUpdateMode.BEFORE);
        assertEquals(1, query.run().getCount());

        // do a query without re-indexing, number of results should be the same
        putNDocs(database, 4);
        query.setIndexUpdateMode(Query.IndexUpdateMode.NEVER);
        assertEquals(1, query.run().getCount());

        // do a query and force re-indexing, number of results should be +4
        query.setIndexUpdateMode(Query.IndexUpdateMode.BEFORE);
        assertEquals(5, query.run().getCount());

        // do a query which will kick off an async index
        putNDocs(database, 1);
        query.setIndexUpdateMode(Query.IndexUpdateMode.AFTER);
        query.run().getCount();

        // wait until indexing is (hopefully) done
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(6, query.run().getCount());

    }

    public void testViewGrouped() throws CouchbaseLiteException {

        Map<String, Object> docProperties1 = new HashMap<String, Object>();
        docProperties1.put("_id", "1");
        docProperties1.put("artist", "Gang Of Four");
        docProperties1.put("album", "Entertainment!");
        docProperties1.put("track", "Ether");
        docProperties1.put("time", 231);
        putDoc(database, docProperties1);

        Map<String, Object> docProperties2 = new HashMap<String, Object>();
        docProperties2.put("_id", "2");
        docProperties2.put("artist", "Gang Of Four");
        docProperties2.put("album", "Songs Of The Free");
        docProperties2.put("track", "I Love A Man In Uniform");
        docProperties2.put("time", 248);
        putDoc(database, docProperties2);

        Map<String, Object> docProperties3 = new HashMap<String, Object>();
        docProperties3.put("_id", "3");
        docProperties3.put("artist", "Gang Of Four");
        docProperties3.put("album", "Entertainment!");
        docProperties3.put("track", "Natural's Not In It");
        docProperties3.put("time", 187);
        putDoc(database, docProperties3);

        Map<String, Object> docProperties4 = new HashMap<String, Object>();
        docProperties4.put("_id", "4");
        docProperties4.put("artist", "PiL");
        docProperties4.put("album", "Metal Box");
        docProperties4.put("track", "Memories");
        docProperties4.put("time", 309);
        putDoc(database, docProperties4);

        Map<String, Object> docProperties5 = new HashMap<String, Object>();
        docProperties5.put("_id", "5");
        docProperties5.put("artist", "Gang Of Four");
        docProperties5.put("album", "Entertainment!");
        docProperties5.put("track", "Not Great Men");
        docProperties5.put("time", 187);
        putDoc(database, docProperties5);

        View view = database.getView("grouper");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  List<Object> key = new ArrayList<Object>();
                                  key.add(document.get("artist"));
                                  key.add(document.get("album"));
                                  key.add(document.get("track"));
                                  emitter.emit(key, document.get("time"));
                              }
                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          }, "1"
        );

        Status status = new Status();
        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setReduce(true);
        List<QueryRow> rows = view.query(options);
        assertNotNull(rows);

        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("key", null);
        row1.put("value", 1162.0);
        expectedRows.add(row1);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());

        //now group
        options.setGroup(true);
        status = new Status();
        rows = view.query(options);

        expectedRows = new ArrayList<Map<String, Object>>();

        row1 = new HashMap<String, Object>();
        List<String> key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        key1.add("Ether");
        row1.put("key", key1);
        row1.put("value", 231.0);
        expectedRows.add(row1);

        Map<String, Object> row2 = new HashMap<String, Object>();
        List<String> key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Entertainment!");
        key2.add("Natural's Not In It");
        row2.put("key", key2);
        row2.put("value", 187.0);
        expectedRows.add(row2);

        Map<String, Object> row3 = new HashMap<String, Object>();
        List<String> key3 = new ArrayList<String>();
        key3.add("Gang Of Four");
        key3.add("Entertainment!");
        key3.add("Not Great Men");
        row3.put("key", key3);
        row3.put("value", 187.0);
        expectedRows.add(row3);

        Map<String, Object> row4 = new HashMap<String, Object>();
        List<String> key4 = new ArrayList<String>();
        key4.add("Gang Of Four");
        key4.add("Songs Of The Free");
        key4.add("I Love A Man In Uniform");
        row4.put("key", key4);
        row4.put("value", 248.0);
        expectedRows.add(row4);

        Map<String, Object> row5 = new HashMap<String, Object>();
        List<String> key5 = new ArrayList<String>();
        key5.add("PiL");
        key5.add("Metal Box");
        key5.add("Memories");
        row5.put("key", key5);
        row5.put("value", 309.0);
        expectedRows.add(row5);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());
        Assert.assertEquals(row4.get("key"), rows.get(3).getKey());
        Assert.assertEquals(row4.get("value"), rows.get(3).getValue());
        Assert.assertEquals(row5.get("key"), rows.get(4).getKey());
        Assert.assertEquals(row5.get("value"), rows.get(4).getValue());

        //group level 1
        options.setGroupLevel(1);
        status = new Status();
        rows = view.query(options);

        expectedRows = new ArrayList<Map<String, Object>>();

        row1 = new HashMap<String, Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        row1.put("key", key1);
        row1.put("value", 853.0);
        expectedRows.add(row1);

        row2 = new HashMap<String, Object>();
        key2 = new ArrayList<String>();
        key2.add("PiL");
        row2.put("key", key2);
        row2.put("value", 309.0);
        expectedRows.add(row2);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());

        //group level 2
        options.setGroupLevel(2);
        status = new Status();
        rows = view.query(options);

        expectedRows = new ArrayList<Map<String, Object>>();

        row1 = new HashMap<String, Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        row1.put("key", key1);
        row1.put("value", 605.0);
        expectedRows.add(row1);

        row2 = new HashMap<String, Object>();
        key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Songs Of The Free");
        row2.put("key", key2);
        row2.put("value", 248.0);
        expectedRows.add(row2);

        row3 = new HashMap<String, Object>();
        key3 = new ArrayList<String>();
        key3.add("PiL");
        key3.add("Metal Box");
        row3.put("key", key3);
        row3.put("value", 309.0);
        expectedRows.add(row3);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());

    }

    public void testViewGroupedStrings() throws CouchbaseLiteException {

        Map<String, Object> docProperties1 = new HashMap<String, Object>();
        docProperties1.put("name", "Alice");
        putDoc(database, docProperties1);

        Map<String, Object> docProperties2 = new HashMap<String, Object>();
        docProperties2.put("name", "Albert");
        putDoc(database, docProperties2);

        Map<String, Object> docProperties3 = new HashMap<String, Object>();
        docProperties3.put("name", "Naomi");
        putDoc(database, docProperties3);

        Map<String, Object> docProperties4 = new HashMap<String, Object>();
        docProperties4.put("name", "Jens");
        putDoc(database, docProperties4);

        Map<String, Object> docProperties5 = new HashMap<String, Object>();
        docProperties5.put("name", "Jed");
        putDoc(database, docProperties5);

        View view = database.getView("default/names");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  String name = (String) document.get("name");
                                  if (name != null) {
                                      emitter.emit(name.substring(0, 1), 1);
                                  }
                              }

                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return values.size();
                              }

                          }, "1.0"
        );


        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setGroupLevel(1);
        List<QueryRow> rows = view.query(options);
        assertNotNull(rows);

        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("key", "A");
        row1.put("value", 2);
        expectedRows.add(row1);
        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("key", "J");
        row2.put("value", 2);
        expectedRows.add(row2);
        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("key", "N");
        row3.put("value", 1);
        expectedRows.add(row3);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());

    }

    public void testViewGroupedNoReduce() throws CouchbaseLiteException {
        Map<String, Object> docProperties = new HashMap<String, Object>();
        docProperties.put("_id", "1");
        docProperties.put("type", "A");
        putDoc(database, docProperties);

        docProperties = new HashMap<String, Object>();
        docProperties.put("_id", "2");
        docProperties.put("type", "A");
        putDoc(database, docProperties);

        docProperties = new HashMap<String, Object>();
        docProperties.put("_id", "3");
        docProperties.put("type", "B");
        putDoc(database, docProperties);

        docProperties = new HashMap<String, Object>();
        docProperties.put("_id", "4");
        docProperties.put("type", "B");
        putDoc(database, docProperties);

        docProperties = new HashMap<String, Object>();
        docProperties.put("_id", "5");
        docProperties.put("type", "C");
        putDoc(database, docProperties);

        docProperties = new HashMap<String, Object>();
        docProperties.put("_id", "6");
        docProperties.put("type", "C");
        putDoc(database, docProperties);

        View view = database.getView("GroupByType");
        view.setMap(new Mapper() {
                        @Override
                        public void map(Map<String, Object> document, Emitter emitter) {
                            String type = (String) document.get("type");
                            if (type != null) {
                                emitter.emit(type, null);
                            }
                        }

                    }, "1.0"
        );

        view.updateIndex();
        QueryOptions options = new QueryOptions();
        //setGroup without reduce function
        options.setGroupLevel(1);
        List<QueryRow> rows = view.query(options);
        assertNotNull(rows);
        assertEquals(3, rows.size());

        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("key", "A");
        row1.put("error", "not_found");

        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("key", "B");
        row2.put("error", "not_found");

        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("key", "C");
        row3.put("error", "not_found");

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("error"), rows.get(0).asJSONDictionary().get("error"));
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("error"), rows.get(1).asJSONDictionary().get("error"));
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("error"), rows.get(2).asJSONDictionary().get("error"));
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/413
     */
    public void testViewGroupedNoReduceWithoutDocs() throws CouchbaseLiteException {
        View view = database.getView("GroupByType");
        view.setMap(new Mapper() {
                        @Override
                        public void map(Map<String, Object> document, Emitter emitter) {
                            String type = (String) document.get("type");
                            if (type != null) {
                                emitter.emit(type, null);
                            }
                        }
                    }, "1.0"
        );
        view.updateIndex();
        QueryOptions options = new QueryOptions();
        options.setGroupLevel(1);
        List<QueryRow> rows = view.query(options);
        assertNotNull(rows);
        assertEquals(0, rows.size());
    }

    public void testViewGroupedVariableLengthKey() throws CouchbaseLiteException {
        Map<String, Object> docProperties1 = new HashMap<String, Object>();
        docProperties1.put("_id", "H");
        docProperties1.put("atomic_number", 1);
        docProperties1.put("name", "Hydrogen");
        docProperties1.put("electrons", new Integer[]{1});
        putDoc(database, docProperties1);

        Map<String, Object> docProperties2 = new HashMap<String, Object>();
        docProperties2.put("_id", "He");
        docProperties2.put("atomic_number", 2);
        docProperties2.put("name", "Helium");
        docProperties2.put("electrons", new Integer[]{2});
        putDoc(database, docProperties2);

        Map<String, Object> docProperties3 = new HashMap<String, Object>();
        docProperties3.put("_id", "Ne");
        docProperties3.put("atomic_number", 10);
        docProperties3.put("name", "Neon");
        docProperties3.put("electrons", new Integer[]{2, 8});
        putDoc(database, docProperties3);

        Map<String, Object> docProperties4 = new HashMap<String, Object>();
        docProperties4.put("_id", "Na");
        docProperties4.put("atomic_number", 11);
        docProperties4.put("name", "Sodium");
        docProperties4.put("electrons", new Integer[]{2, 8, 1});
        putDoc(database, docProperties4);

        Map<String, Object> docProperties5 = new HashMap<String, Object>();
        docProperties5.put("_id", "Mg");
        docProperties5.put("atomic_number", 12);
        docProperties5.put("name", "Magnesium");
        docProperties5.put("electrons", new Integer[]{2, 8, 2});
        putDoc(database, docProperties5);

        Map<String, Object> docProperties6 = new HashMap<String, Object>();
        docProperties6.put("_id", "Cr");
        docProperties6.put("atomic_number", 24);
        docProperties6.put("name", "Chromium");
        docProperties6.put("electrons", new Integer[]{2, 8, 13, 1});
        putDoc(database, docProperties6);

        Map<String, Object> docProperties7 = new HashMap<String, Object>();
        docProperties7.put("_id", "Zn");
        docProperties7.put("atomic_number", 30);
        docProperties7.put("name", "Zinc");
        docProperties7.put("electrons", new Integer[]{2, 8, 18, 2});
        putDoc(database, docProperties7);
        
        /*
            expected key-value pairs at group level 2:
              [1] -> 1
              [2] -> 1
              [2, 8] -> 5
        */

        View view = database.getView("electrons");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  emitter.emit(document.get("electrons"), 1);
                              }
                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          }, "1"
        );

        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setReduce(true);
        options.setGroupLevel(2);
        List<QueryRow> rows = view.query(options);
        assertNotNull(rows);
        assertEquals(3, rows.size());

        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("key", Arrays.asList(new Integer[]{1}));
        row1.put("value", 1.0);
        expectedRows.add(row1);
        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("key", Arrays.asList(new Integer[]{2}));
        row2.put("value", 1.0);
        expectedRows.add(row2);
        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("key", Arrays.asList(new Integer[]{2, 8}));
        row3.put("value", 5.0);
        expectedRows.add(row3);

        Assert.assertEquals(row1.get("key"), toIntegerList((List<Number>) rows.get(0).getKey()));
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), toIntegerList((List<Number>) rows.get(1).getKey()));
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), toIntegerList((List<Number>) rows.get(2).getKey()));
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());
    }


    private static List<Integer> toIntegerList(List<Number> src){
        if(src == null) return null;

        List<Integer> dest = new ArrayList<Integer>(src.size());
        for(Number n : src){
            dest.add(n.intValue());
        }
        return dest;
    }

    public void testViewCollation() throws CouchbaseLiteException {
        List<Object> list1 = new ArrayList<Object>();
        list1.add("a");

        List<Object> list2 = new ArrayList<Object>();
        list2.add("b");

        List<Object> list3 = new ArrayList<Object>();
        list3.add("b");
        list3.add("c");

        List<Object> list4 = new ArrayList<Object>();
        list4.add("b");
        list4.add("c");
        list4.add("a");

        List<Object> list5 = new ArrayList<Object>();
        list5.add("b");
        list5.add("d");

        List<Object> list6 = new ArrayList<Object>();
        list6.add("b");
        list6.add("d");
        list6.add("e");


        // Based on CouchDB's "view_collation.js" test
        List<Object> testKeys = new ArrayList<Object>();
        testKeys.add(null);
        testKeys.add(false);
        testKeys.add(true);
        testKeys.add(0);
        testKeys.add(2.5);
        testKeys.add(10);
        testKeys.add(" ");
        testKeys.add("_");
        testKeys.add("~");
        testKeys.add("a");
        testKeys.add("A");
        testKeys.add("aa");
        testKeys.add("b");
        testKeys.add("B");
        testKeys.add("ba");
        testKeys.add("bb");
        testKeys.add(list1);
        testKeys.add(list2);
        testKeys.add(list3);
        testKeys.add(list4);
        testKeys.add(list5);
        testKeys.add(list6);

        int i = 0;
        for (Object key : testKeys) {
            Map<String, Object> docProperties = new HashMap<String, Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(database, docProperties);
        }

        View view = database.getView("default/names");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        QueryOptions options = new QueryOptions();
        List<QueryRow> rows = view.query(options);
        i = 0;
        for (QueryRow row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.getKey());
        }
    }


    public void testViewCollationRaw() throws CouchbaseLiteException {
        List<Object> list1 = new ArrayList<Object>();
        list1.add("a");

        List<Object> list2 = new ArrayList<Object>();
        list2.add("b");

        List<Object> list3 = new ArrayList<Object>();
        list3.add("b");
        list3.add("c");

        List<Object> list4 = new ArrayList<Object>();
        list4.add("b");
        list4.add("c");
        list4.add("a");

        List<Object> list5 = new ArrayList<Object>();
        list5.add("b");
        list5.add("d");

        List<Object> list6 = new ArrayList<Object>();
        list6.add("b");
        list6.add("d");
        list6.add("e");


        // Based on CouchDB's "view_collation.js" test
        List<Object> testKeys = new ArrayList<Object>();
        testKeys.add(0);
        testKeys.add(2.5);
        testKeys.add(10);
        testKeys.add(false);
        testKeys.add(null);
        testKeys.add(true);
        testKeys.add(list1);
        testKeys.add(list2);
        testKeys.add(list3);
        testKeys.add(list4);
        testKeys.add(list5);
        testKeys.add(list6);
        testKeys.add(" ");
        testKeys.add("A");
        testKeys.add("B");
        testKeys.add("_");
        testKeys.add("a");
        testKeys.add("aa");
        testKeys.add("b");
        testKeys.add("ba");
        testKeys.add("bb");
        testKeys.add("~");

        int i = 0;
        for (Object key : testKeys) {
            Map<String, Object> docProperties = new HashMap<String, Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(database, docProperties);
        }

        View view = database.getView("default/names");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        view.setCollation(TDViewCollation.TDViewCollationRaw);

        QueryOptions options = new QueryOptions();

        List<QueryRow> rows = view.query(options);
        i = 0;
        for (QueryRow row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.getKey());
        }

        database.close();
    }

    public void testLargerViewQuery() throws CouchbaseLiteException {
        putNDocs(database, 4);
        View view = createView(database);

        view.updateIndex();

        // Query all rows:
        QueryOptions options = new QueryOptions();
        Status status = new Status();
        List<QueryRow> rows = view.query(options);
    }

    // http://wiki.apache.org/couchdb/Introduction_to_CouchDB_views#Linked_documents
    public void testViewLinkedDocs() throws CouchbaseLiteException {
        putLinkedDocs(database);

        View view = database.getView("linked");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.containsKey("value")) {
                    emitter.emit(new Object[]{document.get("value"), 0}, null);
                }
                if (document.containsKey("ancestors")) {
                    List<Object> ancestors = (List<Object>) document.get("ancestors");
                    for (int i = 0; i < ancestors.size(); i++) {
                        Map<String, Object> value = new HashMap<String, Object>();
                        value.put("_id", ancestors.get(i));
                        emitter.emit(new Object[]{document.get("value"), i + 1}, value);
                    }
                }
            }
        }, null, "1");

        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setIncludeDocs(true);  // required for linked documents

        List<QueryRow> rows = view.query(options);

        Assert.assertNotNull(rows);
        Assert.assertEquals(5, rows.size());

        Object[][] expected = new Object[][]{
                /* id, key0, key1, value._id, doc._id */
                new Object[]{"22222", "hello", 0, null, "22222"},
                new Object[]{"22222", "hello", 1, "11111", "11111"},
                new Object[]{"33333", "world", 0, null, "33333"},
                new Object[]{"33333", "world", 1, "22222", "22222"},
                new Object[]{"33333", "world", 2, "11111", "11111"},
        };

        for (int i = 0; i < rows.size(); i++) {
            QueryRow row = rows.get(i);

            Map<String, Object> rowAsJson = row.asJSONDictionary();
            Log.d(TAG, "" + rowAsJson);
            List<Object> key = (List<Object>) rowAsJson.get("key");
            Map<String, Object> doc = (Map<String, Object>) rowAsJson.get("doc");
            String id = (String) rowAsJson.get("id");

            Assert.assertEquals(expected[i][0], id);
            Assert.assertEquals(2, key.size());
            Assert.assertEquals(expected[i][1], key.get(0));
            Assert.assertEquals(expected[i][2], ((Number) key.get(1)).intValue());
            if (expected[i][3] == null) {
                Assert.assertNull(row.getValue());
            } else {
                Assert.assertEquals(expected[i][3], ((Map<String, Object>) row.getValue()).get("_id"));
            }
            Assert.assertEquals(expected[i][4], doc.get("_id"));
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/29
     */
    public void testRunLiveQueriesWithReduce() throws Exception {

        final Database db = startDatabase();
        // run a live query
        View view = db.getView("vu");
        view.setMapReduce(new Mapper() {
                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  emitter.emit(document.get("sequence"), 1);
                              }
                          }, new Reducer() {
                              @Override
                              public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          },
                "1"
        );
        final LiveQuery query = view.createQuery().toLiveQuery();

        View view1 = db.getView("vu1");
        view1.setMapReduce(new Mapper() {
                               @Override
                               public void map(Map<String, Object> document, Emitter emitter) {
                                   emitter.emit(document.get("sequence"), 1);
                               }
                           }, new Reducer() {
                               @Override
                               public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                                   return View.totalValues(values);
                               }
                           },
                "1"
        );
        final LiveQuery query1 = view1.createQuery().toLiveQuery();

        final int kNDocs = 10;
        createDocumentsAsync(db, kNDocs);

        assertNull(query.getRows());
        query.start();

        final CountDownLatch gotExpectedQueryResult = new CountDownLatch(1);

        query.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                if (event.getError() != null) {
                    Log.e(TAG, "LiveQuery change event had error", event.getError());
                } else if (event.getRows().getCount() == 1 && ((Double) event.getRows().getRow(0).getValue()).intValue() == kNDocs) {
                    gotExpectedQueryResult.countDown();
                }
            }
        });
        boolean success = gotExpectedQueryResult.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(success);

        query.stop();


        query1.start();

        createDocumentsAsync(db, kNDocs + 5);//10 + 10 + 5

        final CountDownLatch gotExpectedQuery1Result = new CountDownLatch(1);
        query1.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                if (event.getError() != null) {
                    Log.e(TAG, "LiveQuery change event had error", event.getError());
                } else if (event.getRows().getCount() == 1 && ((Double) event.getRows().getRow(0).getValue()).intValue() == 2 * kNDocs + 5) {
                    gotExpectedQuery1Result.countDown();
                }
            }
        });
        success = gotExpectedQuery1Result.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(success);

        query1.stop();

        assertEquals(2 * kNDocs + 5, db.getDocumentCount()); // 25 - OK


    }

    private SavedRevision createTestRevisionNoConflicts(Document doc, String val) throws Exception {
        UnsavedRevision unsavedRev = doc.createRevision();
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("key", val);
        unsavedRev.setUserProperties(props);
        return unsavedRev.save();
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/131
     */
    public void testViewWithConflict() throws Exception {

        // Create doc and add some revs
        Document doc = database.createDocument();
        SavedRevision rev1 = createTestRevisionNoConflicts(doc, "1");
        SavedRevision rev2a = createTestRevisionNoConflicts(doc, "2a");
        SavedRevision rev3 = createTestRevisionNoConflicts(doc, "3");

        // index the view
        View view = createView(database);
        QueryEnumerator rows = view.createQuery().run();

        assertEquals(1, rows.getCount());
        QueryRow row = rows.next();
        assertEquals(row.getKey(), "3");
        // assertNotNull(row.getDocumentRevisionId()); -- TODO: why is this null?

        // Create a conflict
        UnsavedRevision rev2bUnsaved = rev1.createRevision();
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("key", "2b");
        rev2bUnsaved.setUserProperties(props);
        SavedRevision rev2b = rev2bUnsaved.save(true);

        // re-run query
        view.updateIndex();
        rows = view.createQuery().run();

        // we should only see one row, with key=3.
        // if we see key=2b then it's a bug.
        assertEquals(1, rows.getCount());
        row = rows.next();
        assertEquals(row.getKey(), "3");

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/226
     */
    public void testViewSecondQuery() throws Exception {

        // Create doc and add some revs
        final Document doc = database.createDocument();
        String jsonString = "{\n" +
                "    \"name\":\"praying mantis\",\n" +
                "    \"wikipedia\":{\n" +
                "        \"behavior\":{\n" +
                "            \"style\":\"predatory\",\n" +
                "            \"attack\":\"ambush\"\n" +
                "        },\n" +
                "        \"evolution\":{\n" +
                "            \"ancestor\":\"proto-roaches\",\n" +
                "            \"cousin\":\"termite\"\n" +
                "        }       \n" +
                "    }   \n" +
                "\n" +
                "}";

        Map jsonObject = (Map) Manager.getObjectMapper().readValue(jsonString, Object.class);
        doc.putProperties(jsonObject);

        View view = database.getView("testViewSecondQueryView");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("name") != null) {
                    emitter.emit(document.get("name"), document);
                }
            }
        }, null, "1");

        for (int i = 0; i < 2; i++) {

            Query query = view.createQuery();
            QueryEnumerator rows = query.run();

            for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
                QueryRow row = it.next();
                Map wikipediaField = (Map) row.getDocument().getProperty("wikipedia");
                assertTrue(wikipediaField.containsKey("behavior"));
                assertTrue(wikipediaField.containsKey("evolution"));
                Map behaviorField = (Map) wikipediaField.get("behavior");
                assertTrue(behaviorField.containsKey("style"));
                assertTrue(behaviorField.containsKey("attack"));
            }
        }
    }

    public void testStringPrefixMatch() throws Exception {
        putDocs(database);
        View view = createView(database);

        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setPrefixMatchLevel(1);
        options.setStartKey("f");
        options.setEndKey("f");
        List<QueryRow> rows = view.query(options);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals("five", rows.get(0).getKey());
        Assert.assertEquals("four", rows.get(1).getKey());
    }

    public void testArrayPrefixMatch() throws Exception {
        putDocs(database);

        View view = database.getView("aview");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                String key = (String) document.get("key");
                if (key != null) {
                    String first = key.substring(0, 1);
                    Log.w(TAG, "first=%s", first);
                    emitter.emit(Arrays.asList(first, key), null);
                }
            }
        }, null, "1");

        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setPrefixMatchLevel(1);
        options.setStartKey(Arrays.asList("f"));
        options.setEndKey(options.getStartKey());
        List<QueryRow> rows = view.query(options);
        assertNotNull(rows);
        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(Arrays.asList("f", "five"), rows.get(0).getKey());
        Assert.assertEquals(Arrays.asList("f", "four"), rows.get(1).getKey());
    }

    public void testAllDocsPrefixMatch() throws CouchbaseLiteException {
        database.getDocument("aaaaaaa").createRevision().save();
        database.getDocument("a11zzzzz").createRevision().save();
        database.getDocument("a七乃又直ந்த").createRevision().save();
        database.getDocument("A1").createRevision().save();
        database.getDocument("bcd").createRevision().save();
        database.getDocument("01234").createRevision().save();

        QueryOptions options = new QueryOptions();
        options.setPrefixMatchLevel(1);
        options.setStartKey("a");
        options.setEndKey("a");

        Map<String, Object> result = database.getAllDocs(options);
        assertNotNull(result);
        List<QueryRow> rows = (List<QueryRow>) result.get("rows");
        assertNotNull(rows);

        // 1 < a < 七 - order is ascending by default
        assertEquals(3, rows.size());
        assertEquals("a11zzzzz", rows.get(0).getKey());
        assertEquals("aaaaaaa", rows.get(1).getKey());
        assertEquals("a七乃又直ந்த", rows.get(2).getKey());
    }

    /**
     * in View_Tests.m
     * - (void) test06_ViewCustomFilter
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/303
     */
    public void testViewCustomFilter() throws Exception {
        View view = database.getView("vu");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), document.get("skin"));
            }
        }, null, "1");

        Map<String, Object> docProperties1 = new HashMap<String, Object>();
        docProperties1.put("name", "Barry");
        docProperties1.put("skin", "none");
        putDoc(database, docProperties1);
        Map<String, Object> docProperties2 = new HashMap<String, Object>();
        docProperties2.put("name", "Terry");
        docProperties2.put("skin", "furry");
        putDoc(database, docProperties2);
        Map<String, Object> docProperties3 = new HashMap<String, Object>();
        docProperties3.put("name", "Wanda");
        docProperties3.put("skin", "scaly");
        putDoc(database, docProperties3);

        // match all
        Query query = view.createQuery();
        Predicate<QueryRow> postFilterAll = new Predicate<QueryRow>() {
            public boolean apply(QueryRow type) {
                return true;
            }
        };
        query.setPostFilter(postFilterAll);
        QueryEnumerator rows = query.run();
        assertEquals(3, rows.getCount());
        for (int i = 0; i < rows.getCount(); i++) {
            Log.d(Log.TAG_QUERY, "" + rows.getRow(i).getKey() + " => " + rows.getRow(i).getValue());
        }
        assertEquals(docProperties1.get("name"), rows.getRow(0).getKey());
        assertEquals(docProperties1.get("skin"), rows.getRow(0).getValue());
        assertEquals(docProperties2.get("name"), rows.getRow(1).getKey());
        assertEquals(docProperties2.get("skin"), rows.getRow(1).getValue());
        assertEquals(docProperties3.get("name"), rows.getRow(2).getKey());
        assertEquals(docProperties3.get("skin"), rows.getRow(2).getValue());


        // match  zero
        Predicate<QueryRow> postFilterNone = new Predicate<QueryRow>() {
            public boolean apply(QueryRow type) {
                return false;
            }
        };
        query.setPostFilter(postFilterNone);
        rows = query.run();
        assertEquals(0, rows.getCount());


        // match two
        Predicate<QueryRow> postFilter = new Predicate<QueryRow>() {
            public boolean apply(QueryRow type) {
                if (type.getValue() instanceof String) {
                    String val = (String) type.getValue();
                    if (val != null && val.endsWith("y")) {
                        return true;
                    }
                }
                return false;
            }
        };
        query.setPostFilter(postFilter);
        rows = query.run();
        assertEquals(2, rows.getCount());
        assertEquals(docProperties2.get("name"), rows.getRow(0).getKey());
        assertEquals(docProperties2.get("skin"), rows.getRow(0).getValue());
        assertEquals(docProperties3.get("name"), rows.getRow(1).getKey());
        assertEquals(docProperties3.get("skin"), rows.getRow(1).getValue());
    }

    /**
     * in View_Tests.m
     * - (void) test06_AllDocsCustomFilter
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/303
     */
    public void testAllDocsCustomFilter() throws Exception {
        Map<String, Object> docProperties1 = new HashMap<String, Object>();
        docProperties1.put("_id", "1");
        docProperties1.put("name", "Barry");
        docProperties1.put("skin", "none");
        putDoc(database, docProperties1);
        Map<String, Object> docProperties2 = new HashMap<String, Object>();
        docProperties2.put("_id", "2");
        docProperties2.put("name", "Terry");
        docProperties2.put("skin", "furry");
        putDoc(database, docProperties2);
        Map<String, Object> docProperties3 = new HashMap<String, Object>();
        docProperties3.put("_id", "3");
        docProperties3.put("name", "Wanda");
        docProperties3.put("skin", "scaly");
        putDoc(database, docProperties3);
        database.clearDocumentCache();

        Log.d(TAG, "---- QUERYIN' ----");
        Query query = database.createAllDocumentsQuery();
        query.setPostFilter(new Predicate<QueryRow>() {
            public boolean apply(QueryRow type) {
                if (type.getDocument().getProperty("skin") != null && type.getDocument().getProperty("skin") instanceof String) {
                    String skin = (String) type.getDocument().getProperty("skin");
                    if (skin.endsWith("y")) {
                        return true;
                    }
                }
                return false;
            }
        });
        QueryEnumerator rows = query.run();
        assertEquals(2, rows.getCount());
        assertEquals(docProperties2.get("_id"), rows.getRow(0).getKey());
        assertEquals(docProperties3.get("_id"), rows.getRow(1).getKey());
    }

    public void testQueryEnumerationImplementsIterable() {
        assertTrue(new QueryEnumerator(null, new ArrayList<QueryRow>(), 0) instanceof Iterable);
    }

    /**
     * int ViewInternal_Tests.m
     * - (void) test_ConflictWinner
     */
    public void testConflictWinner() throws CouchbaseLiteException {
        // If a view is re-indexed, and a document in the view has gone into conflict,
        // rows emitted by the earlier 'losing' revision shouldn't appear in the view.
        List<RevisionInternal> docs = putDocs(database);
        RevisionInternal leaf1 = docs.get(1);

        View view = createView(database);
        view.updateIndex();
        List<Map<String, Object>> dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, ((Number)dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());

        // Create a conflict, won by the new revision:
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "44444");
        //props.put("_rev", "1-~~~~~");  // higher revID, will win conflict
        props.put("_rev", "1-ffffff");  // higher revID, will win conflict
        props.put("key", "40ur");
        RevisionInternal leaf2 = new RevisionInternal(props);
        database.forceInsert(leaf2, new ArrayList<String>(), null);
        Assert.assertEquals(leaf1.getDocID(), leaf2.getDocID());

        // Update the view -- should contain only the key from the new rev, not the old:
        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"40ur\"", dump.get(0).get("key"));
        Assert.assertEquals(6, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"five\"", dump.get(1).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());
    }

    /**
     * int ViewInternal_Tests.m
     * - (void) test_ConflictWinner
     *
     * https://github.com/couchbase/couchbase-lite-android/issues/494
     */
    public void testConflictLoser() throws CouchbaseLiteException {
        // Like the ConflictWinner test, except the newer revision is the loser,
        // so it shouldn't be indexed at all. Instead, the older still-winning revision
        // should be indexed again.
        List<RevisionInternal> docs = putDocs(database);
        RevisionInternal leaf1 = docs.get(1);

        View view = createView(database);
        view.updateIndex();
        List<Map<String, Object>> dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, ((Number)dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());

        // Create a conflict, won by the new revision:
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "44444");
        //props.put("_rev", "1-...."); // lower revID, will lose conflict
        props.put("_rev", "1-0000"); // lower revID, will lose conflict
        props.put("key", "40ur");
        RevisionInternal leaf2 = new RevisionInternal(props);
        database.forceInsert(leaf2, new ArrayList<String>(), null);
        Assert.assertEquals(leaf1.getDocID(), leaf2.getDocID());

        // Update the view -- should contain only the key from the new rev, not the old:
        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, ((Number)dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/494
     *
     * in ViewInternal_tests.m
     * - (void) test_IndexingOlderRevision
     */
    public void testIndexingOlderRevision() throws CouchbaseLiteException {
        // In case conflictWinner was deleted, conflict loser should be indexed.

        // create documents
        List<RevisionInternal> docs = putDocs(database);
        RevisionInternal leaf1 = docs.get(1);

        Assert.assertEquals("four", database.getDocument("44444").getProperty("key"));

        // update index
        View view = createView(database);
        view.updateIndex();
        List<Map<String, Object>> dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, ((Number)dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());

        // Create a conflict, won by the new revision:
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "44444");
        props.put("_rev", "1-FFFFFFFF");  // higher revID, will win conflict
        props.put("key", "40ur");
        RevisionInternal leaf2 = new RevisionInternal(props);
        database.forceInsert(leaf2, new ArrayList<String>(), null);
        Assert.assertEquals(leaf1.getDocID(), leaf2.getDocID());

        Assert.assertEquals("40ur", database.getDocument("44444").getProperty("key"));

        // Update the view -- should contain only the key from the new rev, not the old:
        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"40ur\"", dump.get(0).get("key"));
        Assert.assertEquals(6, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"five\"", dump.get(1).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());

        // create new revision with delete
        RevisionInternal leaf3 = new RevisionInternal("44444", null, true);
        leaf3 = database.putRevision(leaf3, leaf2.getRevID(), true);
        Assert.assertEquals(leaf1.getDocID(), leaf3.getDocID());
        Assert.assertEquals(true, leaf3.isDeleted());

        Assert.assertEquals("four", database.getDocument("44444").getProperty("key"));

        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        long forSeq = (isSQLiteDB()?leaf1.getSequence():leaf3.getSequence());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, ((Number)dump.get(0).get("seq")).intValue());
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(forSeq, ((Number) dump.get(1).get("seq")).intValue());
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, ((Number)dump.get(2).get("seq")).intValue());
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, ((Number)dump.get(3).get("seq")).intValue());
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, ((Number)dump.get(4).get("seq")).intValue());
    }

    /**
     * LiveQuery should re-run query from scratch after options are changed
     * https://github.com/couchbase/couchbase-lite-ios/issues/596
     * <p/>
     * in View_Tests.m
     * - (void) test13_LiveQuery_UpdateWhenQueryOptionsChanged
     */
    public void testLiveQueryUpdateWhenQueryOptionsChanged() throws CouchbaseLiteException, InterruptedException {
        View view = database.getView("vu");
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");

        createDocuments(database, 5);

        Query query = view.createQuery();
        QueryEnumerator rows = query.run();

        assertEquals(5, rows.getCount());

        int expectedKey = 0;
        for (QueryRow row : rows) {
            assertEquals(((Number)row.getKey()).intValue(), expectedKey++);
        }

        LiveQuery liveQuery = view.createQuery().toLiveQuery();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);
        liveQuery.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                Log.d(TAG, "---- changed ---");
                latch1.countDown();
                latch2.countDown();
            }
        });
        liveQuery.start();

        boolean success1 = latch1.await(3, TimeUnit.SECONDS);
        assertTrue(success1);

        rows = liveQuery.getRows();
        assertNotNull(rows);
        assertEquals(5, rows.getCount());
        expectedKey = 0;
        for (QueryRow row : rows) {
            assertEquals(((Number)row.getKey()).intValue(), expectedKey++);
        }

        liveQuery.setStartKey(2);
        liveQuery.queryOptionsChanged();

        boolean success2 = latch2.await(3, TimeUnit.SECONDS);
        assertTrue(success2);

        rows = liveQuery.getRows();
        assertNotNull(rows);
        assertEquals(3, rows.getCount());
        expectedKey = 2;
        for (QueryRow row : rows) {
            assertEquals(((Number)row.getKey()).intValue(), expectedKey++);
        }

        liveQuery.stop();
    }

    /**
     * Tests that when converting from a Query to a LiveQuery, all properties are preserved.
     * More speficially tests that the Query copy constructor copies all fields.
     *
     * Regression test for couchbase/couchbase-lite-java-core#585.
     */
    public void testQueryToLiveQuery() throws Exception {
        View view = database.getView("vu");
        Query query = view.createQuery();

        query.setAllDocsMode(Query.AllDocsMode.INCLUDE_DELETED);
        assertTrue(query.shouldIncludeDeleted());
        query.setPrefetch(true);
        query.setPrefixMatchLevel(1);
        query.setStartKey(Arrays.asList("hello", "wo"));
        query.setStartKey(Arrays.asList("hello", "wo", Collections.EMPTY_MAP));
        query.setKeys(Arrays.<Object>asList("1", "5", "aaaaa"));
        query.setGroupLevel(2);
        query.setPrefixMatchLevel(2);
        query.setStartKeyDocId("1");
        query.setEndKeyDocId("123456789");
        query.setDescending(true);
        query.setLimit(10);
        query.setIndexUpdateMode(Query.IndexUpdateMode.NEVER);
        query.setSkip(2);
        query.setPostFilter(new Predicate<QueryRow>() {
            @Override
            public boolean apply(QueryRow type) {
                return true;
            }
        });
        query.setMapOnly(true);

        // Lets also test the copy constructor itself
        // But first ensure our assumptions hold
        if (Modifier.isAbstract(Query.class.getModifiers())) {
            fail("Assumption failed: test needs to be updated");
        }
        if (Query.class.getSuperclass() != Object.class) {
            // sameFields(Object, Object) does not compare fields of superclasses
            fail("Assumption failed: test needs to be updated");
        }
        Constructor<Query> copyConstructor = null;
        try {
            copyConstructor = Query.class.getDeclaredConstructor(Database.class, Query.class);
        } catch (NoSuchMethodException e) {
            fail("Copy constructor not found");
        }
        if (Modifier.isPrivate(copyConstructor.getModifiers())) {
            fail("Copy constructor is private");
        }

        // Constructor is package protected
        copyConstructor.setAccessible(true);

        // Now make some copies
        LiveQuery copy1 = query.toLiveQuery();
        Query copy2 = copyConstructor.newInstance(query.getDatabase(), query);

        sameFields(query, copy1);
        sameFields(query, copy2);
    }

    private static <T> void sameFields(T expected, T actual) throws Exception {
        // Compare the fields in the expected class (Query)
        // and not the ones in actual, as it may be a subclass (LiveQuery)
        assertNotNull(actual);
        Class<?> clazz = expected.getClass();
        Field[] fields = clazz.getDeclaredFields();

        boolean compared = false;
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object expectedObj = field.get(expected);
            Object actualObj = field.get(actual);
            assertEquals(expectedObj, actualObj);
            compared = true;
        }
        assertTrue("No fields to compare?!?", compared);
    }

    public void testDeleteIndexTest() {
        View newView = database.getView("newview");
        newView.deleteIndex();
        newView.setMap(
                new Mapper() {
                    @Override
                    public void map(Map<String, Object> documentProperties, Emitter emitter) {
                        String documentId = (String) documentProperties.get("_id");
                        emitter.emit(documentId, "Document id is " + documentId);
                    }
                },
                "1"
        );
        Query query = database.getView("newview").createQuery();
        QueryEnumerator queryResult = null;
        try {
            queryResult = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Failed to run Query.run() for indexDeleted", e);
            fail("Failed to run Query.run() for indexDeleted");
        }

        for (Iterator<QueryRow> it = queryResult.iterator(); it.hasNext(); ) {
            QueryRow row = it.next();
            Log.i("deleteIndexTest", (String) row.getValue());
        }
    }

    /**
     * View update skips winning revisions
     * https://github.com/couchbase/couchbase-lite-java-core/issues/709
     */
    public void testViewUpdateSkipsWinningRevisions() throws CouchbaseLiteException{

        // seq  | doc_id | revid | parent | current | deleted
        // -----+--------+-------+--------+---------+--------
        // 2753 | 172    | 1-6b  | NULL   | 0       | 0
        // 2754 | 172    | 2-1a  | 2753   | 0       | 0
        // 2761 | 172    | 3-d7  | 2754   | 0       | 0
        // 2763 | 172    | 3-06  | 2754   | 1       | 0
        // 2764 | 172    | 4-80  | 2761   | 1       | 1

        // create view
        View view = createView(database);

        // crete doc
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "172");
        props.put("key", "1");
        RevisionInternal rev1 = new RevisionInternal(props);
        RevisionInternal leaf1 = database.putRevision(rev1, null, false);
        Log.i(TAG, String.format("leaf1: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf1.getSequence(), leaf1.getDocID(), leaf1.getRevID(), leaf1.isDeleted() ? "true" : "false"));

        // Need to override StoreDelegate to control revision ID for generation 2-.
        Store store = database.getStore();
        StoreDelegate delegate = store.getDelegate();

        // set Revision ID "2-0002"
        store.setDelegate(new StoreDelegate() {
            @Override
            public void storageExitedTransaction(boolean committed) {
            }

            @Override
            public void databaseStorageChanged(DocumentChange change) {
            }

            @Override
            public String generateRevID(byte[] json, boolean deleted, String prevRevID) {
                return "2-0002";
            }

            @Override
            public boolean runFilter(ReplicationFilter filter, Map<String, Object> filterParams, RevisionInternal rev) {
                return false;
            }
        });

        // create conflicts rev2a and rev2b
        props.put("_rev", leaf1.getRevID());
        props.put("key", "2a");
        RevisionInternal rev2a = new RevisionInternal(props);
        RevisionInternal leaf2a = database.putRevision(rev2a, leaf1.getRevID(), true);
        Log.i(TAG, String.format("leaf2a: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf2a.getSequence(), leaf2a.getDocID(), leaf2a.getRevID(), leaf2a.isDeleted() ? "true" : "false"));

        // set Revision ID "2-0001" which is same generation but lower revision ID than previous one
        store.setDelegate(new StoreDelegate() {
            @Override
            public void storageExitedTransaction(boolean committed) {
            }

            @Override
            public void databaseStorageChanged(DocumentChange change) {
            }

            @Override
            public String generateRevID(byte[] json, boolean deleted, String prevRevID) {
                return "2-0001";
            }

            @Override
            public boolean runFilter(ReplicationFilter filter, Map<String, Object> filterParams, RevisionInternal rev) {
                return false;
            }
        });

        props.put("key", "2b");
        RevisionInternal rev2b = new RevisionInternal(props);
        RevisionInternal leaf2b = database.putRevision(rev2b, leaf1.getRevID(), true);
        Log.i(TAG, String.format("leaf2b: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf2b.getSequence(), leaf2b.getDocID(), leaf2b.getRevID(), leaf2b.isDeleted() ? "true" : "false"));

        store.setDelegate(delegate);

        // make sure which rev wins
        RevisionInternal winning = leaf2a.getRevID().compareTo(leaf2b.getRevID()) > 0 ? leaf2a : leaf2b; // Should be leaf2a
        RevisionInternal looser = leaf2a.getRevID().compareTo(leaf2b.getRevID()) < 0 ? leaf2a : leaf2b; // Should be leaf2b
        assertEquals(leaf2a.getRevID(), winning.getRevID());
        assertEquals(leaf2b.getRevID(), looser.getRevID());

        // update index
        view.updateIndex();
        List<QueryRow> rows = view.query(null);
        Log.i(TAG, rows.toString());
        assertEquals(winning.getProperties().get("key"), rows.get(0).getKey());

        // Delete winning rev
        RevisionInternal leaf3 = new RevisionInternal("172", null, true);
        leaf3 = database.putRevision(leaf3, winning.getRevID(), true);
        Log.i(TAG, String.format("leaf3: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf3.getSequence(), leaf3.getDocID(), leaf3.getRevID(), leaf3.isDeleted() ? "true" : "false"));

        // update index
        view.updateIndex();
        rows = view.query(null);
        Log.i(TAG, rows.toString());
        assertEquals(looser.getProperties().get("key"), rows.get(0).getKey());
    }

    /**
     * Views broken with concurrent update and delete
     * https://github.com/couchbase/couchbase-lite-java-core/issues/952
     */
    public void testViewUpdateWinningRevisionIsNotIndexed() throws CouchbaseLiteException {
        //
        // revid 3-c should be indexed with folloiwng condition.
        // NOTE: As 3-c < 3-d (deleted), So this might cause indexing problem
        //
        // seq  | doc_id | revid | parent | current | deleted
        // -----+--------+-------+--------+---------+--------
        // 1    | doc1   | 1-x   | NULL   | 0       | 0
        // 2    | doc1   | 2-a   | 1      | 0       | 0
        // 3    | doc1   | 2-b   | 1      | 0       | 0
        // 4    | doc1   | 3-c   | 2      | 1       | 0
        // 5    | doc1   | 3-d   | 3      | 1       | 1

        // create view
        View view = createView(database);

        // crete doc
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", "doc1");
        props.put("key", "1-x");
        RevisionInternal rev1 = new RevisionInternal(props);
        RevisionInternal leaf1 = database.putRevision(rev1, null, false);
        Log.i(TAG, String.format("leaf1: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf1.getSequence(), leaf1.getDocID(), leaf1.getRevID(), leaf1.isDeleted() ? "true" : "false"));

        // create conflicts rev2a and rev2b
        props.put("_rev", leaf1.getRevID());
        props.put("key", "2-a");
        RevisionInternal rev2a = new RevisionInternal(props);
        RevisionInternal leaf2a = database.putRevision(rev2a, leaf1.getRevID(), true);
        Log.i(TAG, String.format("leaf2a: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf2a.getSequence(), leaf2a.getDocID(), leaf2a.getRevID(), leaf2a.isDeleted() ? "true" : "false"));

        props.put("key", "2-b");
        RevisionInternal rev2b = new RevisionInternal(props);
        RevisionInternal leaf2b = database.putRevision(rev2b, leaf1.getRevID(), true);
        Log.i(TAG, String.format("leaf2b: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf2b.getSequence(), leaf2b.getDocID(), leaf2b.getRevID(), leaf2b.isDeleted() ? "true" : "false"));

        // update index
        view.updateIndex();
        List<QueryRow> rows = view.query(null);
        assertNotNull(rows);
        Log.i(TAG, rows.toString());
        assertEquals(1, rows.size()); // one 2 must win

        // Need to override StoreDelegate to control revision ID for generation 2-.
        Store store = database.getStore();

        // set Revision ID "3-cccc"
        store.setDelegate(new StoreDelegate() {
            @Override
            public void storageExitedTransaction(boolean committed) {
            }

            @Override
            public void databaseStorageChanged(DocumentChange change) {
            }

            @Override
            public String generateRevID(byte[] json, boolean deleted, String prevRevID) {
                return "3-cccc";// 3-c is not appropriate revision id for forestdb
            }

            @Override
            public boolean runFilter(ReplicationFilter filter, Map<String, Object> filterParams, RevisionInternal rev) {
                return false;
            }
        });
        // create rev3c from rev2a
        props.put("_rev", leaf1.getRevID());
        props.put("key", "3-c");
        RevisionInternal rev3c = new RevisionInternal(props);
        RevisionInternal leaf3c = database.putRevision(rev3c, leaf2a.getRevID(), true);
        Log.i(TAG, String.format("leaf3c: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf3c.getSequence(), leaf3c.getDocID(), leaf3c.getRevID(), leaf3c.isDeleted() ? "true" : "false"));

        // set Revision ID "3-dddd"
        store.setDelegate(new StoreDelegate() {
            @Override
            public void storageExitedTransaction(boolean committed) {
            }
            @Override
            public void databaseStorageChanged(DocumentChange change) {
            }
            @Override
            public String generateRevID(byte[] json, boolean deleted, String prevRevID) {
                return "3-dddd"; // 3-d is not appropriate revision id for forestdb
            }
            @Override
            public boolean runFilter(ReplicationFilter filter, Map<String, Object> filterParams, RevisionInternal rev) {
                return false;
            }
        });
        // create rev3d from rev2b with delete
        RevisionInternal leaf3d = new RevisionInternal("doc1", null, true);
        leaf3d = database.putRevision(leaf3d, leaf2b.getRevID(), true);
        Log.i(TAG, String.format("leaf3d: seq=%d, doc_id=%s, rev_id=%s deleted=%s", leaf3d.getSequence(), leaf3d.getDocID(), leaf3d.getRevID(), leaf3d.isDeleted() ? "true" : "false"));
        assertTrue(leaf3d.isDeleted());

        // make sure 3-d is higher revision than 3-c
        assertTrue(leaf3d.getRevID().compareTo(leaf3c.getRevID()) > 0);

        // update index again ... now we must receive 3-c
        view.updateIndex();
        rows = view.query(null);
        assertNotNull(rows);
        Log.i(TAG, rows.toString());
        assertEquals(1, rows.size());
        assertEquals("3-c", rows.get(0).getKey());
    }

    // test21_TotalRows in View_Tests.m
    public void testTotalRows() throws Exception {
        View view = database.getView("vu");
        assertNotNull(view);
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");
        assertNotNull(view.getMap());
        assertEquals(0, view.getTotalRows());

        // Add 20 documents:
        createDocuments(database, 20);
        assertTrue(view.isStale());
        assertEquals(20, view.getTotalRows());
        assertTrue(!view.isStale());

        // Add another 20 documents, query, and check totalRows:
        createDocuments(database, 20);
        Query query = view.createQuery();
        QueryEnumerator rows = query.run();
        assertEquals(40, rows.getCount());
        assertEquals(40, view.getTotalRows());
    }

    // test04_IndexMultiple in ViewInternal_Tests.m
    public void testIndexMultipleViews() throws Exception {
        View v1 = createView(database, "agroup/view1");
        View v2 = createView(database, "other/view2");
        View v3 = createView(database, "other/view3");
        View vX = createView(database, "other/viewX");
        View v4 = createView(database, "view4");
        View v5 = createView(database, "view5");

        View[] v1Groups = sortViews(v1.getViewsInGroup());
        assertTrue(Arrays.equals(new View[] {v1}, v1Groups));

        View[] v2Groups = sortViews(v2.getViewsInGroup());
        assertTrue(Arrays.equals(new View[] {v2, v3, vX}, v2Groups));

        View[] v3Groups = sortViews(v3.getViewsInGroup());
        assertTrue(Arrays.equals(new View[] {v2, v3, vX}, v3Groups));

        View[] vXGroups = sortViews(vX.getViewsInGroup());
        assertTrue(Arrays.equals(new View[] {v2, v3, vX}, vXGroups));

        View[] v4Groups = sortViews(v4.getViewsInGroup());
        assertTrue(Arrays.equals(new View[] {v4}, v4Groups));

        View[] v5Groups = sortViews(v5.getViewsInGroup());
        assertTrue(Arrays.equals(new View[] {v5}, v5Groups));

        final int numDocs = 10;
        for (int i = 0; i < numDocs; i++) {
            Map <String, Object> props = new HashMap<String, Object>();
            props.put("key", i);
            putDoc(database, props);

            if (i == numDocs/2) {
                Status status = v1.updateIndex();
                assertEquals(Status.OK, status.getCode());
            }
        }

        Status status = v2.updateIndexAlone();
        assertEquals(Status.OK, status.getCode());

        status = v2.updateIndex();
        assertEquals(Status.NOT_MODIFIED, status.getCode());

        List<View> views = Arrays.asList(new View[] {v1, v2, v3});
        status = v3.updateIndexes(views);
        assertEquals(Status.OK, status.getCode());

        for (View view : new View[] {v2, v3}) {
            assertEquals(numDocs, view.getLastSequenceIndexed());
        }
    }

    private View[] sortViews(List<View> views) {
        List<View> result = new ArrayList<View>(views);
        Collections.sort(result, new Comparator<View>() {
            @Override
            public int compare(View lhs, View rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
        return result.toArray(new View[result.size()]);
    }

    public void testIndexMultipleViewsDifferentMaps() throws Exception {
        View view1 = database.getView("a/1");
        view1.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("_id"), "a/1");
            }
        }, "1");

        View view2 = database.getView("a/2");
        view2.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("_id"), "a/2");
            }
        }, "1");

        View view3 = database.getView("b/1");
        view3.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("_id"), "b/1");
            }
        }, "1");

        createDocuments(database, 5);

        assertTrue(view1.isStale());
        assertTrue(view2.isStale());
        assertTrue(view3.isStale());

        Status status = view1.updateIndex();
        assertEquals(Status.OK, status.getCode());

        status = view2.updateIndex();
        assertEquals(Status.NOT_MODIFIED, status.getCode());

        status = view3.updateIndex();
        assertEquals(Status.OK, status.getCode());

        assertEquals(5, view1.getTotalRows());
        assertEquals(5, view2.getTotalRows());
        assertEquals(5, view3.getTotalRows());

        Query query1 = view1.createQuery();
        QueryEnumerator rows1 = query1.run();
        assertEquals(5, rows1.getCount());
        while (rows1.hasNext()) {
            QueryRow row = rows1.next();
            assertEquals("a/1", row.getValue());
        }

        Query query2 = view2.createQuery();
        QueryEnumerator rows2 = query2.run();
        assertEquals(5, rows1.getCount());
        while (rows2.hasNext()) {
            QueryRow row = rows2.next();
            assertEquals("a/2", row.getValue());
        }

        Query query3 = view3.createQuery();
        QueryEnumerator rows3 = query3.run();
        assertEquals(5, rows1.getCount());
        while (rows3.hasNext()) {
            QueryRow row = rows3.next();
            assertEquals("b/1", row.getValue());
        }
    }

    // test20_DocTypes in View_Tests.m
    public void testDocTypes() throws Exception {
        View view1 = database.getView("test/peepsNames");
        view1.setDocumentType("person");
        view1.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                assertEquals("person", document.get("type"));
                emitter.emit(document.get("name"), null);
            }
        }, "1");

        View view2 = database.getView("test/aardvarks");
        view2.setDocumentType("aardvark");
        view2.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                assertEquals("aardvark", document.get("type"));
                emitter.emit(document.get("name"), null);
            }
        }, "1");

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("type", "person");
        properties.put("name", "mick");
        createDocWithProperties(properties);

        properties = new HashMap<String, Object>();
        properties.put("type", "person");
        properties.put("name", "keef");
        createDocWithProperties(properties);

        properties.put("type", "aardvark");
        properties.put("name", "cerebus");
        final Document doc = createDocWithProperties(properties);

        Query query = view1.createQuery();
        QueryEnumerator rows = query.run();
        assertEquals(2, rows.getCount());
        assertEquals("keef", rows.getRow(0).getKey());
        assertEquals("mick", rows.getRow(1).getKey());

        query = view2.createQuery();
        rows = query.run();
        assertEquals(1, rows.getCount());
        assertEquals("cerebus", rows.getRow(0).getKey());

        // Make sure that documents that are updated to no longer match the view's documentType get
        // removed from its index:
        SavedRevision rev = doc.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision rev) {
                Map<String, Object> props = rev.getProperties();
                props.put("type", "person");
                rev.setProperties(props);
                return true;
            }
        });
        assertNotNull(rev);

        rows = query.run();
        assertEquals(0, rows.getCount());

        // Make sure a view without a docType will coexist:
        properties = new HashMap<String, Object>();
        properties.put("type", "elf");
        properties.put("name", "regency elf");
        createDocWithProperties(properties);

        View view3 = database.getView("test/all");
        view3.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), null);
            }
        }, "1");

        query = view3.createQuery();
        rows = query.run();
        assertEquals(4, rows.getCount());
    }

    // test22_MapFn_Conflicts in View_Tests.m
    public void testMapFnConflicts() throws Exception {
        View view = database.getView("vu");
        assertNotNull(view);
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("_id"), document.get("_conflicts"));
            }
        }, "1");
        assertNotNull(view.getMap());

        Map <String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");
        Document doc = createDocWithProperties(properties);
        SavedRevision rev1 = doc.getCurrentRevision();

        properties = new HashMap<String, Object>(doc.getProperties());
        properties.put("tag", "1");
        SavedRevision rev2a = doc.putProperties(properties);
        assertNotNull(rev2a);

        // No conflicts:
        Query query = view.createQuery();
        QueryEnumerator rows = query.run();
        assertEquals(1, rows.getCount());
        QueryRow row = rows.getRow(0);
        assertEquals(doc.getId(), row.getKey());
        assertNull(row.getValue());

        // Create a conflict revision:
        properties = new HashMap<String, Object>(rev1.getProperties());
        properties.put("tag", "2");
        UnsavedRevision newRev = rev1.createRevision();
        newRev.setProperties(properties);
        SavedRevision rev2b = newRev.save(true);
        assertNotNull(rev2b);

        rows = query.run();
        assertEquals(1, rows.getCount());
        row = rows.getRow(0);
        assertEquals(doc.getId(), row.getKey());
        List<String> v = (List<String>)row.getValue();
        assertNotNull(v);

        String conflictRevID;
        if (rev2b.getId().equals(doc.getCurrentRevisionId()))
            conflictRevID = rev2a.getId();
        else
            conflictRevID = rev2b.getId();
        assertTrue(Arrays.equals(new String[] {conflictRevID}, v.toArray(new String[v.size()])));

        // Create another conflict revision:
        properties = new HashMap<String, Object>(rev1.getProperties());
        properties.put("tag", "3");
        newRev = rev1.createRevision();
        newRev.setProperties(properties);
        SavedRevision rev2c = newRev.save(true);
        assertNotNull(rev2c);

        rows = query.run();
        assertEquals(1, rows.getCount());
        row = rows.getRow(0);
        assertEquals(doc.getId(), row.getKey());
        v = (List<String>)row.getValue();
        assertNotNull(v);

        // _conflicts in the map function are sorted by RevID desc. Therefore
        // the order will depend on the actual RevID string content.
        //
        // Currently there is an issue that the RevIDs generated on different Android APIs may
        // not be the same (https://github.com/couchbase/couchbase-lite-java-core/issues/878).
        // As a result we couldn't guarantee the order or the conflicting RevIDs when writing
        // the assertion tests.
        //
        // Workaround: sort the conflicting revs IDs for comparison:
        List<String> conflictRevs = new ArrayList<String>();
        for (SavedRevision rev : doc.getConflictingRevisions()) {
            if (!rev.getId().equals(doc.getCurrentRevisionId()))
                conflictRevs.add(rev.getId());
        }
        String[] conflicts = conflictRevs.toArray(new String[conflictRevs.size()]);
        Arrays.sort(conflicts);
        String[] _conflicts = v.toArray(new String[v.size()]);
        Arrays.sort(_conflicts);
        assertTrue(Arrays.equals(conflicts, _conflicts));
    }

    public void testViewNameWithSpecialChars() throws Exception {

        // NOTE: On Windows, following characters are not allowed to use as folder name: "<>:\"/\\|?*"

        // view name start with period "." => Not Allow with forestdb
        View v1 = database.getView(".view");
        if (isSQLiteDB())
            assertNotNull(v1);
        else
            assertNull(v1);

        // view name that includes colon ":" => Not Allow with forestdb
        View v2 = database.getView("group:view");
        if (isSQLiteDB())
            assertNotNull(v2);
        else
            assertNull(v2);

        // view name that includes forward slash "/" => Allow
        View v3 = database.getView("group/view");
        assertNotNull(v3);

        // view name that includes forward slash "\"
        View v4 = database.getView("group\\view");
        assertNotNull(v4);

        // view name that includes less than & larger than "<>"
        View v5 = database.getView("<group>view");
        assertNotNull(v5);

        // view name that includes double quote '"'
        View v6 = database.getView("\"group\"view");
        assertNotNull(v6);

        // view name that includes vertical bar '|'
        View v7 = database.getView("group|view");
        assertNotNull(v7);

        // view name that includes question mark '?'
        View v8 = database.getView("group?view");
        assertNotNull(v8);

        // view name that includes asterisk '*'
        View v9 = database.getView("group*view");
        assertNotNull(v9);
    }

    // https://github.com/couchbase/couchbase-lite-ios/issues/1082
    public void test23_ViewWithDocDeletion() throws CouchbaseLiteException {
        _testViewWithDocDeletionOrPurge(false);
    }

    public void test24_ViewWithDocPurge() throws CouchbaseLiteException {
        _testViewWithDocDeletionOrPurge(true);
    }

    public void _testViewWithDocDeletionOrPurge(boolean purge) throws CouchbaseLiteException {
        View view = database.getView("vu");
        assertNotNull(view);
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if ("task".equals(document.get("type"))) {
                    List<Object> keys = new ArrayList<Object>();
                    keys.add(document.get("list_id"));
                    keys.add(document.get("created_at"));
                    emitter.emit(keys, document);
                }
            }
        }, "1");
        assertNotNull(view.getMap());
        assertEquals(0, view.getCurrentTotalRows());

        String listId = "list1";

        // Create 3 documents:
        Map<String, Object> props1 = new HashMap<String, Object>();
        props1.put("_id", "doc1");
        props1.put("type", "task");
        props1.put("created_at", "2016-01-29T22:25:01.000Z");
        props1.put("list_id", listId);
        Document doc1 = createDocWithProperties(props1);

        Map<String, Object> props2 = new HashMap<String, Object>();
        props2.put("_id", "doc2");
        props2.put("type", "task");
        props2.put("created_at", "2016-01-29T22:25:02.000Z");
        props2.put("list_id", listId);
        Document doc2 = createDocWithProperties(props2);

        Map<String, Object> props3 = new HashMap<String, Object>();
        props3.put("_id", "doc3");
        props3.put("type", "task");
        props3.put("created_at", "2016-01-29T22:25:03.000Z");
        props3.put("list_id", listId);
        Document doc3 = createDocWithProperties(props3);

        // Check query result:
        Query query = view.createQuery();
        query.setDescending(true);
        List<Object> startKeys = new ArrayList<Object>();
        startKeys.add(listId);
        startKeys.add(new HashMap<String, Object>());
        query.setStartKey(startKeys);
        List<Object> endKeys = new ArrayList<Object>();
        endKeys.add(listId);
        query.setEndKey(endKeys);

        QueryEnumerator rows = query.run();
        Log.i(TAG, "First query: rows.getCount() = " + rows.getCount());
        assertEquals(3, rows.getCount());
        assertEquals(doc3.getId(), rows.getRow(0).getDocumentId());
        assertEquals(doc2.getId(), rows.getRow(1).getDocumentId());
        assertEquals(doc1.getId(), rows.getRow(2).getDocumentId());

        // Delete doc2:
        assertNotNull(doc2);
        if (purge)
            doc2.purge();
        else
            assertTrue(doc2.delete());
        Log.i(TAG, "Deleted doc2");

        // Check ascending query result:
        query.setDescending(false);
        query.setStartKey(endKeys);
        query.setEndKey(startKeys);
        rows = query.run();
        Log.i(TAG, "Ascending query: rows.getCount() = " + rows.getCount());
        assertEquals(2, rows.getCount());
        assertEquals(doc1.getId(), rows.getRow(0).getDocumentId());
        assertEquals(doc3.getId(), rows.getRow(1).getDocumentId());

        // Check descending query result:
        query.setDescending(true);
        query.setStartKey(startKeys);
        query.setEndKey(endKeys);

        rows = query.run();
        Log.i(TAG, "Descending query: rows.getCount() = " + rows.getCount());
        assertEquals(2, rows.getCount());
        assertEquals(doc3.getId(), rows.getRow(0).getDocumentId());
        assertEquals(doc1.getId(), rows.getRow(1).getDocumentId());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/971
     *
     * ForestDB: Query with descending order against empty db
     */
    public void testViewDecendingOrderWithEmptyDB() throws Exception {
        View view = database.getView("vu");
        assertNotNull(view);
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if ("task".equals(document.get("type"))) {
                    List<Object> keys = new ArrayList<Object>();
                    keys.add(document.get("list_id"));
                    keys.add(document.get("created_at"));
                    emitter.emit(keys, document);
                }
            }
        }, "1");
        assertNotNull(view.getMap());
        assertEquals(0, view.getCurrentTotalRows());

        String listId = "list1";

        // Check ascending query result:
        Query query = view.createQuery();
        query.setDescending(false);
        List<Object> startKeys = new ArrayList<Object>();
        startKeys.add(listId);
        startKeys.add(new HashMap<String, Object>());
        List<Object> endKeys = new ArrayList<Object>();
        endKeys.add(listId);
        query.setStartKey(endKeys);
        query.setEndKey(startKeys);
        QueryEnumerator rows = query.run();
        Log.i(TAG, "Ascending query: rows.getCount() = " + rows.getCount());
        assertEquals(0, rows.getCount());

        // Check descending query result:
        query.setDescending(true);
        query.setStartKey(startKeys);
        query.setEndKey(endKeys);
        rows = query.run();
        Log.i(TAG, "Descending query: rows.getCount() = " + rows.getCount());
        assertEquals(0, rows.getCount());
    }

    // - (void) test28_Nil_Key
    public void testEmitWithNullKey() throws Exception {
        // set up view
        View view = database.getView("vu");
        assertNotNull(view);
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                // null key -> ignored
                emitter.emit(null,  "foo");
                emitter.emit("name", "bar");
            }
        }, "1");
        assertNotNull(view.getMap());
        assertEquals(0, view.getCurrentTotalRows());

        // insert 1 doc
        Map<String, Object> dict = new HashMap<String, Object>();
        dict.put("_id", "11111");
        assertNotNull(putDoc(database, dict));

        // regular query
        Query query = view.createQuery();
        assertNotNull(query);
        QueryEnumerator e = query.run();
        assertNotNull(e);
        assertEquals(1, e.getCount());
        QueryRow row = e.getRow(0);
        assertNotNull(row);
        assertEquals("name", row.getKey());
        assertEquals("bar", row.getValue());

        // query with null key. it should be ignored. this caused exception previously for sqlite
        query.setKeys(Collections.singletonList(null));
        e = query.run();
        assertNotNull(e);
        assertEquals(0, e.getCount());
    }

    /**
     * Issue: https://github.com/couchbase/couchbase-lite-android/issues/709
     * Documentation: http://developer.couchbase.com/documentation/mobile/1.2/develop/references/couchbase-lite/couchbase-lite/query/query/index.html#boolean-includedeleted--get-set-
     */
    public void testAllDocsWithIncludeDeleted() throws CouchbaseLiteException {


        // create 5 docs
        putDocs(database);

        // create view
        View view = createView(database);

        // create regular query
        Query regQuery = view.createQuery();

        // create all docs query
        Query allQuery = database.createAllDocumentsQuery();

        // Regular query: should return 5 result
        QueryEnumerator e = regQuery.run();
        assertEquals(5, e.getCount());

        // All docs query: should return 5 result
        e = allQuery.run();
        assertEquals(5, e.getCount());

        // delete one doc
        Document doc = database.getDocument("33333");
        doc.delete();

        // Regular query: should return 4 result
        e = regQuery.run();
        assertEquals(4, e.getCount());

        // All docs query: should return 4 result
        e = allQuery.run();
        assertEquals(4, e.getCount());

        // Regular query: should return 4 result as IncludedDeleted does not effect for reg query
        regQuery.setIncludeDeleted(true);
        e = regQuery.run();
        assertEquals(4, e.getCount());

        // All docs query: should return 5 result
        allQuery.setIncludeDeleted(true);
        e = allQuery.run();
        assertEquals(5, e.getCount());
    }
}
