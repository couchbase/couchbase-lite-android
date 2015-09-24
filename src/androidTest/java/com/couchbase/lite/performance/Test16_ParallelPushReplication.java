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
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Test16_ParallelPushReplication extends LiteTestCaseWithDB {

    public static final String TAG = "PushReplicationPerformance";

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "DeleteDBPerformance setUp");
        super.setUp();

        if (!performanceTestsEnabled()) {
            return;
        }

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

    /*
     * Test expects 4 remote datbases named db0, db1, db2, db3
     */
    public void testPushReplicationPerformance() throws Exception {

        if (!performanceTestsEnabled()) {
            return;
        }

        long startMillis = System.currentTimeMillis();

        URL remote0 = getReplicationSubURL("0");
        URL remote1 = getReplicationSubURL("1");
        URL remote2 = getReplicationSubURL("2");
        URL remote3 = getReplicationSubURL("3");

        final Replication repl0 = database.createPushReplication(remote0);
        repl0.setContinuous(false);
        if (!isSyncGateway(remote0)) {
            repl0.setCreateTarget(true);
            Assert.assertTrue(repl0.shouldCreateTarget());
        }

        final Replication repl1 = database.createPushReplication(remote1);
        repl1.setContinuous(false);
        if (!isSyncGateway(remote1)) {
            repl1.setCreateTarget(true);
            Assert.assertTrue(repl1.shouldCreateTarget());
        }

        final Replication repl2 = database.createPushReplication(remote2);
        repl2.setContinuous(false);
        if (!isSyncGateway(remote2)) {
            repl2.setCreateTarget(true);
            Assert.assertTrue(repl2.shouldCreateTarget());
        }

        final Replication repl3 = database.createPushReplication(remote3);
        repl3.setContinuous(false);
        if (!isSyncGateway(remote3)) {
            repl3.setCreateTarget(true);
            Assert.assertTrue(repl3.shouldCreateTarget());
        }

        Thread t0 = new Thread() {
            public void run() {
                try {
                    runReplication(repl0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Thread t1 = new Thread() {
            public void run() {
                try {
                    runReplication(repl1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Thread t2 = new Thread() {
            public void run() {
                try {
                    runReplication(repl2);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        t0.start();
        t1.start();
        t2.start();

        runReplication(repl3);

        Log.d(TAG, "testPusher() finished");

        Log.v("PerformanceStats", TAG + "," + Long.valueOf(System.currentTimeMillis() - startMillis).toString() + "," + getNumberOfDocuments());

    }

    private boolean isSyncGateway(URL remote) {
        return (remote.getPort() == 4984);
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

