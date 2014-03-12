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
import com.couchbase.lite.threading.BackgroundTask;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Test6_PushReplication extends LiteTestCase {

    public static final String TAG = "PushReplicationPerformance";

    private static final String _propertyValue = "1234567";

    public void testPushReplicationPerformance() throws CouchbaseLiteException {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        String doc1Id;
        String docIdTimestamp = Long.toString(System.currentTimeMillis());

        URL remote = getReplicationURL();
        doc1Id = createDocumentsForPushReplication(docIdTimestamp);
        Map<String, Object> documentProperties;


        final boolean continuous = false;
        final Replication repl = database.createPushReplication(remote);
        repl.setContinuous(continuous);
        if (!isSyncGateway(remote)) {
            repl.setCreateTarget(true);
            Assert.assertTrue(repl.shouldCreateTarget());
        }

        // Check the replication's properties:
        Assert.assertEquals(database, repl.getLocalDatabase());
        Assert.assertEquals(remote, repl.getRemoteUrl());
        Assert.assertFalse(repl.isPull());
        Assert.assertFalse(repl.isContinuous());
        Assert.assertNull(repl.getFilter());
        Assert.assertNull(repl.getFilterParams());
        // TODO: CAssertNil(r1.doc_ids);
        // TODO: CAssertNil(r1.headers);

        // Check that the replication hasn't started running:
        Assert.assertFalse(repl.isRunning());
        Assert.assertEquals(Replication.ReplicationStatus.REPLICATION_STOPPED, repl.getStatus());
        Assert.assertEquals(0, repl.getCompletedChangesCount());
        Assert.assertEquals(0, repl.getChangesCount());
        Assert.assertNull(repl.getLastError());

        runReplication(repl);

        // make sure doc1 is there
        try {
            verifyRemoteDocExists(remote, doc1Id);
        } catch (MalformedURLException murlex) {
            Log.e(TAG, "Document create failed", murlex);
            fail();
        }

        // add doc3
        documentProperties = new HashMap<String, Object>();
        String doc3Id = String.format("doc3-%s", docIdTimestamp);
        Document doc3 = database.getDocument(doc3Id);
        documentProperties.put("bat", 677);
        doc3.putProperties(documentProperties);

        // re-run push replication
        final Replication repl2 = database.createPushReplication(remote);
        repl2.setContinuous(continuous);
        if (!isSyncGateway(remote)) {
            repl2.setCreateTarget(true);
        }
        runReplication(repl2);

        // make sure the doc has been added
        try {
            verifyRemoteDocExists(remote, doc3Id);
        } catch (MalformedURLException murlex) {
            Log.e(TAG, "Verify remote doc failed", murlex);
            fail();
        }

        Log.d(TAG, "testPusher() finished");
    }

    private String createDocumentsForPushReplication(String docIdTimestamp) throws CouchbaseLiteException {
        String doc1Id;
        String doc2Id;// Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        doc1Id = String.format("doc1-%s", docIdTimestamp);
        documentProperties.put("_id", doc1Id);
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        Body body = new Body(documentProperties);
        RevisionInternal rev1 = new RevisionInternal(body, database);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);
        assertEquals(Status.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        RevisionInternal rev2 = database.putRevision(new RevisionInternal(documentProperties, database), rev1.getRevId(), false, status);
        assertEquals(Status.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        doc2Id = String.format("doc2-%s", docIdTimestamp);
        documentProperties.put("_id", doc2Id);
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new RevisionInternal(documentProperties, database), null, false, status);
        assertEquals(Status.CREATED, status.getCode());
        return doc1Id;
    }

    private boolean isSyncGateway(URL remote) {
        return (remote.getPort() == 4984 || remote.getPort() == 4984);
    }

    private void verifyRemoteDocExists(URL remote, final String doc1Id) throws MalformedURLException {
        URL replicationUrlTrailing = new URL(String.format("%s/", remote.toExternalForm()));
        final URL pathToDoc = new URL(replicationUrlTrailing, doc1Id);
        Log.d(TAG, "Send http request to " + pathToDoc);

        final CountDownLatch httpRequestDoneSignal = new CountDownLatch(1);
        BackgroundTask getDocTask = new BackgroundTask() {

            @Override
            public void run() {

                HttpClient httpclient = new DefaultHttpClient();

                HttpResponse response;
                String responseString = null;
                try {
                    response = httpclient.execute(new HttpGet(pathToDoc.toExternalForm()));
                    StatusLine statusLine = response.getStatusLine();
                    assertTrue(statusLine.getStatusCode() == HttpStatus.SC_OK);
                    if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        response.getEntity().writeTo(out);
                        out.close();
                        responseString = out.toString();
                        assertTrue(responseString.contains(doc1Id));
                        Log.d(TAG, "result: " + responseString);

                    } else {
                        //Closes the connection.
                        response.getEntity().getContent().close();
                        throw new IOException(statusLine.getReasonPhrase());
                    }
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
}

