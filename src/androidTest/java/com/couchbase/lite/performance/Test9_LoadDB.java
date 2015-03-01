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
import com.couchbase.lite.LiteTestContext;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Status;
import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;

public class Test9_LoadDB extends LiteTestCase {

    public static final String TAG = "LoadDBPerformance";

    private static final String _propertyValue = "1234567";

    public void testLoadDBPerformance() throws CouchbaseLiteException {

        if (!performanceTestsEnabled()) {
            return;
        }

        long startMillis = System.currentTimeMillis();

        String[] bigObj = new String[getSizeOfDocument()];

        for (int i = 0; i < getSizeOfDocument(); i++) {
            bigObj[i] = _propertyValue;
        }


        for (int j = 0; j < getNumberOfShutAndReloadCycles(); j++) {

            //Force close and reopen of manager and database to ensure cold
            //start before doc creation
            try {
                tearDown();

                manager = new Manager(new LiteTestContext(), Manager.DEFAULT_OPTIONS);
                database = manager.getDatabase(DEFAULT_TEST_DB);
            }
            catch(Exception ex)
            {
                Log.e(TAG, "DB teardown", ex);
                fail();
            }

            for (int k = 0; k < getNumberOfDocuments(); k++) {
                //create a document
                Map<String, Object> props = new HashMap<String, Object>();
                props.put("bigArray", bigObj);

                Body body = new Body(props);
                RevisionInternal rev1 = new RevisionInternal(body);

                Status status = new Status();
                try {
                    rev1 = database.putRevision(rev1, null, false, status);
                } catch (Throwable t) {
                    Log.e(TAG, "Document creation failed", t);
                    fail();
                }
            }
        }

        Log.v("PerformanceStats",TAG+","+Long.valueOf(System.currentTimeMillis()-startMillis).toString()+","+getNumberOfDocuments()+","+getSizeOfDocument()+",,"+getNumberOfShutAndReloadCycles());

    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("Test9_sizeOfDocument"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("Test9_numberOfDocuments"));
    }

    private int getNumberOfShutAndReloadCycles() {
        return Integer.parseInt(System.getProperty("Test9_numberOfShutAndReloadCycles"));
    }


}
