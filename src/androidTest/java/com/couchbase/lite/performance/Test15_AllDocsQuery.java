/**
 * Created by Pasin Suriyentrakorn on 10/6/15
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Test15_AllDocsQuery extends PerformanceTestCase {
    public static final String TAG = "AllDocsQueryPerformance";

    @Override
    protected String getTestTag() {
        return TAG;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled())
            return;

        // Populate documents into the database:
        char[] chars = new char[getSizeOfDocument()];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

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
    }

    public void testAllDocsQueryPerformance() throws CouchbaseLiteException {
        if (!performanceTestsEnabled())
            return;

        long start = System.currentTimeMillis();
        Query query = database.createAllDocumentsQuery();
        query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
        QueryEnumerator rowEnum = query.run();
        while (rowEnum.hasNext()) {
            QueryRow row = rowEnum.next();
            assertNotNull(row.getDocumentId());
            assertNotNull(row.getDocumentRevisionId());
        }
        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getSizeOfDocument());
    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("test15.sizeOfDocument"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test15.numberOfDocuments"));
    }
}
