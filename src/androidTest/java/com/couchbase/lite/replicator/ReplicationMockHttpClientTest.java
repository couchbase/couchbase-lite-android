/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.replicator;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.Manager;
import com.couchbase.lite.SavedRevision;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by hideki on 6/8/16.
 */
public class ReplicationMockHttpClientTest extends LiteTestCaseWithDB {
    /**
     * Regression test for issue couchbase/couchbase-lite-android#174
     */
    public void testAllLeafRevisionsArePushed() throws Exception {
        final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderRevDiffsAllMissing();
        interceptor.setResponseDelayMilliseconds(250);
        interceptor.addResponderFakeLocalDocumentUpdate404();
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        manager.setDefaultHttpClientFactory(httpClientFactory);

        Document doc = database.createDocument();
        SavedRevision rev1a = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1a, false);
        SavedRevision rev3a = createRevisionWithRandomProps(rev2a, false);

        // delete the branch we've been using, then create a new one to replace it
        SavedRevision rev4a = rev3a.deleteDocument();
        SavedRevision rev2b = createRevisionWithRandomProps(rev1a, true);
        assertEquals(rev2b.getId(), doc.getCurrentRevisionId());

        // sync with remote DB -- should push both leaf revisions
        Replication push = database.createPushReplication(getReplicationURL());

        runReplication(push);
        assertNull(push.getLastError());

