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
import com.couchbase.lite.LiteTestCase;
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

public class Test6_PushReplication extends LiteTestCase {

    public static final String TAG = "PushReplicationPerformance";

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "DeleteDBPerformance setUp");
        super.setUp();

        String docIdTimestamp = Long.toString(System.currentTimeMillis());

        for (int i = 0; i < getNumberOfDocuments(); i++) {
            String docId = String.format("doc%d-%s", i, docIdTimestamp);

            try {
                addDocWithId(docId, "attachment.png", false);
                //addDocWithId(docId, null, false);
            } catch (IOException ioex) {
                Log.e(TAG, "Add document directly to sync gateway failed", ioex);
                fail();
            }
        }
    }

    public void testPushReplicationPerformance() throws CouchbaseLiteException {

        long startMillis = System.currentTimeMillis();

        URL remote = getReplicationURL();

        final Replication repl = database.createPushReplication(remote);
        repl.setContinuous(false);
        if (!isSyncGateway(remote)) {
            repl.setCreateTarget(true);
            Assert.assertTrue(repl.shouldCreateTarget());
        }

        runReplication(repl);

        Log.d(TAG, "testPusher() finished");

        Log.v("PerformanceStats", TAG + "," + Long.valueOf(System.currentTimeMillis() - startMillis).toString() + "," + getNumberOfDocuments());

    }

    private boolean isSyncGateway(URL remote) {
        return (remote.getPort() == 4984 || remote.getPort() == 4984);
    }

    private void addDocWithId(String docId, String attachmentName, boolean gzipped) throws IOException, CouchbaseLiteException {

        final String docJson;
        final Map<String, Object> documentProperties = new HashMap<String, Object>();

        if (attachmentName == null) {
            documentProperties.put("foo", 1);
            documentProperties.put("bar", false);
            Document doc = database.getDocument(docId);
            doc.putProperties(documentProperties);
        } else {
            // add attachment to document
            InputStream attachmentStream = getAsset(attachmentName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(attachmentStream, baos);
            if (gzipped == false) {
                String attachmentBase64 = Base64.encodeBytes(baos.toByteArray());
                documentProperties.put("foo", 1);
                documentProperties.put("bar", false);
                Map<String, Object> attachment = new HashMap<String, Object>();
                attachment.put("content_type", "image/png");
                attachment.put("data", attachmentBase64);
                Map<String, Object> attachments = new HashMap<String, Object>();
                attachments.put(attachmentName, attachment);
                documentProperties.put("_attachments", attachments);
                Document doc = database.getDocument(docId);
                doc.putProperties(documentProperties);
            } else {
                byte[] bytes = baos.toByteArray();
                String attachmentBase64 = Base64.encodeBytes(bytes, Base64.GZIP);
                documentProperties.put("foo", 1);
                documentProperties.put("bar", false);
                Map<String, Object> attachment = new HashMap<String, Object>();
                attachment.put("content_type", "image/png");
                attachment.put("data", attachmentBase64);
                attachment.put("encoding", "gzip");
                attachment.put("length", bytes.length);

                Map<String, Object> attachments = new HashMap<String, Object>();
                attachments.put(attachmentName, attachment);
                documentProperties.put("_attachments", attachments);
                Document doc = database.getDocument(docId);
                doc.putProperties(documentProperties);
            }
        }
    }


    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("Test6_numberOfDocuments"));
    }
}

