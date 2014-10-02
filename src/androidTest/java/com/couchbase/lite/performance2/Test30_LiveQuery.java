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

package com.couchbase.lite.performance2;

import com.couchbase.lite.AsyncTask;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LitePerfTestCase;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.Status;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.View;
import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Test30_LiveQuery extends LitePerfTestCase {

    public static final String TAG = "Test30_LiveQuery";

    public double runOne(final int numberOfDocuments, final int sizeOfDocuments) throws CouchbaseLiteException {
        final CountDownLatch doneSignal = new CountDownLatch(numberOfDocuments);

        // run a live query
        View view = database.getView("vu");
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");
        final LiveQuery query = view.createQuery().toLiveQuery();
        Log.i(TAG, "Created  " + query);

        // install a change listener which decrements countdown latch when it sees a new
        // key from the list of expected keys
        final LiveQuery.ChangeListener changeListener = new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                QueryEnumerator rows = event.getRows();
                int count = rows.getCount();
                //Log.v("PerformanceStats",TAG+", In ChangeListener, got " + rows.getCount() + " rows.");
                if (count == numberOfDocuments) {
                    doneSignal.countDown();
                }
            }
        };
        query.addChangeListener(changeListener);

        //Start measurement, including create docs, define view, and do query
        long startMillis = System.currentTimeMillis();

        // create the docs that will cause the above change listener to decrement countdown latch
        createDocumentsAsync(database, numberOfDocuments, sizeOfDocuments);

        query.run();  // this will block until the query completes

        // wait for the doneSignal to be finished
        try {
            boolean success = doneSignal.await(5, TimeUnit.SECONDS);  //??? 300
        }
        catch(InterruptedException ex)
        {
            Log.v("PerformanceStats",TAG+", Got exception during doneSignal.await. "+ ex);
            return 0;
        }

        // stop the live query since we are done with it
        query.removeChangeListener(changeListener);
        query.stop();

        double executionTime = Long.valueOf(System.currentTimeMillis()-startMillis);
        Log.v("PerformanceStats",TAG+","+executionTime+","+numberOfDocuments+","+sizeOfDocuments);
        if (query.getRows().getCount() == numberOfDocuments) {
            return executionTime;
        } else {
            Log.v("PerformanceStats", TAG + ", Number of docs return by query:" + query.getRows().getCount() + ". Expecting:" + numberOfDocuments);
            return failingPerfNumber;
        }
    }

    public static void createDocuments(final Database db, final int numberOfDocuments, final int sizeOfDocuments) {
        final StringBuffer bigObj = new StringBuffer(sizeOfDocuments);
        for (int i = 0; i < sizeOfDocuments; i++) {
            bigObj.append(_propertyValue);
        }
        String name = String.format("%s",bigObj);

        for (int i=0; i<numberOfDocuments; i++) {
            Map<String,Object> properties = new HashMap<String,Object>();
            properties.put("name", name);
            properties.put("sequence", i);
            createDocumentWithProperties(db, properties);
        }
    };

    static Future createDocumentsAsync(final Database db, final int numberOfDocuments, final int sizeOfDocuments) {
        return db.runAsync(new AsyncTask() {
            @Override
            public void run(Database database) {
                database.beginTransaction();
                createDocuments(db, numberOfDocuments, sizeOfDocuments);
                database.endTransaction(true);
            }
        });

    };


    public static Document createDocumentWithProperties(Database db, Map<String,Object>  properties) {
        Document  doc = db.createDocument();
        Assert.assertNotNull(doc);
        Assert.assertNull(doc.getCurrentRevisionId());
        Assert.assertNull(doc.getCurrentRevision());
        Assert.assertNotNull("Document has no ID", doc.getId()); // 'untitled' docs are no longer untitled (8/10/12)
        try{
            doc.putProperties(properties);
        } catch( Exception e){
            Log.e(TAG, "Error creating document", e);
            assertTrue("can't create new document in db:" + db.getName() + " with properties:" + properties.toString(), false);
        }
        Assert.assertNotNull(doc.getId());
        Assert.assertNotNull(doc.getCurrentRevisionId());
        Assert.assertNotNull(doc.getUserProperties());

        // should be same doc instance, since there should only ever be a single Document instance for a given document
        Assert.assertEquals(db.getDocument(doc.getId()), doc);

        Assert.assertEquals(db.getDocument(doc.getId()).getId(), doc.getId());

        return doc;
    }



}

