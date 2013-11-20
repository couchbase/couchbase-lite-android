package com.couchbase.cblite.testapp.tests;


import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLLiveQuery;
import com.couchbase.cblite.CBLLiveQueryChangedFunction;
import com.couchbase.cblite.CBLMapEmitFunction;
import com.couchbase.cblite.CBLMapFunction;
import com.couchbase.cblite.CBLQueryEnumerator;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.auth.CBLFacebookAuthorizer;
import com.couchbase.cblite.internal.CBLBody;
import com.couchbase.cblite.internal.CBLRevisionInternal;
import com.couchbase.cblite.replicator.CBLPusher;
import com.couchbase.cblite.replicator.CBLReplicator;
import com.couchbase.cblite.support.Base64;
import com.couchbase.cblite.support.HttpClientFactory;
import com.couchbase.cblite.threading.BackgroundTask;
import com.couchbase.cblite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CountDownLatch;

public class Replicator extends CBLiteTestCase {

    public static final String TAG = "Replicator";

    public String testPusher() throws Throwable {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        URL remote = getReplicationURL();
        String docIdTimestamp = Long.toString(System.currentTimeMillis());

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        documentProperties.put("_id", doc1Id);
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        CBLBody body = new CBLBody(documentProperties);
        CBLRevisionInternal rev1 = new CBLRevisionInternal(body, database);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        CBLRevisionInternal rev2 = database.putRevision(new CBLRevisionInternal(documentProperties, database), rev1.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        String doc2Id = String.format("doc2-%s", docIdTimestamp);
        documentProperties.put("_id", doc2Id);
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new CBLRevisionInternal(documentProperties, database), null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        final CBLReplicator repl = database.getReplicator(remote, true, false, manager.getWorkExecutor());
        ((CBLPusher)repl).setCreateTarget(true);

        BackgroundTask replicationTask = new BackgroundTask() {

            @Override
            public void run() {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }

        };
        replicationTask.execute();


        ReplicationObserver replicationObserver = new ReplicationObserver(replicationDoneSignal);
        repl.addObserver(replicationObserver);

        Log.d(TAG, "Waiting for replicator to finish");
        try {
            replicationDoneSignal.await();
            Log.d(TAG, "replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
                    Assert.assertTrue(statusLine.getStatusCode() == HttpStatus.SC_OK);
                    if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        response.getEntity().writeTo(out);
                        out.close();
                        responseString = out.toString();
                        Assert.assertTrue(responseString.contains(doc1Id));
                        Log.d(TAG, "result: " + responseString);

                    } else{
                        //Closes the connection.
                        response.getEntity().getContent().close();
                        throw new IOException(statusLine.getReasonPhrase());
                    }
                } catch (ClientProtocolException e) {
                    Assert.assertNull("Got ClientProtocolException: " + e.getLocalizedMessage(), e);
                } catch (IOException e) {
                    Assert.assertNull("Got IOException: " + e.getLocalizedMessage(), e);
                }

                httpRequestDoneSignal.countDown();
            }


        };
        getDocTask.execute();


        Log.d(TAG, "Waiting for http request to finish");
        try {
            httpRequestDoneSignal.await();
            Log.d(TAG, "http request finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        Log.d(TAG, "testPusher() finished");
        return docIdTimestamp;

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

        CBLBody body = new CBLBody(documentProperties);
        CBLRevisionInternal rev1 = new CBLRevisionInternal(body, database);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);
        documentProperties.put("_deleted", true);

        @SuppressWarnings("unused")
        CBLRevisionInternal rev2 = database.putRevision(new CBLRevisionInternal(documentProperties, database), rev1.getRevId(), false, status);
        Assert.assertTrue(status.getCode() >= 200 && status.getCode() < 300);

        final CBLReplicator repl = database.getReplicator(remote, true, false, manager.getWorkExecutor());
        ((CBLPusher)repl).setCreateTarget(true);

        BackgroundTask replicationTask = new BackgroundTask() {

            @Override
            public void run() {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }

        };
        replicationTask.execute();


        ReplicationObserver replicationObserver = new ReplicationObserver(replicationDoneSignal);
        repl.addObserver(replicationObserver);

        Log.d(TAG, "Waiting for replicator to finish");
        try {
            replicationDoneSignal.await();
            Log.d(TAG, "replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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

                    Assert.assertEquals(HttpStatus.SC_NOT_FOUND, statusLine.getStatusCode());
                } catch (ClientProtocolException e) {
                    Assert.assertNull("Got ClientProtocolException: " + e.getLocalizedMessage(), e);
                } catch (IOException e) {
                    Assert.assertNull("Got IOException: " + e.getLocalizedMessage(), e);
                } finally {
                    httpRequestDoneSignal.countDown();
                }
            }
        };
        getDocTask.execute();


