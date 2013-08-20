package com.couchbase.cblite.testapp.tests;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CountDownLatch;

import junit.framework.Assert;

import android.os.AsyncTask;
import android.util.Log;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.replicator.CBLPusher;
import com.couchbase.cblite.replicator.CBLReplicator;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;

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
        CBLRevision rev1 = new CBLRevision(body);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        CBLRevision rev2 = database.putRevision(new CBLRevision(documentProperties), rev1.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        String doc2Id = String.format("doc2-%s", docIdTimestamp);
        documentProperties.put("_id", doc2Id);
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new CBLRevision(documentProperties), null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        final CBLReplicator repl = database.getReplicator(remote, true, false, server.getWorkExecutor());
        ((CBLPusher)repl).setCreateTarget(true);

        AsyncTask replicationTask = new AsyncTask<Object, Object, Object>() {

            @Override
            protected Object doInBackground(Object... aParams) {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
                return null;
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
        AsyncTask getDocTask = new AsyncTask<Object, Object, Object>() {

            @Override
            protected Object doInBackground(Object... aParams) {
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
                return null;
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

    public void testPuller() throws Throwable {


        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);

        // push a document to server
        final String json = String.format("{\"foo\":1,\"bar\":false}", doc1Id);

        URL replicationUrlTrailing = new URL(String.format("%s/%s", getReplicationURL().toExternalForm(), doc1Id));
        final URL pathToDoc = new URL(replicationUrlTrailing, doc1Id);
        Log.d(TAG, "Send http request to " + pathToDoc);

        final CountDownLatch httpRequestDoneSignal = new CountDownLatch(1);
        AsyncTask getDocTask = new AsyncTask<Object, Object, Object>() {

            @Override
            protected Object doInBackground(Object... aParams) {
                org.apache.http.client.HttpClient httpclient = new DefaultHttpClient();
                HttpResponse response;
                String responseString = null;
                try {
                    HttpPut post = new HttpPut(pathToDoc.toExternalForm());
                    StringEntity se = new StringEntity( json.toString() );
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
                return null;
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


        URL remote = getReplicationURL();

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        final CBLReplicator repl = database.getReplicator(remote, false, false, server.getWorkExecutor());
        AsyncTask replicationTask = new AsyncTask<Object, Object, Object>() {

            @Override
            protected Object doInBackground(Object... aParams) {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
                return null;
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

        CBLRevision doc = database.getDocumentWithIDAndRev(doc1Id, null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("1-"));
        Assert.assertEquals(1, doc.getProperties().get("foo"));

        Log.d(TAG, "testPuller() finished");


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
