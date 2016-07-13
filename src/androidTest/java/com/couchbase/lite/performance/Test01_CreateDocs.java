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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Test01_CreateDocs extends PerformanceTestCase {
    public static final String TAG = "CreateDocsPerformance";

    @Override
    protected String getTestTag() {
        return TAG;
    }

    public void testCreateDocsPerformance() throws Exception {
        if (!performanceTestsEnabled())
            return;

        char[] chars = new char[getSizeOfDocument()];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        long start = System.currentTimeMillis();
        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    try {
                        Map<String, Object> props = new HashMap<String, Object>();
                        props.put("content", content);
                        Document doc = database.createDocument();
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error when creating a document", e);
                        return false;
                    }
                }
                return true;
            }
        });
        assertTrue(success);

        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getSizeOfDocument());
    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("test1.sizeOfDocument"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test1.numberOfDocuments"));
    }
}
