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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Test7_PullReplication extends LiteTestCase {

    public static final String TAG = "PullReplicationPerformance";

    private static final String _propertyValue = "1234567";

    public void testPullReplicationPerformance() throws CouchbaseLiteException {


        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        final String doc2Id = String.format("doc2-%s", docIdTimestamp);

        Log.d(TAG, "Adding " + doc1Id + " directly to sync gateway");

        try {
            addDocWithId(doc1Id, "attachment.png", false);
        } catch (IOException ioex) {
            Log.e(TAG, "Add document directly to sync gateway failed", ioex);
            fail();
        }

        Log.d(TAG, "Adding " + doc2Id + " directly to sync gateway");

        try {
            addDocWithId(doc2Id, "attachment2.png", false);
        } catch (IOException ioex) {
            Log.e(TAG, "Add document directly to sync gateway failed", ioex);
            fail();
        }

        doPullReplication();

        assertNotNull(database);
        Log.d(TAG, "Fetching doc1 via id: " + doc1Id);
        Document doc1 = database.getDocument(doc1Id);
        Log.d(TAG, "doc1" + doc1);
        assertNotNull(doc1);
        assertNotNull(doc1.getCurrentRevisionId());
        assertTrue(doc1.getCurrentRevisionId().startsWith("1-"));
        assertNotNull(doc1.getProperties());
        assertEquals(1, doc1.getProperties().get("foo"));

        Log.d(TAG, "Fetching doc2 via id: " + doc2Id);
        Document doc2 = database.getDocument(doc2Id);
        assertNotNull(doc2);
        assertNotNull(doc2.getCurrentRevisionId());
        assertNotNull(doc2.getProperties());

        assertTrue(doc2.getCurrentRevisionId().startsWith("1-"));
        assertEquals(1, doc2.getProperties().get("foo"));

        // update doc1 on sync gateway
        String docJson = String.format("{\"foo\":2,\"bar\":true,\"_rev\":\"%s\",\"_id\":\"%s\"}", doc1.getCurrentRevisionId(), doc1.getId());

        try {
            pushDocumentToSyncGateway(doc1.getId(), docJson);
        } catch (IOException ioex) {
            Log.e(TAG, "Add document directly to sync gateway failed", ioex);
            fail();
        }

        // do another pull
        Log.d(TAG, "Doing 2nd pull replication");
        doPullReplication();
        Log.d(TAG, "Finished 2nd pull replication");

        // make sure it has the latest properties
        Document doc1Fetched = database.getDocument(doc1Id);
        assertNotNull(doc1Fetched);
        assertTrue(doc1Fetched.getCurrentRevisionId().startsWith("2-"));
        assertEquals(2, doc1Fetched.getProperties().get("foo"));

        Log.d(TAG, "testPuller() finished");
    }

    private void doPullReplication() {
        URL remote = getReplicationURL();

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        final Replication repl = (Replication) database.createPullReplication(remote);
        repl.setContinuous(false);

        Log.d(TAG, "Doing pull replication with: " + repl);
        runReplication(repl);
        Log.d(TAG, "Finished pull replication with: " + repl);


    }


    private void addDocWithId(String docId, String attachmentName, boolean gzipped) throws IOException {

        final String docJson;

        if (attachmentName != null) {
            // add attachment to document
            InputStream attachmentStream = getAsset(attachmentName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(attachmentStream, baos);
            if (gzipped == false) {
                String attachmentBase64 = Base64.encodeBytes(baos.toByteArray());
                docJson = String.format("{\"foo\":1,\"bar\":false, \"_attachments\": { \"%s\": { \"content_type\": \"image/png\", \"data\": \"%s\" } } }", attachmentName, attachmentBase64);
            } else {
                byte[] bytes = baos.toByteArray();
                String attachmentBase64 = Base64.encodeBytes(bytes, Base64.GZIP);
                docJson = String.format("{\"foo\":1,\"bar\":false, \"_attachments\": { \"%s\": { \"content_type\": \"image/png\", \"data\": \"%s\", \"encoding\": \"gzip\", \"length\":%d } } }", attachmentName, attachmentBase64, bytes.length);
            }
        } else {
            docJson = "{\"foo\":1,\"bar\":false}";
        }
        pushDocumentToSyncGateway(docId, docJson);

        workaroundSyncGatewayRaceCondition();
    }

    private void pushDocumentToSyncGateway(String docId, final String docJson) throws MalformedURLException {
        // push a document to server
        URL replicationUrlTrailingDoc1 = new URL(String.format("%s/%s", getReplicationURL().toExternalForm(), docId));
        final URL pathToDoc1 = new URL(replicationUrlTrailingDoc1, docId);
        Log.d(TAG, "Send http request to " + pathToDoc1);

        final CountDownLatch httpRequestDoneSignal = new CountDownLatch(1);
        BackgroundTask getDocTask = new BackgroundTask() {

            @Override
            public void run() {

                HttpClient httpclient = new DefaultHttpClient();

                HttpResponse response;
                String responseString = null;
                try {
                    HttpPut post = new HttpPut(pathToDoc1.toExternalForm());
                    StringEntity se = new StringEntity(docJson.toString());
                    se.setContentType(new BasicHeader("content_type", "application/json"));
                    post.setEntity(se);
                    response = httpclient.execute(post);
                    StatusLine statusLine = response.getStatusLine();
                    Log.d(TAG, "Got response: " + statusLine);
                    assertTrue(statusLine.getStatusCode() == HttpStatus.SC_CREATED);
                } catch (ClientProtocolException e) {
                    assertNull("Got ClientProtocolException: " + e.getLocalizedMessage(), e);
                } catch (IOException e) {
                    assertNull("Got IOException: " + e.getLocalizedMessage(), e);
                }

                httpRequestDoneSignal.countDown();
            }


        };
        getDocTask.execute();

        Log.d(TAG, "Waiting for http request to finish");
        try {
            httpRequestDoneSignal.await(300, TimeUnit.SECONDS);
            Log.d(TAG, "http request finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Whenever posting information directly to sync gateway via HTTP, the client
     * must pause briefly to give it a chance to achieve internal consistency.
     * <p/>
     * This is documented in https://github.com/couchbase/sync_gateway/issues/228
     */
    private void workaroundSyncGatewayRaceCondition() {
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
