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
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.Status;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;

public class Test10_DeleteDB extends LiteTestCase {

    public static final String TAG = "DeleteDBPerformance";

    private static final String _propertyValue = "1234567";

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "DeleteDBPerformance setUp");
        super.setUp();

        if (!performanceTestsEnabled()) {
            return;
        }

        //Create docs that will be deleted in test case
        assertTrue(database.runInTransaction(new TransactionalTask() {

            public boolean run() {

                String[] bigObj = new String[getSizeOfDocument()];

                for (int i = 0; i < getSizeOfDocument(); i++) {
                    bigObj[i] = _propertyValue;
                }

                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    //create a document
                    Map<String, Object> props = new HashMap<String, Object>();
                    props.put("bigArray", bigObj);

                    Body body = new Body(props);
                    RevisionInternal rev1 = new RevisionInternal(body);

                    Status status = new Status();
                    try {
                        rev1 = database.putRevision(rev1, null, false, status);
                    } catch (Throwable t) {
                        Log.e(TAG, "Document create failed", t);
                        return false;
                    }
                }

                return true;
            }
        }));
    }

    public void testDeleteDBPerformance() throws CouchbaseLiteException {

        if (!performanceTestsEnabled()) {
            return;
        }

        long startMillis = System.currentTimeMillis();

        try
        {
            for(int i=0; i<getNumberOfDBs(); i++)
            {
                //Note: This shuts down the current manager and database
                //but does not delete the database, that is done in setUp()
                super.tearDown();
                setUp(); //run local test setup
            }
        }
        catch (Exception ex)
        {
            Log.e(TAG, "DB Teardown/Setup failed", ex);
            fail();
        }

        Log.v("PerformanceStats",TAG+","+Long.valueOf(System.currentTimeMillis()-startMillis).toString()+","+getNumberOfDocuments()+","+getSizeOfDocument()+","+getNumberOfDBs());

    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("Test10_sizeOfDocument"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("Test10_numberOfDocuments"));
    }

    private int getNumberOfDBs() {
        return Integer.parseInt(System.getProperty("Test10_numberOfDBs"));
    }
}
