package com.couchbase.cblite.testapp.tests;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
import com.couchbase.cblite.auth.CBLFacebookAuthorizer;
import com.couchbase.cblite.replicator.CBLPusher;
import com.couchbase.cblite.replicator.CBLReplicator;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;

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
        CBLRevision rev1 = new CBLRevision(body, database);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        CBLRevision rev2 = database.putRevision(new CBLRevision(documentProperties, database), rev1.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        String doc2Id = String.format("doc2-%s", docIdTimestamp);
        documentProperties.put("_id", doc2Id);
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new CBLRevision(documentProperties, database), null, false, status);
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

                // workaround attempt for issue #81
                // the last commit fixed the problem (using a new httpcontext object for each http client).
                // it ran the unit tests in a loop for 1 hour with no failures.
                BasicHttpParams params = new BasicHttpParams();
                SchemeRegistry schemeRegistry = new SchemeRegistry();
                schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
                final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
                schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
                ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
                org.apache.http.client.HttpClient httpclient = new DefaultHttpClient(cm, params);

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

                // workaround attempt for issue #81
                BasicHttpParams params = new BasicHttpParams();
                SchemeRegistry schemeRegistry = new SchemeRegistry();
                schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
                final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
                schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
                ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
                org.apache.http.client.HttpClient httpclient = new DefaultHttpClient(cm, params);

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

    public void testGetReplicator() throws Throwable {

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", getReplicationURL().toExternalForm());

        CBLReplicator replicator = server.getManager().getReplicator(properties);
        Assert.assertNotNull(replicator);
        Assert.assertEquals(getReplicationURL().toExternalForm(), replicator.getRemote().toExternalForm());
        Assert.assertTrue(replicator.isPush());
        Assert.assertFalse(replicator.isContinuous());
        Assert.assertFalse(replicator.isRunning());

        // start the replicator
        replicator.start();

        // now lets lookup existing replicator and stop it
        properties.put("cancel", true);
        CBLReplicator activeReplicator = server.getManager().getReplicator(properties);
        activeReplicator.stop();
        Assert.assertFalse(activeReplicator.isRunning());

    }

    public void testGetReplicatorWithAuth() throws Throwable {

        Map<String, Object> properties = getPushReplicationParsedJson();

        CBLReplicator replicator = server.getManager().getReplicator(properties);
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
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "POST", destUrl, facebookTokenInfo, CBLStatus.OK, null);
        Log.v(TAG, String.format("result %s", result));

        // start a replicator
        Map<String,Object> properties = getPullReplicationParsedJson();
        CBLReplicator replicator = server.getManager().getReplicator(properties);
        replicator.start();

        // wait a few seconds
        Thread.sleep(5 * 1000);

        // expect an error since it will try to contact the sync gateway with this bogus login,
        // and the sync gateway will reject it.
        ArrayList<Object> activeTasks = (ArrayList<Object>)send(server, "GET", "/_active_tasks", CBLStatus.OK, null);
        Log.d(TAG, "activeTasks: " + activeTasks);
        Map<String,Object> activeTaskReplication = (Map<String,Object>) activeTasks.get(0);
        Assert.assertNotNull(activeTaskReplication.get("error"));

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
