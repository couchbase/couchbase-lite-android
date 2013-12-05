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

import com.couchbase.lite.CBLView.TDViewCollation;
import com.couchbase.lite.internal.CBLRevisionInternal;
import com.couchbase.lite.testapp.tests.CBLiteTestCase;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Views extends CBLiteTestCase {

    public static final String TAG = "Views";

    public void testViewCreation() {

        Assert.assertNull(database.getExistingView("aview"));

        CBLView view = database.getView("aview");
        Assert.assertNotNull(view);
        Assert.assertEquals(database, view.getDatabase());
        Assert.assertEquals("aview", view.getName());
        Assert.assertNull(view.getMap());
        Assert.assertEquals(view, database.getExistingView("aview"));

        boolean changed = view.setMapAndReduce(new CBLMapper() {

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, database.getAllViews().size());
        Assert.assertEquals(view, database.getAllViews().get(0));

        changed = view.setMapAndReduce(new CBLMapper() {

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertFalse(changed);

        changed = view.setMapAndReduce(new CBLMapper() {

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
                //no-op
            }
        }, null, "2");

        Assert.assertTrue(changed);
    }

    private CBLRevisionInternal putDoc(CBLDatabase db, Map<String,Object> props) throws CBLiteException {
        CBLRevisionInternal rev = new CBLRevisionInternal(props, db);
        CBLStatus status = new CBLStatus();
        rev = db.putRevision(rev, null, false, status);
        Assert.assertTrue(status.isSuccessful());
        return rev;
    }

    private void putDocViaUntitledDoc(CBLDatabase db, Map<String, Object> props) throws CBLiteException {
        CBLDocument document = db.createDocument();
        document.putProperties(props);
    }

    public List<CBLRevisionInternal> putDocs(CBLDatabase db) throws CBLiteException {
        List<CBLRevisionInternal> result = new ArrayList<CBLRevisionInternal>();

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

    // http://wiki.apache.org/couchdb/Introduction_to_CouchDB_views#Linked_documents
    public List<CBLRevisionInternal> putLinkedDocs(CBLDatabase db) throws CBLiteException {
        List<CBLRevisionInternal> result = new ArrayList<CBLRevisionInternal>();

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("_id", "11111");
        result.add(putDoc(db, dict1));

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("_id", "22222");
        dict2.put("value", "hello");
        dict2.put("ancestors", new String[] { "11111" });
        result.add(putDoc(db, dict2));

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("_id", "33333");
        dict3.put("value", "world");
        dict3.put("ancestors", new String[] { "22222", "11111" });
        result.add(putDoc(db, dict3));

        return result;
    }

    
    public void putNDocs(CBLDatabase db, int n) throws CBLiteException {
        for(int i=0; i< n; i++) {
            Map<String,Object> doc = new HashMap<String,Object>();
            doc.put("_id", String.format("%d", i));
            List<String> key = new ArrayList<String>();
            for(int j=0; j< 256; j++) {
                key.add("key");
            }
            key.add(String.format("key-%d", i));
            doc.put("key", key);
            putDocViaUntitledDoc(db, doc);
        }
    }

    public static CBLView createView(CBLDatabase db) {
        CBLView view = db.getView("aview");
        view.setMapAndReduce(new CBLMapper() {

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if (document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }
        }, null, "1");
        return view;
    }

    public void testViewIndex() throws CBLiteException {

        int numTimesMapFunctionInvoked = 0;

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("key", "one");
        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("key", "two");
        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("key", "three");
        Map<String,Object> dictX = new HashMap<String,Object>();
        dictX.put("clef", "quatre");

        CBLRevisionInternal rev1 = putDoc(database, dict1);
        CBLRevisionInternal rev2 = putDoc(database, dict2);
        CBLRevisionInternal rev3 = putDoc(database, dict3);
        putDoc(database, dictX);

        class InstrumentedMapBlock implements CBLMapper {

            int numTimesInvoked = 0;

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
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
        CBLView view = database.getView("aview");
        InstrumentedMapBlock mapBlock = new InstrumentedMapBlock();
        view.setMap(mapBlock, "1");

        Assert.assertEquals(1, view.getViewId());
        Assert.assertTrue(view.isStale());

        view.updateIndex();

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
        Assert.assertFalse(view.isStale());

        view.updateIndex();

        // Now add a doc and update a doc:
        CBLRevisionInternal threeUpdated = new CBLRevisionInternal(rev3.getDocId(), rev3.getRevId(), false, database);
        numTimesMapFunctionInvoked = mapBlock.getNumTimesInvoked();

        Map<String,Object> newdict3 = new HashMap<String,Object>();
        newdict3.put("key", "3hree");
        threeUpdated.setProperties(newdict3);
        CBLStatus status = new CBLStatus();
        rev3 = database.putRevision(threeUpdated, rev3.getRevId(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        view.updateIndex();

        // Make sure the map function was only invoked one more time (for the document that was added)
        Assert.assertEquals(mapBlock.getNumTimesInvoked(), numTimesMapFunctionInvoked + 1);

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("key", "four");
        CBLRevisionInternal rev4 = putDoc(database, dict4);

        CBLRevisionInternal twoDeleted = new CBLRevisionInternal(rev2.getDocId(), rev2.getRevId(), true, database);
        database.putRevision(twoDeleted, rev2.getRevId(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        view.updateIndex();

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
        List<CBLQueryRow> rows = view.queryWithOptions(null);
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals("one", rows.get(2).getKey());
        Assert.assertEquals(rev1.getDocId(), rows.get(2).getDocumentId());
        Assert.assertEquals("3hree", rows.get(0).getKey());
        Assert.assertEquals(rev3.getDocId(), rows.get(0).getDocumentId());
        Assert.assertEquals("four", rows.get(1).getKey());
        Assert.assertEquals(rev4.getDocId(), rows.get(1).getDocumentId());

        view.deleteIndex();
    }

    public void testViewQuery() throws CBLiteException {

        putDocs(database);
        CBLView view = createView(database);

        view.updateIndex();

        // Query all rows:
        CBLQueryOptions options = new CBLQueryOptions();
        List<CBLQueryRow> rows = view.queryWithOptions(options);

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
        options = new CBLQueryOptions();
        options.setStartKey("a");
        options.setEndKey("one");

        rows = view.queryWithOptions(options);

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

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        rows = view.queryWithOptions(options);

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

        rows = view.queryWithOptions(options);

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

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);

        Assert.assertEquals(1, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());

        // Specific keys:
        options = new CBLQueryOptions();
        List<Object> keys = new ArrayList<Object>();
        keys.add("two");
        keys.add("four");
        options.setKeys(keys);

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict2);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict2.get("value"), rows.get(1).getValue());

    }

    public void testAllDocsQuery() throws CBLiteException {

        List<CBLRevisionInternal> docs = putDocs(database);

        List<CBLQueryRow> expectedRow = new ArrayList<CBLQueryRow>();
        for (CBLRevisionInternal rev : docs) {
            Map<String,Object> value = new HashMap<String, Object>();
            value.put("rev", rev.getRevId());
            CBLQueryRow queryRow = new CBLQueryRow(rev.getDocId(), 0, rev.getDocId(), value, null);
            queryRow.setDatabase(database);
            expectedRow.add(queryRow);
        }

        CBLQueryOptions options = new CBLQueryOptions();
        Map<String,Object> allDocs = database.getAllDocs(options);

        List<CBLQueryRow> expectedRows = new ArrayList<CBLQueryRow>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));

        Map<String,Object> expectedQueryResult = createExpectedQueryResult(expectedRows, 0);

        Assert.assertEquals(expectedQueryResult, allDocs);

        // Start/end key query:
        options = new CBLQueryOptions();
        options.setStartKey("2");
        options.setEndKey("44444");

        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<CBLQueryRow>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<CBLQueryRow>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Get all documents: with default CBLQueryOptions
        options = new CBLQueryOptions();
        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<CBLQueryRow>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);

        Assert.assertEquals(expectedQueryResult, allDocs);

        // Get specific documents:
        options = new CBLQueryOptions();
        List<Object> docIds = new ArrayList<Object>();
        CBLQueryRow expected2 = expectedRow.get(2);
        docIds.add(expected2.getDocument().getId());
        options.setKeys(docIds);
        allDocs = database.getAllDocs(options);
        expectedRows = new ArrayList<CBLQueryRow>();
        expectedRows.add(expected2);
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);
    }

    private Map<String, Object> createExpectedQueryResult(List<CBLQueryRow> rows, int offset) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("total_rows", rows.size());
        result.put("offset", offset);
        return result;
    }

    public void testViewReduce() throws CBLiteException {

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "CD");
        docProperties1.put("cost", 8.99);
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "App");
        docProperties2.put("cost", 1.95);
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "Dessert");
        docProperties3.put("cost", 6.50);
        putDoc(database, docProperties3);

        CBLView view = database.getView("totaler");
        view.setMapAndReduce(new CBLMapper() {

                                 @Override
                                 public void map(Map<String, Object> document, CBLEmitter emitter) {
                                     Assert.assertNotNull(document.get("_id"));
                                     Assert.assertNotNull(document.get("_rev"));
                                     Object cost = document.get("cost");
                                     if (cost != null) {
                                         emitter.emit(document.get("_id"), cost);
                                     }
                                 }
                             }, new CBLReducer() {

                                 @Override
                                 public Object reduce(List<Object> keys, List<Object> values,
                                                      boolean rereduce) {
                                     return CBLView.totalValues(values);
                                 }
                             }, "1"
        );

        view.updateIndex();

        List<Map<String,Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"App\"", dumpResult.get(0).get("key"));
        Assert.assertEquals("1.95", dumpResult.get(0).get("value"));
        Assert.assertEquals(2, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"CD\"", dumpResult.get(1).get("key"));
        Assert.assertEquals("8.99", dumpResult.get(1).get("value"));
        Assert.assertEquals(1, dumpResult.get(1).get("seq"));
        Assert.assertEquals("\"Dessert\"", dumpResult.get(2).get("key"));
        Assert.assertEquals("6.5", dumpResult.get(2).get("value"));
        Assert.assertEquals(3, dumpResult.get(2).get("seq"));

        CBLQueryOptions options = new CBLQueryOptions();
        options.setReduce(true);
        List<CBLQueryRow> reduced = view.queryWithOptions(options);
        Assert.assertEquals(1, reduced.size());
        Object value = reduced.get(0).getValue();
        Number numberValue = (Number)value;
        Assert.assertTrue(Math.abs(numberValue.doubleValue() - 17.44) < 0.001);

    }

    public void testViewGrouped() throws CBLiteException {

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "1");
        docProperties1.put("artist", "Gang Of Four");
        docProperties1.put("album", "Entertainment!");
        docProperties1.put("track", "Ether");
        docProperties1.put("time", 231);
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "2");
        docProperties2.put("artist", "Gang Of Four");
        docProperties2.put("album", "Songs Of The Free");
        docProperties2.put("track", "I Love A Man In Uniform");
        docProperties2.put("time", 248);
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "3");
        docProperties3.put("artist", "Gang Of Four");
        docProperties3.put("album", "Entertainment!");
        docProperties3.put("track", "Natural's Not In It");
        docProperties3.put("time", 187);
        putDoc(database, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("_id", "4");
        docProperties4.put("artist", "PiL");
        docProperties4.put("album", "Metal Box");
        docProperties4.put("track", "Memories");
        docProperties4.put("time", 309);
        putDoc(database, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("_id", "5");
        docProperties5.put("artist", "Gang Of Four");
        docProperties5.put("album", "Entertainment!");
        docProperties5.put("track", "Not Great Men");
        docProperties5.put("time", 187);
        putDoc(database, docProperties5);

        CBLView view = database.getView("grouper");
        view.setMapAndReduce(new CBLMapper() {

                                 @Override
                                 public void map(Map<String, Object> document, CBLEmitter emitter) {
                                     List<Object> key = new ArrayList<Object>();
                                     key.add(document.get("artist"));
                                     key.add(document.get("album"));
                                     key.add(document.get("track"));
                                     emitter.emit(key, document.get("time"));
                                 }
                             }, new CBLReducer() {

                                 @Override
                                 public Object reduce(List<Object> keys, List<Object> values,
                                                      boolean rereduce) {
                                     return CBLView.totalValues(values);
                                 }
                             }, "1"
        );

        CBLStatus status = new CBLStatus();
        view.updateIndex();

        CBLQueryOptions options = new CBLQueryOptions();
        options.setReduce(true);
        List<CBLQueryRow> rows = view.queryWithOptions(options);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", null);
        row1.put("value", 1162.0);
        expectedRows.add(row1);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());

        //now group
        options.setGroup(true);
        status = new CBLStatus();
        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        List<String> key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        key1.add("Ether");
        row1.put("key", key1);
        row1.put("value", 231.0);
        expectedRows.add(row1);

        Map<String,Object> row2 = new HashMap<String,Object>();
        List<String> key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Entertainment!");
        key2.add("Natural's Not In It");
        row2.put("key", key2);
        row2.put("value", 187.0);
        expectedRows.add(row2);

        Map<String,Object> row3 = new HashMap<String,Object>();
        List<String> key3 = new ArrayList<String>();
        key3.add("Gang Of Four");
        key3.add("Entertainment!");
        key3.add("Not Great Men");
        row3.put("key", key3);
        row3.put("value", 187.0);
        expectedRows.add(row3);

        Map<String,Object> row4 = new HashMap<String,Object>();
        List<String> key4 = new ArrayList<String>();
        key4.add("Gang Of Four");
        key4.add("Songs Of The Free");
        key4.add("I Love A Man In Uniform");
        row4.put("key", key4);
        row4.put("value", 248.0);
        expectedRows.add(row4);

        Map<String,Object> row5 = new HashMap<String,Object>();
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
        status = new CBLStatus();
        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        row1.put("key", key1);
        row1.put("value", 853.0);
        expectedRows.add(row1);

        row2 = new HashMap<String,Object>();
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
        status = new CBLStatus();
        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        row1.put("key", key1);
        row1.put("value", 605.0);
        expectedRows.add(row1);

        row2 = new HashMap<String,Object>();
        key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Songs Of The Free");
        row2.put("key", key2);
        row2.put("value", 248.0);
        expectedRows.add(row2);

        row3 = new HashMap<String,Object>();
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

    public void testViewGroupedStrings() throws CBLiteException {

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("name", "Alice");
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("name", "Albert");
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("name", "Naomi");
        putDoc(database, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("name", "Jens");
        putDoc(database, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("name", "Jed");
        putDoc(database, docProperties5);

        CBLView view = database.getView("default/names");
        view.setMapAndReduce(new CBLMapper() {

                                 @Override
                                 public void map(Map<String, Object> document, CBLEmitter emitter) {
                                     String name = (String) document.get("name");
                                     if (name != null) {
                                         emitter.emit(name.substring(0, 1), 1);
                                     }
                                 }

                             }, new CBLReducer() {

                                 @Override
                                 public Object reduce(List<Object> keys, List<Object> values,
                                                      boolean rereduce) {
                                     return values.size();
                                 }

                             }, "1.0"
        );


        view.updateIndex();

        CBLQueryOptions options = new CBLQueryOptions();
        options.setGroupLevel(1);
        List<CBLQueryRow> rows = view.queryWithOptions(options);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", "A");
        row1.put("value", 2);
        expectedRows.add(row1);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("key", "J");
        row2.put("value", 2);
        expectedRows.add(row2);
        Map<String,Object> row3 = new HashMap<String,Object>();
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

    public void testViewCollation() throws CBLiteException {
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

        int i=0;
        for (Object key : testKeys) {
            Map<String,Object> docProperties = new HashMap<String,Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(database, docProperties);
        }

        CBLView view = database.getView("default/names");
        view.setMapAndReduce(new CBLMapper() {

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        CBLQueryOptions options = new CBLQueryOptions();
        List<CBLQueryRow> rows = view.queryWithOptions(options);
        i = 0;
        for (CBLQueryRow row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.getKey());
        }
    }


    public void testViewCollationRaw() throws CBLiteException {
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

        int i=0;
        for (Object key : testKeys) {
            Map<String,Object> docProperties = new HashMap<String,Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(database, docProperties);
        }

        CBLView view = database.getView("default/names");
        view.setMapAndReduce(new CBLMapper() {

            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        view.setCollation(TDViewCollation.TDViewCollationRaw);

        CBLQueryOptions options = new CBLQueryOptions();

        List<CBLQueryRow> rows = view.queryWithOptions(options);
        i = 0;
        for (CBLQueryRow row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.getKey());
        }

        database.close();
    }

    public void testLargerViewQuery() throws CBLiteException {
        putNDocs(database, 4);
        CBLView view = createView(database);

        view.updateIndex();

        // Query all rows:
        CBLQueryOptions options = new CBLQueryOptions();
        CBLStatus status = new CBLStatus();
        List<CBLQueryRow> rows = view.queryWithOptions(options);
    }
    
    
    public void testViewLinkedDocs() throws CBLiteException {
        putLinkedDocs(database);
        
        CBLView view = database.getView("linked");
        view.setMapAndReduce(new CBLMapper() {
            @Override
            public void map(Map<String, Object> document, CBLEmitter emitter) {
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

        CBLQueryOptions options = new CBLQueryOptions();
        options.setIncludeDocs(true);  // required for linked documents
        
        List<CBLQueryRow> rows = view.queryWithOptions(options);
        
        Assert.assertNotNull(rows);
        Assert.assertEquals(5, rows.size());

        Object[][] expected = new Object[][] {
                /* id, key0, key1, value._id, doc._id */
                new Object[] { "22222", "hello", 0, null, "22222" },
                new Object[] { "22222", "hello", 1, "11111", "11111" },
                new Object[] { "33333", "world", 0, null, "33333" },
                new Object[] { "33333", "world", 1, "22222", "22222" },
                new Object[] { "33333", "world", 2, "11111", "11111" },
        };

        for (int i=0; i < rows.size(); i++) {
            CBLQueryRow row = rows.get(i);
            List<Object> key = (List<Object>) row.getKey();
            Map<String,Object> doc = (Map<String,Object>)row.getDocumentProperties();
            
            Assert.assertEquals(expected[i][0], row.getDocumentId());
            Assert.assertEquals(2, key.size());
            Assert.assertEquals(expected[i][1], key.get(0));
            Assert.assertEquals(expected[i][2], key.get(1));
            if (expected[i][3] == null) {
                Assert.assertNull(row.getValue());
            }
            else {
                Assert.assertEquals(expected[i][3], ((Map<String,Object>) row.getValue()).get("_id"));
            }
            Assert.assertEquals(expected[i][4], doc.get("_id"));
        }
    }

}
