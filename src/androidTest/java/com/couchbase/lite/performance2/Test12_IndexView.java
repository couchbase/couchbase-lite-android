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
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LitePerfTestCase;
import com.couchbase.lite.LiteTestCase;
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
import com.couchbase.lite.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test12_IndexView extends LitePerfTestCase {

    public static final String TAG = "Test12_IndexView";
    private static final String _propertyValue = "1";

    public double runOne(final int numberOfDocuments, final int sizeOfDocuments) throws Exception {
        final StringBuffer bigObj = new StringBuffer(sizeOfDocuments);
        for (int i = 0; i < sizeOfDocuments; i++) {
            bigObj.append(_propertyValue);
        }

        View view = database.getView("vacant");
        view.setMapReduce(
                new Mapper() {
                    public void map(Map<String, Object> document, Emitter emitter) {
                        Boolean vacant = (Boolean) document.get("vacant");
                        String name = (String) document.get("name");
                        if (vacant && name != null) {
                            emitter.emit(name, vacant);
                        }
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
                for (int i = 0; i < numberOfDocuments; i++) {
                    String name = String.format("%s",bigObj);
                    boolean vacant = ((i + 2) % 2 == 0) ? true : false;
                    Map<String,Object> props = new HashMap<String,Object>();

                    props.put("name",name);
                    props.put("apt",i);
                    props.put("phone",408100000 + i);
                    props.put("vacant",vacant);

                    Document doc = database.createDocument();
                    try {
                        doc.putProperties(props);
                    }
                    catch(CouchbaseLiteException cblex)
                    {
                        Log.v("PerformanceStats",TAG+", Failed to create doc "+props,cblex);
                        return false;
                    }
                }
                return true;
            }
        });

        long startMillis = System.currentTimeMillis();
        view.updateIndex();
        double executionTime = Long.valueOf(System.currentTimeMillis()-startMillis);
        Log.v("PerformanceStats",TAG+","+executionTime+","+numberOfDocuments+","+sizeOfDocuments);
        return executionTime;
    }
}
