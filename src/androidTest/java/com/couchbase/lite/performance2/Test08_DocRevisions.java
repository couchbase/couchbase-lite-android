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

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.LitePerfTestCase;
import com.couchbase.lite.Status;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;

public class Test08_DocRevisions extends LitePerfTestCase {

    public static final String TAG = "Test8_DocRevisions";
    private static final String _propertyValue = "1";
    private Document[] docs;

    public double runOne(final int numberOfDocuments, final int sizeOfDocuments) throws Exception {

        docs = new Document[numberOfDocuments];
        //Create docs that will be updated in test case
        assertTrue(database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int i = 0; i < numberOfDocuments; i++) {
                    Map<String, Object> props = new HashMap<String, Object>();
                    String[] bigObj = new String[sizeOfDocuments];
                    for (int j = 0; j < sizeOfDocuments; j++) {
                        bigObj[j] = _propertyValue;
                    }
                    props.put("bigArray", bigObj);
                    props.put("toogle", Boolean.TRUE);
                    Document doc = database.createDocument();
                    docs[i] = doc;
                    try {
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException cblex) {
                        Log.v("PerformanceStats",TAG+", Document creation failed", cblex);
                        return false;
                    }
                }
                return true;
            }
        }));

        long startMillis = System.currentTimeMillis();
        assertTrue(database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int j = 0; j < numberOfDocuments; j++) {
                    Document doc = docs[j];
                    //Log.v("PerformanceStats",TAG+", j="+j+","+numberOfDocuments+","+sizeOfDocuments);
                    Map<String, Object> contents = new HashMap(doc.getProperties());
                    Boolean wasChecked = (Boolean) contents.get("toogle");
                    //toggle value of check property
                    contents.put("toogle", !wasChecked);
                    try {
                        doc.putProperties(contents);
                    } catch (CouchbaseLiteException cblex) {
                        Log.v("PerformanceStats",TAG+", Document update failed", cblex);
                        return false;
                    }
                }
                return true;
            }
        }));
        double executionTime = Long.valueOf(System.currentTimeMillis()-startMillis);
        //Log.v("PerformanceStats",TAG+","+executionTime+","+numberOfDocuments+","+sizeOfDocuments);
        return executionTime;
    }
}
