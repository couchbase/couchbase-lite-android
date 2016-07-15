//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.multithreads;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.Revision;
import com.couchbase.lite.View;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hideki on 6/29/16.
 */
public class MultiThreadsTest extends LiteTestCaseWithDB {
    private static final String TAG = MultiThreadsTest.class.getName();

    @Override
    protected void setUp() throws Exception {
        if (!multithreadsTestsEnabled()) {
            return;
        }

        super.setUp();
    }

    public void testInsertAndQueryThreads() {

        if (!multithreadsTestsEnabled()) {
            return;
        }

        // create view
        final View view = createView(database);

        final AtomicInteger count = new AtomicInteger();
        Thread insertThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 500; i++) {
                    String docID = String.format(Locale.ENGLISH, "docID-%08d", count.incrementAndGet());
                    Document doc = database.getDocument(docID);
                    Map<String, Object> props = new HashMap<>();
                    props.put("key", count.get());
                    try {
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Document.putProperty(). ", e);
                        fail(e.getMessage());
                    }
                }
            }
        });

        Thread queryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    Query query = view.createQuery();
                    try {
                        for (QueryRow row : query.run()) {
                            Log.i(TAG, "row=%s", row.getDocumentId());
                        }
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Query.run(). ", e);
                        fail(e.getMessage());
                    }
                }
            }
        });

        queryThread.start();
        insertThread.start();

        try {
            insertThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in insertThread. ", e);
        }
        try {
            queryThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in insertThread. ", e);
        }
    }

    public static View createView(Database db) {
        return createView(db, "aview");
    }

    public static View createView(Database db, String name) {
        View view = db.getView(name);
        if (view != null) {
            view.setMapReduce(new Mapper() {
                @Override
                public void map(Map<String, Object> document, Emitter emitter) {
                    if (document.get("key") != null)
                        emitter.emit(document.get("key"), null);
                }
            }, null, "1");
        }
        return view;
    }

    public void testUpdateDocsAndReadRevHistory() throws Exception {
        if (!multithreadsTestsEnabled())
            return;

        // Insert docs
        Thread insertThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    String docID = String.format(Locale.ENGLISH, "docID-%08d", i);
                    Document doc = database.getDocument(docID);
                    Map<String, Object> props = new HashMap<>();
                    props.put("key", i);
                    try {
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Document.putProperty(). ", e);
                        fail(e.getMessage());
                    }
                }
            }
        });
        insertThread.start();
        try {
            insertThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in insertThread. ", e);
        }

        // Thread1: read docs
        Thread readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for(int j = 0; j < 20; j++) {
                    for (int i = 0; i < 100; i++) {
                        String docID = String.format(Locale.ENGLISH, "docID-%08d", i);
                        Document doc = database.getDocument(docID);
                        String revID = doc.getCurrentRevisionId();
                        //RevisionInternal rev = new RevisionInternal(doc.getId(), revID, false);
                        RevisionInternal rev = new RevisionInternal(doc.getId(), doc.getCurrentRevisionId(), false);
                        //RevisionInternal rev = new RevisionInternal(doc.getId(), null, false);
                        List<RevisionInternal> revs = database.getRevisionHistory(rev);
                        //Log.e(TAG, "readThread docID=[%s] revID=[%s]", docID, revID);
                        assertNotNull(revs);
                    }
                }
            }
        });

        // Thread2: update docs
        Thread updateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    String docID = String.format(Locale.ENGLISH, "docID-%08d", i);
                    Log.e(TAG, "updateThread docID=%s", docID);
                    for (int j = 0; j < 20; j++) {
                        Document doc = database.getDocument(docID);
                        Map<String, Object> props = new HashMap<>();
                        props.putAll(doc.getProperties());
                        props.put("index", j);
                        try {
                            doc.putProperties(props);
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, "Error in Document.putProperty(). ThreadName=[%s]", e, Thread.currentThread().getName());
                            fail(e.getMessage());
                        }
                    }
                }
            }
        });


        // run read thread and update thread in parallel
        readThread.start();
        updateThread.start();

        // wait read thread finish
        try {
            readThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in readThread. ", e);
        }
        // wait update thread finish
        try {
            updateThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in updateThread. ", e);
        }
    }
}
