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
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by hideki on 7/14/16.
 */
public class MultiThreadsWithSGTest extends LiteTestCaseWithDB {
    private static final String TAG = MultiThreadsWithSGTest.class.getName();

    @Override
    protected void setUp() throws Exception {
        if (!multithreadsTestsEnabled())
            return;
        if (!syncgatewayTestsEnabled())
            return;
        super.setUp();
    }

    public void testUpdateDocsWithPushRepl() throws Exception {
        if (!multithreadsTestsEnabled())
            return;
        if (!syncgatewayTestsEnabled())
            return;

        URL remote = getReplicationURL();
        Replication push = database.createPushReplication(remote);
        push.setContinuous(true);
        push.start();

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

        // Thread1: update docs
        Thread updateThread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 50; i++) {
                    String docID = String.format(Locale.ENGLISH, "docID-%08d", i);
                    Log.e(TAG, "updateThread1 docID=%s", docID);
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

        // Thread2: update docs
        Thread updateThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 50; i < 100; i++) {
                    String docID = String.format(Locale.ENGLISH, "docID-%08d", i);
                    Log.e(TAG, "updateThread2 docID=%s", docID);
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

        updateThread1.start();
        updateThread2.start();

        try {
            updateThread1.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in updateThread1. ", e);
        }
        try {
            updateThread2.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in updateThread1. ", e);
        }

        Thread.sleep(1000 * 5);

        final CountDownLatch pushDone = new CountDownLatch(1);
        push.addChangeListener(new ReplicationFinishedObserver(pushDone));
        push.stop();
        assertTrue(pushDone.await(30, TimeUnit.SECONDS));
    }
}
