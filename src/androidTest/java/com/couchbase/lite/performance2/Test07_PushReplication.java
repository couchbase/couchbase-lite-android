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
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.threading.BackgroundTask;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Test07_PushReplication extends LitePerfTestCase {

    public static final String TAG = "Test6_PushReplication";
    private static final String _propertyValue = "1";

    public double runOne(final int numberOfDocuments, final int sizeOfDocuments) throws Exception {
        String[] bigObj = new String[sizeOfDocuments];
        for (int i = 0; i < sizeOfDocuments; i++) {
            bigObj[i] = _propertyValue;
        }
        final Map<String, Object> props = new HashMap<String, Object>();
        props.put("bigArray", bigObj);

        String docIdTimestamp = Long.toString(System.currentTimeMillis());

        for (int i = 0; i < numberOfDocuments; i++) {
            String docId = String.format("doc%d-%s", i, docIdTimestamp);

            try {
                addDocWithId(docId, props, "attachment.png", false);
                //addDocWithId(docId, null, false);
            } catch (IOException ioex) {
                Log.v("PerformanceStats",TAG+", Add document directly to sync gateway failed", ioex);
                fail();
            }
        }

        URL remote = getReplicationURL();

        long startMillis = System.currentTimeMillis();

        final Replication repl = database.createPushReplication(remote);
        repl.setContinuous(false);
        if (!isSyncGateway(remote)) {
            repl.setCreateTarget(true);
            Assert.assertTrue(repl.shouldCreateTarget());
        }
        Log.v("PerformanceStats",TAG+", Starting push operation with: " + repl);
        runReplication(repl);
        Log.v("PerformanceStats",TAG+", Push operation finished with: " + repl);

        double executionTime = Long.valueOf(System.currentTimeMillis()-startMillis);
        Log.v("PerformanceStats",TAG+", "+executionTime+","+numberOfDocuments+","+sizeOfDocuments);
        return executionTime;
    }


}

