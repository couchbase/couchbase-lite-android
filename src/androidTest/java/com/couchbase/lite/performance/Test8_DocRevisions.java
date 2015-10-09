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

package com.couchbase.lite.performance;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;

public class Test8_DocRevisions extends PerformanceTestCase {
    public static final String TAG = "DocRevisionsPerformance";

    private Document[] docs;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled())
            return;

        docs = new Document[getNumberOfDocuments()];

        // Create docs that will be updated:
        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    //create a document
                    Map<String, Object> props = new HashMap<String, Object>();
                    props.put("toogle", Boolean.TRUE);

                    Document doc = database.createDocument();
                    try {
                        doc.putProperties(props);
                        docs[i] = doc;
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Document creation failed", e);
                        return false;
                    }
                }
                return true;
            }
        });
        assertTrue(success);
    }

    public void testDocRevisionsPerformance() throws Exception {
        if (!performanceTestsEnabled())
            return;

        long start = System.currentTimeMillis();
        for (Document doc : docs) {
            for (int i = 0; i < getNumberOfUpdates(); i++) {
                Map<String, Object> contents = new HashMap(doc.getProperties());
                Boolean wasChecked = (Boolean) contents.get("toogle");
                contents.put("toogle", !wasChecked);
                doc.putProperties(contents);
            }
        }
        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getNumberOfUpdates());
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test8.numberOfDocuments"));
    }

    private int getNumberOfUpdates() {
        return Integer.parseInt(System.getProperty("test8.numberOfUpdates"));
    }
}