        Log.d(TAG, "Waiting for http request to finish");
        try {
            httpRequestDoneSignal.await();
            Log.d(TAG, "http request finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        Log.d(TAG, "testPusherDeletedDoc() finished");

    }

    public void testPuller() throws Throwable {

        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        final String doc2Id = String.format("doc2-%s", docIdTimestamp);

        addDocWithId(doc1Id, "attachment.png");
        addDocWithId(doc2Id, "attachment2.png");

        doPullReplication();

        CBLRevisionInternal doc1 = database.getDocumentWithIDAndRev(doc1Id, null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc1);
        Assert.assertTrue(doc1.getRevId().startsWith("1-"));
        Assert.assertEquals(1, doc1.getProperties().get("foo"));

        CBLRevisionInternal doc2 = database.getDocumentWithIDAndRev(doc2Id, null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc2);
        Assert.assertTrue(doc2.getRevId().startsWith("1-"));
        Assert.assertEquals(1, doc2.getProperties().get("foo"));

        Log.d(TAG, "testPuller() finished");


    }

    public void testPullerWithLiveQuery() throws Throwable {

        // This is essentially a regression test for a deadlock
        // that was happening when the LiveQuery#onDatabaseChanged()
        // was calling waitForUpdateThread(), but that thread was
        // waiting on connection to be released by the thread calling
        // waitForUpdateThread().  When the deadlock bug was present,
        // this test would trigger the deadlock and never finish.

        Log.d(CBLDatabase.TAG, "testPullerWithLiveQuery");
        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        final String doc2Id = String.format("doc2-%s", docIdTimestamp);

        addDocWithId(doc1Id, "attachment2.png");
        addDocWithId(doc2Id, "attachment2.png");

        final int numDocsBeforePull = database.getDocumentCount();

        CBLView view = database.getView("testPullerWithLiveQueryView");
        view.setMapAndReduce(new CBLMapFunction() {
            @Override
            public void map(Map<String, Object> document, CBLMapEmitFunction emitter) {
                if (document.get("_id") != null) {
                    emitter.emit(document.get("_id"), null);
                }
            }
        }, null, "1");

        CBLLiveQuery allDocsLiveQuery = view.createQuery().toLiveQuery();
        allDocsLiveQuery.addChangeListener(new CBLLiveQueryChangedFunction() {
            int numTimesCalled = 0;
            @Override
            public void onLiveQueryChanged(CBLQueryEnumerator rows) {
                // the first time this is called back, the rows will be empty.
                // but on subsequent times we should expect to get a non empty
                // row set.
                if (numTimesCalled++ > 0) {
                    Assert.assertTrue(rows.getCount() > numDocsBeforePull);
                }
                Log.d(CBLDatabase.TAG, "rows " + rows);
            }

            @Override
            public void onFailureLiveQueryChanged(Throwable exception) {
                throw new RuntimeException(exception);
            }
        });
        allDocsLiveQuery.start();

        doPullReplication();

    }

    private void doPullReplication() {
        URL remote = getReplicationURL();

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        final CBLReplicator repl = database.getReplicator(remote, false, false, manager.getWorkExecutor());

        repl.start();

        ReplicationObserver replicationObserver = new ReplicationObserver(replicationDoneSignal);
        repl.addObserver(replicationObserver);

        Log.d(TAG, "Waiting for replicator to finish");
        try {
            replicationDoneSignal.await();
            Log.d(TAG, "replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void addDocWithId(String docId, String attachmentName) throws IOException {

        // add attachment to document
        InputStream attachmentStream = getInstrumentation().getContext().getAssets().open(attachmentName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(attachmentStream, baos);
        String attachmentBase64 = Base64.encodeBytes(baos.toByteArray());

        // push a document to server
        final String doc1Json = String.format("{\"foo\":1,\"bar\":false, \"_attachments\": { \"i_use_couchdb.png\": { \"content_type\": \"image/png\", \"data\": \"%s\" } } }", attachmentBase64);

        URL replicationUrlTrailingDoc1 = new URL(String.format("%s/%s", getReplicationURL().toExternalForm(), docId));
        final URL pathToDoc1 = new URL(replicationUrlTrailingDoc1, docId);
        Log.d(TAG, "Send http request to " + pathToDoc1);

        final CountDownLatch httpRequestDoneSignal = new CountDownLatch(1);
        BackgroundTask getDocTask = new BackgroundTask() {

            @Override
            public void run() {

                org.apache.http.client.HttpClient httpclient = new DefaultHttpClient();

                HttpResponse response;
                String responseString = null;
                try {
                    HttpPut post = new HttpPut(pathToDoc1.toExternalForm());
                    StringEntity se = new StringEntity( doc1Json.toString() );
                    se.setContentType(new BasicHeader("content_type", "application/json"));
                    post.setEntity(se);
                    response = httpclient.execute(post);
                    StatusLine statusLine = response.getStatusLine();
                    Log.d(TAG, "Got response: " + statusLine);
                    Assert.assertTrue(statusLine.getStatusCode() == HttpStatus.SC_CREATED);
                } catch (ClientProtocolException e) {
                    Assert.assertNull("Got ClientProtocolException: " + e.getLocalizedMessage(), e);
                } catch (IOException e) {
                    Assert.assertNull("Got IOException: " + e.getLocalizedMessage(), e);
                }

                httpRequestDoneSignal.countDown();
            }


        };
        getDocTask.execute();

        Log.d(TAG, "Waiting for http request to finish");
        try {
            httpRequestDoneSignal.await();
            Log.d(TAG, "http request finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testGetReplicator() throws Throwable {

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", getReplicationURL().toExternalForm());

        CBLReplicator replicator = manager.getReplicator(properties);
        Assert.assertNotNull(replicator);
        Assert.assertEquals(getReplicationURL().toExternalForm(), replicator.getRemoteUrl().toExternalForm());
        Assert.assertTrue(!replicator.isPull());
        Assert.assertFalse(replicator.isContinuous());
        Assert.assertFalse(replicator.isRunning());

        // start the replicator
        replicator.start();

        // now lets lookup existing replicator and stop it
        properties.put("cancel", true);
        CBLReplicator activeReplicator = manager.getReplicator(properties);
        activeReplicator.stop();
        Assert.assertFalse(activeReplicator.isRunning());

    }

    public void testGetReplicatorWithAuth() throws Throwable {

        Map<String, Object> properties = getPushReplicationParsedJson();

        CBLReplicator replicator = manager.getReplicator(properties);
        Assert.assertNotNull(replicator);
        Assert.assertNotNull(replicator.getAuthorizer());
        Assert.assertTrue(replicator.getAuthorizer() instanceof CBLFacebookAuthorizer);

    }

    public void testReplicatorErrorStatus() throws Exception {

        // register bogus fb token
        Map<String,Object> facebookTokenInfo = new HashMap<String,Object>();
        facebookTokenInfo.put("email", "jchris@couchbase.com");
        facebookTokenInfo.put("remote_url", getReplicationURL().toExternalForm());
        facebookTokenInfo.put("access_token", "fake_access_token");
        String destUrl = String.format("/_facebook_token", DEFAULT_TEST_DB);
        Map<String,Object> result = (Map<String,Object>)sendBody("POST", destUrl, facebookTokenInfo, CBLStatus.OK, null);
        Log.v(TAG, String.format("result %s", result));

        // start a replicator
        Map<String,Object> properties = getPullReplicationParsedJson();
        CBLReplicator replicator = manager.getReplicator(properties);
        replicator.start();

        boolean foundError = false;
        for (int i=0; i<10; i++) {

            // wait a few seconds
            Thread.sleep(5 * 1000);

            // expect an error since it will try to contact the sync gateway with this bogus login,
            // and the sync gateway will reject it.
            ArrayList<Object> activeTasks = (ArrayList<Object>)send("GET", "/_active_tasks", CBLStatus.OK, null);
            Log.d(TAG, "activeTasks: " + activeTasks);
            Map<String,Object> activeTaskReplication = (Map<String,Object>) activeTasks.get(0);
            foundError = (activeTaskReplication.get("error") != null);
            if (foundError == true) {
                break;
            }

        }

        Assert.assertTrue(foundError);

    }


    public void testFetchRemoteCheckpointDoc() throws Exception {

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return new MockHttpClient();
            }
        };

        Log.d("TEST", "testFetchRemoteCheckpointDoc() called");
        String dbUrlString = "http://fake.test-url.com:4984/fake/";
        URL remote = new URL(dbUrlString);
        database.setLastSequence("1", remote, true);  // otherwise fetchRemoteCheckpoint won't contact remote
        CBLReplicator replicator = new CBLPusher(database, remote, false, mockHttpClientFactory, manager.getWorkExecutor());
        replicator.fetchRemoteCheckpointDoc();

        CountDownLatch doneSignal = new CountDownLatch(1);
        ReplicationObserver replicationObserver = new ReplicationObserver(doneSignal);
        replicator.addObserver(replicationObserver);

        Log.d(TAG, "testFetchRemoteCheckpointDoc() Waiting for replicator to finish");
        try {
            doneSignal.await();
            Log.d(TAG, "testFetchRemoteCheckpointDoc() replicator finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String errorMessage = "Since we are passing in a mock http client that always throws " +
                "errors, we expect the replicator to be in an error state";
        Assert.assertNotNull(errorMessage, replicator.getError());

    }



    class ReplicationObserver implements Observer {
        public boolean replicationFinished = false;
        private CountDownLatch doneSignal;

        public ReplicationObserver(CountDownLatch doneSignal) {
            super();
            this.doneSignal = doneSignal;
        }

        @Override
        public void update(Observable observable, Object data) {
            Log.d(TAG, "ReplicationObserver.update called.  observable: " + observable);
            CBLReplicator replicator = (CBLReplicator) observable;
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

}
