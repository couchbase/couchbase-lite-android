package com.couchbase.lite.replicator;

import com.couchbase.lite.Attachment;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.SavedRevision;
import com.couchbase.lite.Status;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.View;
import com.couchbase.lite.auth.FacebookAuthorizer;
import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.threading.BackgroundTask;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ReplicationTest extends LiteTestCase {

    public static final String TAG = "Replicator";

    // Reproduces issue #167
    // https://github.com/couchbase/couchbase-lite-android/issues/167
    public void testPushPurgedDoc() throws Throwable {

        int numBulkDocRequests = 0;
        HttpPost lastBulkDocsRequest = null;

        Map<String,Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testPurgeDocument");

        Document doc = createDocumentWithProperties(database, properties);
        assertNotNull(doc);

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderRevDiffsAllMissing();
        mockHttpClient.setResponseDelayMilliseconds(250);

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };

        URL remote = getReplicationURL();

        manager.setDefaultHttpClientFactory(mockHttpClientFactory);
        Replication pusher = database.createPushReplication(remote);
        pusher.setContinuous(true);
        pusher.start();

        final CountDownLatch replicationCaughtUpSignal = new CountDownLatch(1);

        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                final int changesCount = event.getSource().getChangesCount();
                final int completedChangesCount = event.getSource().getCompletedChangesCount();
                String msg = String.format("changes: %d completed changes: %d", changesCount, completedChangesCount);
                Log.d(TAG, msg);
                if (changesCount == completedChangesCount) {
                    replicationCaughtUpSignal.countDown();
                }
            }
        });

        // wait until that doc is pushed
        boolean didNotTimeOut = replicationCaughtUpSignal.await(5, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

        // at this point, we should have captured exactly 1 bulk docs request
        numBulkDocRequests = 0;
        for (HttpRequest capturedRequest : mockHttpClient.getCapturedRequests()) {
            if (capturedRequest instanceof  HttpPost && ((HttpPost) capturedRequest).getURI().toString().endsWith("_bulk_docs")) {
                lastBulkDocsRequest = (HttpPost) capturedRequest;
                numBulkDocRequests += 1;
            }
        }
        assertEquals(1, numBulkDocRequests);

        // that bulk docs request should have the "start" key under its _revisions
        Map<String, Object> jsonMap = mockHttpClient.getJsonMapFromRequest((HttpPost) lastBulkDocsRequest);
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
        assertTrue(doc.purge());

        // wait for a while to give the replicator a chance to push it
        // (it should not actually push anything)
        Thread.sleep(5*1000);

        // we should not have gotten any more _bulk_docs requests, because
        // the replicator should not have pushed anything else.
        // (in the case of the bug, it was trying to push the purged revision)
        numBulkDocRequests = 0;
        for (HttpRequest capturedRequest : mockHttpClient.getCapturedRequests()) {
            if (capturedRequest instanceof  HttpPost && ((HttpPost) capturedRequest).getURI().toString().endsWith("_bulk_docs")) {
                numBulkDocRequests += 1;
            }
        }
        assertEquals(1, numBulkDocRequests);


    }

    public void testPusher() throws Throwable {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        URL remote = getReplicationURL();
        String docIdTimestamp = Long.toString(System.currentTimeMillis());

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
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
        String doc2Id = String.format("doc2-%s", docIdTimestamp);
        documentProperties.put("_id", doc2Id);
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new RevisionInternal(documentProperties, database), null, false, status);
        assertEquals(Status.CREATED, status.getCode());

        final boolean continuous = false;
        final Replication repl = database.createPushReplication(remote);
        repl.setContinuous(continuous);

        repl.setCreateTarget(true);

        // Check the replication's properties:
        Assert.assertEquals(database, repl.getLocalDatabase());
        Assert.assertEquals(remote, repl.getRemoteUrl());
        Assert.assertFalse(repl.isPull());
        Assert.assertFalse(repl.isContinuous());
        Assert.assertTrue(repl.shouldCreateTarget());
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
        // TODO: make sure doc2 is there (refactoring needed)
        URL replicationUrlTrailing = new URL(String.format("%s/", remote.toExternalForm()));
        final URL pathToDoc = new URL(replicationUrlTrailing, doc1Id);
        Log.d(TAG, "Send http request to " + pathToDoc);

        final CountDownLatch httpRequestDoneSignal = new CountDownLatch(1);
        BackgroundTask getDocTask = new BackgroundTask() {

            @Override
            public void run() {

                org.apache.http.client.HttpClient httpclient = new DefaultHttpClient();

                HttpResponse response;
                String responseString = null;
                try {
                    response = httpclient.execute(new HttpGet(pathToDoc.toExternalForm()));
                    StatusLine statusLine = response.getStatusLine();
                    assertTrue(statusLine.getStatusCode() == HttpStatus.SC_OK);
                    if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        response.getEntity().writeTo(out);
                        out.close();
                        responseString = out.toString();
                        assertTrue(responseString.contains(doc1Id));
                        Log.d(TAG, "result: " + responseString);

                    } else{
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


        Log.d(TAG, "testPusher() finished");

    }

    public void testPusherDeletedDoc() throws Throwable {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        URL remote = getReplicationURL();
        String docIdTimestamp = Long.toString(System.currentTimeMillis());

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
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
        documentProperties.put("_deleted", true);

        @SuppressWarnings("unused")
        RevisionInternal rev2 = database.putRevision(new RevisionInternal(documentProperties, database), rev1.getRevId(), false, status);
        assertTrue(status.getCode() >= 200 && status.getCode() < 300);

        final Replication repl = database.createPushReplication(remote);
        ((Pusher)repl).setCreateTarget(true);

        runReplication(repl);


        // make sure doc1 is deleted
        URL replicationUrlTrailing = new URL(String.format("%s/", remote.toExternalForm()));
        final URL pathToDoc = new URL(replicationUrlTrailing, doc1Id);
        Log.d(TAG, "Send http request to " + pathToDoc);

        final CountDownLatch httpRequestDoneSignal = new CountDownLatch(1);
        BackgroundTask getDocTask = new BackgroundTask() {

            @Override
            public void run() {

                org.apache.http.client.HttpClient httpclient = new DefaultHttpClient();

                HttpResponse response;
                String responseString = null;
                try {
                    response = httpclient.execute(new HttpGet(pathToDoc.toExternalForm()));
                    StatusLine statusLine = response.getStatusLine();
                    Log.d(TAG, "statusLine " + statusLine);

                    assertEquals(HttpStatus.SC_NOT_FOUND, statusLine.getStatusCode());
                } catch (ClientProtocolException e) {
                    assertNull("Got ClientProtocolException: " + e.getLocalizedMessage(), e);
                } catch (IOException e) {
                    assertNull("Got IOException: " + e.getLocalizedMessage(), e);
                } finally {
                    httpRequestDoneSignal.countDown();
                }
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


        Log.d(TAG, "testPusherDeletedDoc() finished");

    }

    public void failingTestPullerGzipped() throws Throwable {

        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);

        String attachmentName = "attachment.png";
        addDocWithId(doc1Id, attachmentName, true);

        // workaround for https://github.com/couchbase/sync_gateway/issues/228
        Thread.sleep(1000);

        doPullReplication();

        Log.d(TAG, "Fetching doc1 via id: " + doc1Id);
        Document doc1 = database.getDocument(doc1Id);
        assertNotNull(doc1);
        assertTrue(doc1.getCurrentRevisionId().startsWith("1-"));
        assertEquals(1, doc1.getProperties().get("foo"));

        Attachment attachment = doc1.getCurrentRevision().getAttachment(attachmentName);
        assertTrue(attachment.getLength() > 0);
        assertTrue(attachment.getGZipped());
        byte[] receivedBytes = TextUtils.read(attachment.getContent());

        InputStream attachmentStream = getAsset(attachmentName);
        byte[] actualBytes = TextUtils.read(attachmentStream);
        Assert.assertEquals(actualBytes.length, receivedBytes.length);
        Assert.assertEquals(actualBytes, receivedBytes);

    }

    public void testPuller() throws Throwable {

        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        final String doc2Id = String.format("doc2-%s", docIdTimestamp);

        addDocWithId(doc1Id, "attachment.png", false);
        addDocWithId(doc2Id, "attachment2.png", false);

        // workaround for https://github.com/couchbase/sync_gateway/issues/228
        Thread.sleep(1000);

        doPullReplication();

        Log.d(TAG, "Fetching doc1 via id: " + doc1Id);
        Document doc1 = database.getDocument(doc1Id);
        assertNotNull(doc1);
        assertTrue(doc1.getCurrentRevisionId().startsWith("1-"));
        assertEquals(1, doc1.getProperties().get("foo"));

        Log.d(TAG, "Fetching doc2 via id: " + doc2Id);
                Document doc2 = database.getDocument(doc2Id);
        assertNotNull(doc2);
        assertTrue(doc2.getCurrentRevisionId().startsWith("1-"));
        assertEquals(1, doc2.getProperties().get("foo"));

        // update doc1 on sync gateway
        String docJson = String.format("{\"foo\":2,\"bar\":true,\"_rev\":\"%s\",\"_id\":\"%s\"}", doc1.getCurrentRevisionId(), doc1.getId());
        pushDocumentToSyncGateway(doc1.getId(), docJson);

        // workaround for https://github.com/couchbase/sync_gateway/issues/228
        Thread.sleep(1000);

        // do another pull
        doPullReplication();

        // make sure it has the latest properties
        Document doc1Fetched = database.getDocument(doc1Id);
        assertNotNull(doc1Fetched);
        assertTrue(doc1Fetched.getCurrentRevisionId().startsWith("2-"));
        assertEquals(2, doc1Fetched.getProperties().get("foo"));

        Log.d(TAG, "testPuller() finished");


    }

    public void testPullerWithLiveQuery() throws Throwable {

        // This is essentially a regression test for a deadlock
        // that was happening when the LiveQuery#onDatabaseChanged()
        // was calling waitForUpdateThread(), but that thread was
        // waiting on connection to be released by the thread calling
        // waitForUpdateThread().  When the deadlock bug was present,
        // this test would trigger the deadlock and never finish.

        Log.d(Database.TAG, "testPullerWithLiveQuery");
        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        final String doc2Id = String.format("doc2-%s", docIdTimestamp);

        addDocWithId(doc1Id, "attachment2.png", false);
        addDocWithId(doc2Id, "attachment2.png", false);

        final int numDocsBeforePull = database.getDocumentCount();

        View view = database.getView("testPullerWithLiveQueryView");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("_id") != null) {
                    emitter.emit(document.get("_id"), null);
                }
            }
        }, null, "1");

        LiveQuery allDocsLiveQuery = view.createQuery().toLiveQuery();
        allDocsLiveQuery.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                int numTimesCalled = 0;
                if (event.getError() != null) {
                    throw new RuntimeException(event.getError());
                }
                // the first time this is called back, the rows will be empty.
                // but on subsequent times we should expect to get a non empty
                // row set.
                if (numTimesCalled++ > 0) {

                    assertTrue(event.getRows().getCount() > numDocsBeforePull);
                }
                Log.d(Database.TAG, "rows " + event.getRows());

            }
        });

        allDocsLiveQuery.start();

        doPullReplication();

    }

    private void doPullReplication() {
        URL remote = getReplicationURL();

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        final Replication repl = (Replication) database.createPullReplication(remote);
        repl.setContinuous(false);

        runReplication(repl);

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
        }
        else {
            docJson = "{\"foo\":1,\"bar\":false}";
        }
        pushDocumentToSyncGateway(docId, docJson);


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
                    StringEntity se = new StringEntity( docJson.toString() );
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

    public void testGetReplicator() throws Throwable {

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", getReplicationURL().toExternalForm());

        Map<String,Object> headers = new HashMap<String,Object>();
        String coolieVal = "SyncGatewaySession=c38687c2696688a";
        headers.put("Cookie", coolieVal);
        properties.put("headers", headers);

        Replication replicator = manager.getReplicator(properties);
        assertNotNull(replicator);
        assertEquals(getReplicationURL().toExternalForm(), replicator.getRemoteUrl().toExternalForm());
        assertTrue(!replicator.isPull());
        assertFalse(replicator.isContinuous());
        assertFalse(replicator.isRunning());
        assertTrue(replicator.getHeaders().containsKey("Cookie"));
        assertEquals(replicator.getHeaders().get("Cookie"), coolieVal);

        // start the replicator
        replicator.start();

        // now lets lookup existing replicator and stop it
        properties.put("cancel", true);
        Replication activeReplicator = manager.getReplicator(properties);
        activeReplicator.stop();
        assertFalse(activeReplicator.isRunning());

    }

    public void testGetReplicatorWithAuth() throws Throwable {

        Map<String, Object> properties = getPushReplicationParsedJson();

        Replication replicator = manager.getReplicator(properties);
        assertNotNull(replicator);
        assertNotNull(replicator.getAuthorizer());
        assertTrue(replicator.getAuthorizer() instanceof FacebookAuthorizer);

    }

    private void runReplication(Replication replication) {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        ReplicationObserver replicationObserver = new ReplicationObserver(replicationDoneSignal);
        replication.addChangeListener(replicationObserver);

        replication.start();

        CountDownLatch replicationDoneSignalPolling = replicationWatcherThread(replication);

        Log.d(TAG, "Waiting for replicator to finish");
        try {
            boolean success = replicationDoneSignal.await(300, TimeUnit.SECONDS);
            assertTrue(success);

            success = replicationDoneSignalPolling.await(300, TimeUnit.SECONDS);
            assertTrue(success);

            Log.d(TAG, "replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    private CountDownLatch replicationWatcherThread(final Replication replication) {

        final CountDownLatch doneSignal = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean started = false;
                boolean done = false;
                while (!done) {

                    if (replication.isRunning()) {
                        started = true;
                    }
                    final boolean statusIsDone = (replication.getStatus() == Replication.ReplicationStatus.REPLICATION_STOPPED ||
                            replication.getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE);
                    if (started && statusIsDone) {
                        done = true;
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

                doneSignal.countDown();

            }
        }).start();
        return doneSignal;

    }

    public void testRunReplicationWithError() throws Exception {

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                int statusCode = 500;
                mockHttpClient.addResponderFailAllRequests(statusCode);
                return mockHttpClient;
            }
        };

        String dbUrlString = "http://fake.test-url.com:4984/fake/";
        URL remote = new URL(dbUrlString);
        final boolean continuous = false;
        Replication r1 = new Puller(database, remote, continuous, mockHttpClientFactory, manager.getWorkExecutor());
        Assert.assertFalse(r1.isContinuous());
        runReplication(r1);

        // It should have failed with a 404:
        Assert.assertEquals(Replication.ReplicationStatus.REPLICATION_STOPPED, r1.getStatus());
        Assert.assertEquals(0, r1.getCompletedChangesCount());
        Assert.assertEquals(0, r1.getChangesCount());
        Assert.assertNotNull(r1.getLastError());


    }

    public void testReplicatorErrorStatus() throws Exception {

        // register bogus fb token
        Map<String,Object> facebookTokenInfo = new HashMap<String,Object>();
        facebookTokenInfo.put("email", "jchris@couchbase.com");
        facebookTokenInfo.put("remote_url", getReplicationURL().toExternalForm());
        facebookTokenInfo.put("access_token", "fake_access_token");
        String destUrl = String.format("/_facebook_token", DEFAULT_TEST_DB);
        Map<String,Object> result = (Map<String,Object>)sendBody("POST", destUrl, facebookTokenInfo, Status.OK, null);
        Log.v(TAG, String.format("result %s", result));

        // start a replicator
        Map<String,Object> properties = getPullReplicationParsedJson();
        Replication replicator = manager.getReplicator(properties);
        replicator.start();

        boolean foundError = false;
        for (int i=0; i<10; i++) {

            // wait a few seconds
            Thread.sleep(5 * 1000);

            // expect an error since it will try to contact the sync gateway with this bogus login,
            // and the sync gateway will reject it.
            ArrayList<Object> activeTasks = (ArrayList<Object>)send("GET", "/_active_tasks", Status.OK, null);
            Log.d(TAG, "activeTasks: " + activeTasks);
            Map<String,Object> activeTaskReplication = (Map<String,Object>) activeTasks.get(0);
            foundError = (activeTaskReplication.get("error") != null);
            if (foundError == true) {
                break;
            }

        }

        assertTrue(foundError);

    }


    public void testFetchRemoteCheckpointDoc() throws Exception {

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                mockHttpClient.addResponderThrowExceptionAllRequests();
                return mockHttpClient;
            }
        };

        Log.d("TEST", "testFetchRemoteCheckpointDoc() called");
        String dbUrlString = "http://fake.test-url.com:4984/fake/";
        URL remote = new URL(dbUrlString);
        database.setLastSequence("1", remote, true);  // otherwise fetchRemoteCheckpoint won't contact remote
        Replication replicator = new Pusher(database, remote, false, mockHttpClientFactory, manager.getWorkExecutor());

        CountDownLatch doneSignal = new CountDownLatch(1);
        ReplicationObserver replicationObserver = new ReplicationObserver(doneSignal);
        replicator.addChangeListener(replicationObserver);

        replicator.fetchRemoteCheckpointDoc();

        Log.d(TAG, "testFetchRemoteCheckpointDoc() Waiting for replicator to finish");
        try {
            boolean succeeded = doneSignal.await(300, TimeUnit.SECONDS);
            assertTrue(succeeded);
            Log.d(TAG, "testFetchRemoteCheckpointDoc() replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String errorMessage = "Since we are passing in a mock http client that always throws " +
                "errors, we expect the replicator to be in an error state";
        assertNotNull(errorMessage, replicator.getLastError());

    }

    public void testGoOffline() throws Exception {

        URL remote = getReplicationURL();

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        Replication repl = database.createPullReplication(remote);
        repl.setContinuous(true);
        repl.start();

        repl.goOffline();
        Assert.assertTrue(repl.getStatus() == Replication.ReplicationStatus.REPLICATION_OFFLINE);


    }

    class ReplicationObserver implements Replication.ChangeListener {

        public boolean replicationFinished = false;
        private CountDownLatch doneSignal;

        ReplicationObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Replication replicator = event.getSource();
            if (!replicator.isRunning()) {
                replicationFinished = true;
                String msg = String.format("myobserver.update called, set replicationFinished to: %b", replicationFinished);
                Log.d(TAG, msg);
                doneSignal.countDown();
            }
            else {
                String msg = String.format("myobserver.update called, but replicator still running, so ignore it");
                Log.d(TAG, msg);
            }
        }

        boolean isReplicationFinished() {
            return replicationFinished;
        }

    }

    public void testBuildRelativeURLString() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replicator = new Pusher(null, new URL(dbUrlString), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("foo");

        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);

    }

    public void testBuildRelativeURLStringWithLeadingSlash() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replicator = new Pusher(null, new URL(dbUrlString), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("/foo");

        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);

    }

    public void testChannels() throws Exception {

        URL remote = getReplicationURL();
        Replication replicator = database.createPullReplication(remote);
        List<String> channels = new ArrayList<String>();
        channels.add("chan1");
        channels.add("chan2");
        replicator.setChannels(channels);
        Assert.assertEquals(channels, replicator.getChannels());
        replicator.setChannels(null);
        Assert.assertTrue(replicator.getChannels().isEmpty());

    }

    public void testChannelsMore() throws MalformedURLException, CouchbaseLiteException {

        Database  db = startDatabase();
        URL fakeRemoteURL = new URL("http://couchbase.com/no_such_db");
        Replication r1 = db.createPullReplication(fakeRemoteURL);

        assertTrue(r1.getChannels().isEmpty());
        r1.setFilter("foo/bar");
        assertTrue(r1.getChannels().isEmpty());
        Map<String, Object> filterParams= new HashMap<String, Object>();
        filterParams.put("a", "b");
        r1.setFilterParams(filterParams);
        assertTrue(r1.getChannels().isEmpty());

        r1.setChannels(null);
        assertEquals("foo/bar", r1.getFilter());
        assertEquals(filterParams, r1.getFilterParams());

        List<String> channels = new ArrayList<String>();
        channels.add("NBC");
        channels.add("MTV");
        r1.setChannels(channels);
        assertEquals(channels, r1.getChannels());
        assertEquals("sync_gateway/bychannel", r1.getFilter());
        filterParams= new HashMap<String, Object>();
        filterParams.put("channels", "NBC,MTV");
        assertEquals(filterParams, r1.getFilterParams());

        r1.setChannels(null);
        assertEquals(r1.getFilter(), null);
        assertEquals(null ,r1.getFilterParams());

    }


    public void testHeaders() throws Exception {

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderThrowExceptionAllRequests();

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };

        URL remote = getReplicationURL();

        manager.setDefaultHttpClientFactory(mockHttpClientFactory);
        Replication puller = database.createPullReplication(remote);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        puller.setHeaders(headers);
        puller.start();

        Thread.sleep(2000);
        puller.stop();

        boolean foundFooHeader = false;
        List<HttpRequest> requests = mockHttpClient.getCapturedRequests();
        for (HttpRequest request : requests) {
            Header[] requestHeaders = request.getHeaders("foo");
            for (Header requestHeader : requestHeaders) {
                foundFooHeader = true;
                Assert.assertEquals("bar", requestHeader.getValue());
            }
        }

        Assert.assertTrue(foundFooHeader);
        manager.setDefaultHttpClientFactory(null);

    }

    /**
     * Regression test for issue couchbase/couchbase-lite-android#174
     */
    public void testAllLeafRevisionsArePushed() throws Exception {

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderRevDiffsAllMissing();
        mockHttpClient.setResponseDelayMilliseconds(250);

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };
        manager.setDefaultHttpClientFactory(mockHttpClientFactory);

        Document doc = database.createDocument();
        SavedRevision rev1a = doc.createRevision().save();
        SavedRevision rev2a = rev1a.createRevision().save();
        SavedRevision rev3a = rev2a.createRevision().save();

        // delete the branch we've been using, then create a new one to replace it
        SavedRevision rev4a = rev3a.deleteDocument();
        SavedRevision rev2b = rev1a.createRevision().save(true);
        assertEquals(rev2b.getId(), doc.getCurrentRevisionId());

        // sync with remote DB -- should push both leaf revisions
        Replication push = database.createPushReplication(getReplicationURL());
        runReplication(push);

        // find the _revs_diff captured request and decode into json
        List<HttpRequest> captured = mockHttpClient.getCapturedRequests();
        Log.d(TAG, "captured " + captured);
        HttpRequest revsDiff = captured.get(0);
        assertTrue(revsDiff instanceof HttpPost);
        HttpPost revsDiffPost = (HttpPost) revsDiff;
        assertTrue(revsDiffPost.getURI().toString().endsWith("_revs_diff"));
        Map<String, Object> jsonMap = CustomizableMockHttpClient.getJsonMapFromRequest(revsDiffPost);

        // assert that it contains the expected revisions
        List<String> revisionIds = (List) jsonMap.get(doc.getId());
        assertEquals(2, revisionIds.size());
        assertTrue(revisionIds.contains(rev4a.getId()));
        assertTrue(revisionIds.contains(rev2b.getId()));

    }

    public void testRemoteConflictResolution() throws Exception {
        // Create a document with two conflicting edits.
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = rev1.createRevision().save();
        SavedRevision rev2b = rev1.createRevision().save(true);

        // Push the conflicts to the remote DB.
        Replication push = database.createPushReplication(getReplicationURL());
        runReplication(push);

        // Prepare a bulk docs request to resolve the conflict remotely. First, advance rev 2a.
        JSONObject rev3aBody = new JSONObject();
        rev3aBody.put("_id", doc.getId());
        rev3aBody.put("_rev", rev2a.getId());
        // Then, delete rev 2b.
        JSONObject rev3bBody = new JSONObject();
        rev3bBody.put("_id", doc.getId());
        rev3bBody.put("_rev", rev2b.getId());
        rev3bBody.put("_deleted", true);
        // Combine into one _bulk_docs request.
        JSONObject requestBody = new JSONObject();
        requestBody.put("docs", new JSONArray(Arrays.asList(rev3aBody, rev3bBody)));

        // Make the _bulk_docs request.
        HttpClient client = new DefaultHttpClient();
        String bulkDocsUrl = getReplicationURL().toExternalForm() + "/_bulk_docs";
        HttpPost request = new HttpPost(bulkDocsUrl);
        request.setHeader("Content-Type", "application/json");
        String json = requestBody.toString();
        request.setEntity(new StringEntity(json));
        HttpResponse response = client.execute(request);

        // Check the response to make sure everything worked as it should.
        assertEquals(201, response.getStatusLine().getStatusCode());
        String rawResponse = IOUtils.toString(response.getEntity().getContent());
        JSONArray resultArray = new JSONArray(rawResponse);
        assertEquals(2, resultArray.length());
        for (int i = 0; i < resultArray.length(); i++) {
            assertTrue(((JSONObject) resultArray.get(i)).isNull("error"));
        }

        // Pull the remote changes.
        Replication pull = database.createPullReplication(getReplicationURL());
        runReplication(pull);

        // Make sure the conflict was resolved locally.
        assertEquals(1, doc.getConflictingRevisions().size());
    }


}

