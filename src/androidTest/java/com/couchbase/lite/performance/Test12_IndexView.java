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
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.View;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test12_IndexView extends PerformanceTestCase {
    public static final String TAG = "IndexViewPerformance";

    @Override
    protected String getTestTag() {
        return TAG;
    }

    public void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled())
            return;

        View view = database.getView("vacant");
        view.setMapReduce(
            new Mapper() {
                public void map(Map<String, Object> document, Emitter emitter) {
                    Boolean vacant = (Boolean) document.get("vacant");
                    String name = (String) document.get("name");
                    if (vacant && name != null)
                        emitter.emit(name, vacant);
                }
            },
            new Reducer() {
                public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                    return View.totalValues(values);
                }
            },
            "1.0.0"
        );

        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    String name = String.format("%s%s", "n", i);
                    boolean vacant = ((i + 2) % 2 == 0) ? true : false;
                    Map<String,Object> props = new HashMap<String,Object>();
                    props.put("name", name);
                    props.put("apt", i);
                    props.put("phone", 408100000 + i);
                    props.put("vacant", vacant);

                    Document doc = database.createDocument();
                    try {
                        doc.putProperties(props);
                    } catch(CouchbaseLiteException e) {
                        Log.e(TAG,"!!! Failed to create doc " + props, e);
                        return false;
                    }
                }
                return true;
            }
        });
        assertTrue(success);
    }


    public void testViewIndexPerformance() throws CouchbaseLiteException {
        if (!performanceTestsEnabled())
            return;

        long start = System.currentTimeMillis();
        View view = database.getView("vacant");
        view.updateIndex();
        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + "");
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test12.numberOfDocuments"));
    }
}
