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
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test3_ReadDocs extends PerformanceTestCase {
    public static final String TAG = "ReadDocsPerformance";
    private List<String> docIds;

    @Override
    protected String getTestTag() {
        return TAG;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled())
            return;

        char[] chars = new char[getSizeOfDocument()];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        // Prepare data:
        docIds = new ArrayList<String>();
        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    Map<String, Object> props = new HashMap<String, Object>();
                    props.put("content", content);
                    Document doc = database.createDocument();
                    try {
                        assertNotNull(doc.putProperties(props));
                        docIds.add(doc.getId());
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Document create failed", e);
                        return false;
                    }
                }
                return true;
            }
        });
        assertTrue(success);

        // Close and reopen the database:
        database.close();
        database = manager.getDatabase(DEFAULT_TEST_DB);
    }

    public void testReadDocsPerformance() throws Exception {
        if (!performanceTestsEnabled())
            return;

        long start = System.currentTimeMillis();
        for(String docId : docIds) {
            Document doc = database.getDocument(docId);
            assertNotNull(doc);
            Map<String,Object> properties = doc.getProperties();
            assertNotNull(properties);
            assertNotNull(properties.get("content"));
        }
        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getSizeOfDocument());
    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("test3.sizeOfDocument"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test3.numberOfDocuments"));
    }
}