        // find the _revs_diff captured request and decode into json
        boolean foundRevsDiff = false;
        for (Request request : interceptor.getCapturedRequests()) {
            if ("POST".equals(request.method()) &&
                    request.url().pathSegments().get(1).equals("_revs_diff")) {
                foundRevsDiff = true;
                Map<String, Object> jsonMap = OkHttpUtils.getJsonMapFromRequest(request);
                // assert that it contains the expected revisions
                List<String> revisionIds = (List) jsonMap.get(doc.getId());
                assertEquals(2, revisionIds.size());
                assertTrue(revisionIds.contains(rev4a.getId()));
                assertTrue(revisionIds.contains(rev2b.getId()));
            }
        }
        assertTrue(foundRevsDiff);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/95
     */
    public void testPushReplicationCanMissDocs() throws Exception {
        assertEquals(0, database.getLastSequenceNumber());

        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("doc1", "testPushReplicationCanMissDocs");
        final Document doc1 = createDocWithProperties(properties1);

        Map<String, Object> properties2 = new HashMap<String, Object>();
        properties1.put("doc2", "testPushReplicationCanMissDocs");
        final Document doc2 = createDocWithProperties(properties2);

        UnsavedRevision doc2UnsavedRev = doc2.createRevision();
        InputStream attachmentStream = getAsset("attachment.png");
        doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderFakeLocalDocumentUpdate404();
        interceptor.setResponder("_bulk_docs", new CustomizableMockInterceptor.Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                String json = "{\"error\":\"not_found\",\"reason\":\"missing\"}";
                return new Response.Builder()
                        .request(request)
                        .code(404)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(OkHttpUtils.JSON, json)).build();
            }
        });
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        manager.setDefaultHttpClientFactory(httpClientFactory);

        // create a replication obeserver to wait until replication finishes
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver =
                new ReplicationFinishedObserver(replicationDoneSignal);

        // create replication and add observer
        //manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(getReplicationURL());
        pusher.addChangeListener(replicationFinishedObserver);

        // save the checkpoint id for later usage
        String checkpointId = pusher.remoteCheckpointDocID();

        // kick off the replication
        pusher.start();

        // wait for it to finish
        assertTrue(replicationDoneSignal.await(60, TimeUnit.SECONDS));
        Log.d(TAG, "replicationDoneSignal finished");

        // we would expect it to have recorded an error because one of the docs (the one without the attachment)
        // will have failed.
        assertNotNull(pusher.getLastError());

        // workaround for the fact that the replicationDoneSignal.wait() call will unblock before all
        // the statements in Replication.stopped() have even had a chance to execute.
        // (specifically the ones that come after the call to notifyChangeListeners())
        Thread.sleep(500);

        String localLastSequence = database.lastSequenceWithCheckpointId(checkpointId);

        Log.d(TAG, "database.lastSequenceWithCheckpointId(): " + localLastSequence);
        Log.d(TAG, "doc2.getCurrentRevision().getSequence(): " + doc2.getCurrentRevision().getSequence());
        String msg = "Since doc1 failed, the database should _not_ have had its lastSequence bumped" +
                " to doc2's sequence number.  If it did, it's bug: github.com/couchbase/couchbase-lite-java-core/issues/95";
        assertFalse(msg, Long.toString(doc2.getCurrentRevision().getSequence()).equals(localLastSequence));
        assertNull(localLastSequence);
        assertTrue(doc2.getCurrentRevision().getSequence() > 0);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/66
     */
    public void testPushUpdatedDocWithoutReSendingAttachments() throws Exception {

        assertEquals(0, database.getLastSequenceNumber());

        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("dynamic", 1);
        final Document doc = createDocWithProperties(properties1);
        SavedRevision doc1Rev = doc.getCurrentRevision();

        // Add attachment to document
        UnsavedRevision doc2UnsavedRev = doc.createRevision();
        InputStream attachmentStream = getAsset("attachment.png");
        doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderFakeLocalDocumentUpdate404();
        // http://url/{db}/{docid}
        interceptor.setResponder(doc.getId(), new CustomizableMockInterceptor.Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                Map<String, Object> responseObject = new HashMap<String, Object>();
                responseObject.put("id", doc.getId());
                responseObject.put("ok", true);
                responseObject.put("rev", doc.getCurrentRevisionId());
                String json = Manager.getObjectMapper().writeValueAsString(responseObject);
                return new Response.Builder()
                        .request(request)
                        .code(200)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(OkHttpUtils.JSON, json)).build();
            }
        });
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        manager.setDefaultHttpClientFactory(httpClientFactory);

        // create replication and add observer
        Replication pusher = database.createPushReplication(getReplicationURL());
        runReplication(pusher);

        for (Request request : interceptor.getCapturedRequests()) {
            // verify that there are no PUT requests with attachments
            //if("PUT".equals(request.method()))
            //    assertFalse("multipart".equals(request.body().contentType().type()));
        }

        interceptor.clearCapturedRequests();
        assertEquals(0, interceptor.getCapturedRequests().size());

        Document oldDoc = database.getDocument(doc.getId());
        UnsavedRevision aUnsavedRev = oldDoc.createRevision();
        Map<String, Object> prop = new HashMap<String, Object>();
        prop.putAll(oldDoc.getProperties());
        prop.put("dynamic", (Integer) oldDoc.getProperty("dynamic") + 1);
        aUnsavedRev.setProperties(prop);
        final SavedRevision savedRev = aUnsavedRev.save();

        //{db}/_revs_diff
        final String json = String.format(Locale.ENGLISH,
                "{\"%s\":{\"missing\":[\"%s\"],\"possible_ancestors\":[\"%s\",\"%s\"]}}",
                doc.getId(), savedRev.getId(), doc1Rev.getId(), doc2Rev.getId());
        interceptor.setResponder("_revs_diff", new CustomizableMockInterceptor.Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                return new Response.Builder()
                        .request(request)
                        .code(200)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(OkHttpUtils.JSON, json)).build();
            }
        });
        //{db}/{doc_id}
        interceptor.setResponder(doc.getId(), new CustomizableMockInterceptor.Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                Map<String, Object> responseObject = new HashMap<String, Object>();
                responseObject.put("id", doc.getId());
                responseObject.put("ok", true);
                responseObject.put("rev", savedRev.getId());
                String json = Manager.getObjectMapper().writeValueAsString(responseObject);
                return new Response.Builder()
                        .request(request)
                        .code(200)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(OkHttpUtils.JSON, json)).build();
            }
        });

        pusher = database.createPushReplication(getReplicationURL());
        runReplication(pusher);

        for (Request request : interceptor.getCapturedRequests()) {
            // verify that there are no PUT requests with attachments
            if ("PUT".equals(request.method()))
                assertFalse("multipart".equals(request.body().contentType().type()));
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/188
     */
    public void testServerDoesNotSupportMultipart() throws Exception {

        final AtomicInteger counter = new AtomicInteger();

        assertEquals(0, database.getLastSequenceNumber());

        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("dynamic", 1);
        final Document doc = createDocWithProperties(properties1);
        SavedRevision doc1Rev = doc.getCurrentRevision();

        // Add attachment to document
        UnsavedRevision doc2UnsavedRev = doc.createRevision();
        InputStream attachmentStream = getAsset("attachment.png");
        doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderFakeLocalDocumentUpdate404();
        // first http://url/{db}/{docid}
        interceptor.setResponder(doc.getId(), new CustomizableMockInterceptor.Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                // First: Reject multipart PUT with response code 415
                if (counter.intValue() == 0) {
                    counter.incrementAndGet();
                    String json = "{\"error\":\"Unsupported Media Type\",\"reason\":\"missing\"}";
                    return new Response.Builder()
                            .request(request)
                            .code(415)
                            .protocol(Protocol.HTTP_1_1)
                            .body(ResponseBody.create(OkHttpUtils.JSON, json)).build();
                }
                // second call should be plain json, return good response
                else {
                    Map<String, Object> responseObject = new HashMap<String, Object>();
                    responseObject.put("id", doc.getId());
                    responseObject.put("ok", true);
                    responseObject.put("rev", doc.getCurrentRevisionId());
                    String json = Manager.getObjectMapper().writeValueAsString(responseObject);
                    return new Response.Builder()
                            .request(request)
                            .code(200)
                            .protocol(Protocol.HTTP_1_1)
                            .body(ResponseBody.create(OkHttpUtils.JSON, json)).build();
                }
            }
        });
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        manager.setDefaultHttpClientFactory(httpClientFactory);

        // create replication and add observer
        Replication pusher = database.createPushReplication(getReplicationURL());

        runReplication(pusher);

        int entityIndex = 0;
        for (Request request : interceptor.getCapturedRequests()) {
            // verify that there are no PUT requests with attachments
            if ("PUT".equals(request.method())) {
                if (entityIndex++ == 0)
                    assertTrue("multipart".equals(request.body().contentType().type()));
                else
                    assertFalse("multipart".equals(request.body().contentType().type()));
            }
        }
    }

    /**
     * Reproduces https://github.com/couchbase/couchbase-lite-android/issues/167
     */
    public void testPushPurgedDoc() throws Throwable {
        int numBulkDocRequests = 0;
        Request lastBulkDocsRequest = null;

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testPurgeDocument");
        Document doc = createDocumentWithProperties(database, properties);
        assertNotNull(doc);

        final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderRevDiffsAllMissing();
        interceptor.setResponseDelayMilliseconds(250);
        interceptor.addResponderFakeLocalDocumentUpdate404();
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        manager.setDefaultHttpClientFactory(httpClientFactory);

        Replication pusher = database.createPushReplication(getReplicationURL());
        pusher.setContinuous(true);

        final CountDownLatch replicationCaughtUpSignal = new CountDownLatch(1);

        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                final int changesCount = event.getSource().getChangesCount();
                final int completedChangesCount = event.getSource().getCompletedChangesCount();
                String msg = String.format(Locale.ENGLISH, "changes: %d completed changes: %d", changesCount, completedChangesCount);
                Log.d(TAG, msg);
                if (changesCount == completedChangesCount && changesCount != 0) {
                    replicationCaughtUpSignal.countDown();
                }
            }
        });
        pusher.start();

        // wait until that doc is pushed
        boolean didNotTimeOut = replicationCaughtUpSignal.await(60, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

        // at this point, we should have captured exactly 1 bulk docs request
        numBulkDocRequests = 0;
        for (Request request : interceptor.getCapturedRequests()) {
            if ("POST".equals(request.method()) &&
                    request.url().pathSegments().get(1).equals("_bulk_docs")) {
                lastBulkDocsRequest = request;
                numBulkDocRequests += 1;
            }
        }
        assertEquals(1, numBulkDocRequests);

        // that bulk docs request should have the "start" key under its _revisions
        Map<String, Object> jsonMap = OkHttpUtils.getJsonMapFromRequest(lastBulkDocsRequest);
        List docs = (List) jsonMap.get("docs");
        Map<String, Object> onlyDoc = (Map) docs.get(0);
        Map<String, Object> revisions = (Map) onlyDoc.get("_revisions");
        assertTrue(revisions.containsKey("start"));

        // now add a new revision, which will trigger the pusher to try to push it
        properties = new HashMap<String, Object>();
        properties.put("testName2", "update doc");
        UnsavedRevision unsavedRevision = doc.createRevision();
        unsavedRevision.setUserProperties(properties);
        unsavedRevision.save();

        // but then immediately purge it
        doc.purge();

        // wait for a while to give the replicator a chance to push it
        // (it should not actually push anything)
        Thread.sleep(5 * 1000);

        // we should not have gotten any more _bulk_docs requests, because
        // the replicator should not have pushed anything else.
        // (in the case of the bug, it was trying to push the purged revision)
        numBulkDocRequests = 0;
        for (Request request : interceptor.getCapturedRequests()) {
            if ("POST".equals(request.method()) &&
                    request.url().pathSegments().get(1).equals("_bulk_docs")) {
                lastBulkDocsRequest = request;
                numBulkDocRequests += 1;
            }
        }
        assertEquals(1, numBulkDocRequests);

        stopReplication(pusher);
    }

    /**
     * Regression test for https://github.com/couchbase/couchbase-lite-java-core/issues/72
     */
    public void testPusherBatching() throws Throwable {
        int previous = ReplicationInternal.INBOX_CAPACITY;
        ReplicationInternal.INBOX_CAPACITY = 5;
        try {
            // create a bunch local documents
            int numDocsToSend = ReplicationInternal.INBOX_CAPACITY * 3;
            for (int i = 0; i < numDocsToSend; i++) {
                Map<String, Object> properties = new HashMap<String, Object>();
                properties.put("testPusherBatching", i);
                createDocumentWithProperties(database, properties);
            }

            final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
            interceptor.addResponderFakeLocalDocumentUpdate404();
            HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
                @Override
                public OkHttpClient getOkHttpClient() {
                    return new OkHttpClient.Builder().addInterceptor(interceptor).build();
                }
            };
            manager.setDefaultHttpClientFactory(httpClientFactory);

            Replication pusher = database.createPushReplication(getReplicationURL());
            runReplication(pusher);
            assertNull(pusher.getLastError());

            int numDocsSent = 0;

            // verify that only INBOX_SIZE documents are included in any given bulk post request
            for (Request request : interceptor.getCapturedRequests()) {
                if ("POST".equals(request.method()) &&
                        request.url().pathSegments().get(1).equals("_bulk_docs")) {
                    Map<String, Object> body = OkHttpUtils.getJsonMapFromRequest(request);
                    ArrayList docs = (ArrayList) body.get("docs");
                    String msg = "# of bulk docs pushed should be <= INBOX_CAPACITY";
                    assertTrue(msg, docs.size() <= ReplicationInternal.INBOX_CAPACITY);
                    numDocsSent += docs.size();
                }
            }

            assertEquals(numDocsToSend, numDocsSent);
        } finally {
            ReplicationInternal.INBOX_CAPACITY = previous;
        }
    }

    public void testRunReplicationWithError() throws Exception {
        final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
        interceptor.addResponderThrowExceptionAllRequests();
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
            @Override
            public OkHttpClient getOkHttpClient() {
                return new OkHttpClient.Builder().addInterceptor(interceptor).build();
            }
        };
        manager.setDefaultHttpClientFactory(httpClientFactory);

        Replication r1 = database.createPushReplication(getReplicationURL());

        final CountDownLatch changeEventError = new CountDownLatch(1);
        r1.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.d(TAG, "change event: %s", event);
                if (event.getError() != null) {
                    changeEventError.countDown();
                }
            }
        });
        Assert.assertFalse(r1.isContinuous());
        runReplication(r1);

        // It should have failed with a 404:
        Assert.assertEquals(0, r1.getCompletedChangesCount());
        Assert.assertEquals(0, r1.getChangesCount());
        Assert.assertNotNull(r1.getLastError());
        boolean success = changeEventError.await(5, TimeUnit.SECONDS);
        Assert.assertTrue(success);
    }

    /**
     * Verify that running a one-shot push replication will complete when run against a
     * mock server that throws io exceptions on every request.
     */
    public void testOneShotReplicationErrorNotification() throws Throwable {
        int previous = RemoteRequestRetry.RETRY_DELAY_MS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;
        try {
            final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
            interceptor.addResponderThrowExceptionAllRequests();
            HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
                @Override
                public OkHttpClient getOkHttpClient() {
                    return new OkHttpClient.Builder().addInterceptor(interceptor).build();
                }
            };
            manager.setDefaultHttpClientFactory(httpClientFactory);

            Replication pusher = database.createPushReplication(getReplicationURL());
            runReplication(pusher);
            assertTrue(pusher.getLastError() != null);
        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = previous;
        }
    }

    /**
     * Verify that running a continuous push replication will emit a change while
     * in an error state when run against a mock server that returns 500 Internal Server
     * errors on every request.
     */
    public void testContinuousReplicationErrorNotification() throws Throwable {
        int previous = RemoteRequestRetry.RETRY_DELAY_MS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;
        try {
            final CustomizableMockInterceptor interceptor = new CustomizableMockInterceptor();
            interceptor.addResponderThrowExceptionAllRequests();
            HttpClientFactory httpClientFactory = new DefaultHttpClientFactory() {
                @Override
                public OkHttpClient getOkHttpClient() {
                    return new OkHttpClient.Builder().addInterceptor(interceptor).build();
                }
            };
            manager.setDefaultHttpClientFactory(httpClientFactory);

            Replication pusher = database.createPushReplication(getReplicationURL());
            pusher.setContinuous(true);
            // add replication observer
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            pusher.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getError() != null) {
                        countDownLatch.countDown();
                    }
                }
            });
            // start replication
            pusher.start();
            assertTrue(countDownLatch.await(30, TimeUnit.SECONDS));
            stopReplication(pusher);
        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = previous;
        }
    }
}
