package com.couchbase.lite.replicator;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryOptions;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.Revision;
import com.couchbase.lite.SavedRevision;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.ValidationContext;
import com.couchbase.lite.Validator;
import com.couchbase.lite.View;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.auth.FacebookAuthorizer;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockBulkDocs;
import com.couchbase.lite.mockserver.MockChangesFeed;
import com.couchbase.lite.mockserver.MockChangesFeedNoResponse;
import com.couchbase.lite.mockserver.MockCheckpointGet;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockDocumentBulkGet;
import com.couchbase.lite.mockserver.MockDocumentGet;
import com.couchbase.lite.mockserver.MockDocumentPut;
import com.couchbase.lite.mockserver.MockFacebookAuthPost;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.MockRevsDiff;
import com.couchbase.lite.mockserver.MockSessionGet;
import com.couchbase.lite.mockserver.SmartMockResponse;
import com.couchbase.lite.mockserver.WrappedSmartMockResponse;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.support.RemoteRequestRetry;
import com.couchbase.lite.util.Log;
import com.couchbase.org.apache.http.entity.mime.MultipartEntity;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for the new state machine based replicator
 */
public class ReplicationTest extends LiteTestCase {

    /**
     * Continuous puller starts offline
     * Wait for a while .. (til what?)
     * Add remote document (simulate w/ mock webserver)
     * Put replication online
     * Make sure doc is pulled
     */
    public void testGoOnlinePuller() throws Exception {

        Log.d(Log.TAG, "testGoOnlinePuller");

        // create mock server
        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        server.play();

        // mock documents to be pulled
        MockDocumentGet.MockDocument mockDoc1 = new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
        mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());

        // checkpoint PUT or GET response (sticky)
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _changes response 503 error (sticky)
        WrappedSmartMockResponse wrapped2 = new WrappedSmartMockResponse(new MockResponse().setResponseCode(503));
        wrapped2.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, wrapped2);

        // doc1 response
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc1);
        dispatcher.enqueueResponse(mockDoc1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        mockRevsDiff.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        // _bulk_docs response -- everything stored
        MockBulkDocs mockBulkDocs = new MockBulkDocs();
        mockBulkDocs.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

        // create and start replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);
        pullReplication.start();
        Log.d(Log.TAG, "Started pullReplication: %s", pullReplication);

        // wait until a _checkpoint request have been sent
        dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);

        // wait until a _changes request has been sent
        dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHANGES);

        putReplicationOffline(pullReplication);

        // clear out existing queued mock responses to make room for new ones
        dispatcher.clearQueuedResponse(MockHelper.PATH_REGEX_CHANGES);

        // real _changes response with doc1
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // long poll changes feed no response
        MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
        mockChangesFeedNoResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

        putReplicationOnline(pullReplication);

        Log.d(Log.TAG, "Waiting for PUT checkpoint request with seq: %d", mockDoc1.getDocSeq());
        waitForPutCheckpointRequestWithSeq(dispatcher, mockDoc1.getDocSeq());
        Log.d(Log.TAG, "Got PUT checkpoint request with seq: %d", mockDoc1.getDocSeq());

        stopReplication(pullReplication);
        server.shutdown();


    }


    /**
     * Start continuous replication with a closed db.
     *
     * Expected behavior:
     *   - Receive replication finished callback
     *   - Replication lastError will contain an exception
     */
    public void testStartReplicationClosedDb() throws Exception {

        Database db = this.manager.getDatabase("closed");
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Replication replication = db.createPullReplication(new URL("http://fake.com/foo"));
        replication.setContinuous(true);
        replication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.d(TAG, "changed event: %s", event);
                if (replication.isRunning() == false) {
                    countDownLatch.countDown();
                }

            }
        });

        db.close();
        replication.start();

        boolean success = countDownLatch.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        assertTrue(replication.getLastError() != null);

    }

    /**
     * Start a replication and stop it immediately
     */
    public void failingTestStartReplicationStartStop() throws Exception {

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<ReplicationStateTransition> transitions = new ArrayList<ReplicationStateTransition>();
        final Replication replication = database.createPullReplication(new URL("http://fake.com/foo"));
        replication.setContinuous(true);
        replication.addChangeListener(new ReplicationFinishedObserver(countDownLatch));

        replication.start();
        replication.start();  // this should be ignored

        replication.stop();
        replication.stop();  // this should be ignored

        boolean success = countDownLatch.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        assertTrue(replication.getLastError() == null);

        assertEquals(3, transitions.size());

        assertEquals(ReplicationState.INITIAL, transitions.get(0).getSource());
        assertEquals(ReplicationState.RUNNING, transitions.get(0).getDestination());

        assertEquals(ReplicationState.RUNNING, transitions.get(1).getSource());
        assertEquals(ReplicationState.STOPPING, transitions.get(1).getDestination());

        assertEquals(ReplicationState.STOPPING, transitions.get(2).getSource());
        assertEquals(ReplicationState.STOPPED, transitions.get(2).getDestination());

    }

    /**
     * Pull replication test:
     *
     * - Single one-shot pull replication
     * - Against simulated sync gateway
     * - Remote docs do not have attachments
     */
    public void testMockSinglePullSyncGw() throws Exception {

        boolean shutdownMockWebserver = true;
        boolean addAttachments = false;

        mockSinglePull(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW, addAttachments);

    }

    /**
     * Pull replication test:
     *
     * - Single one-shot pull replication
     * - Against simulated couchdb
     * - Remote docs do not have attachments
     */
    public void testMockSinglePullCouchDb() throws Exception {

        boolean shutdownMockWebserver = true;
        boolean addAttachments = false;


        mockSinglePull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB, addAttachments);

    }

    /**
     * Pull replication test:
     *
     * - Single one-shot pull replication
     * - Against simulated couchdb
     * - Remote docs have attachments
     */
    public void testMockSinglePullCouchDbAttachments() throws Exception {

        boolean shutdownMockWebserver = true;
        boolean addAttachments = true;


        mockSinglePull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB, addAttachments);

    }

    /**
     * Pull replication test:
     *
     * - Single one-shot pull replication
     * - Against simulated sync gateway
     * - Remote docs have attachments
     *
     * TODO: sporadic assertion failure when checking rev field of PUT checkpoint requests
     *
     */
    public void testMockSinglePullSyncGwAttachments() throws Exception {

        boolean shutdownMockWebserver = true;
        boolean addAttachments = true;

        mockSinglePull(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW, addAttachments);

    }

    public void testMockMultiplePullSyncGw() throws Exception {

        boolean shutdownMockWebserver = true;

        mockMultiplePull(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW);

    }

    public void testMockMultiplePullCouchDb() throws Exception {

        boolean shutdownMockWebserver = true;

        mockMultiplePull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB);

    }

    public void testMockContinuousPullCouchDb() throws Exception {
        boolean shutdownMockWebserver = true;
        mockContinuousPull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB);
    }



    /**
     * Do a pull replication
     *
     * @param shutdownMockWebserver - should this test shutdown the mockwebserver
     *                              when done?  if another test wants to pick up
     *                              where this left off, you should pass false.
     * @param serverType - should the mock return the Sync Gateway server type in
     *                   the "Server" HTTP Header?  this changes the behavior of the
     *                   replicator to use bulk_get and POST reqeusts for _changes feeds.
     * @param addAttachments - should the mock sync gateway return docs with attachments?
     * @return a map that contains the mockwebserver (key="server") and the mock dispatcher
     *         (key="dispatcher")
     */
    public Map<String, Object> mockSinglePull(boolean shutdownMockWebserver, MockDispatcher.ServerType serverType, boolean addAttachments) throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(serverType);

        // mock documents to be pulled
        MockDocumentGet.MockDocument mockDoc1 = new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
        mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());
        mockDoc1.setAttachmentName("attachment.png");
        MockDocumentGet.MockDocument mockDoc2 = new MockDocumentGet.MockDocument("doc2", "1-563b", 2);
        mockDoc2.setJsonMap(MockHelper.generateRandomJsonMap());
        mockDoc2.setAttachmentName("attachment2.png");

        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc2));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // doc1 response
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc1);
        if (addAttachments) {
            mockDocumentGet.addAttachmentFilename(mockDoc1.getAttachmentName());
        }
        dispatcher.enqueueResponse(mockDoc1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // doc2 response
        mockDocumentGet = new MockDocumentGet(mockDoc2);
        if (addAttachments) {
            mockDocumentGet.addAttachmentFilename(mockDoc2.getAttachmentName());
        }
        dispatcher.enqueueResponse(mockDoc2.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // _bulk_get response
        MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
        mockBulkGet.addDocument(mockDoc1);
        mockBulkGet.addDocument(mockDoc2);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

        // respond to all PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(500);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        pullReplication.setHeaders(headers);
        String checkpointId = pullReplication.remoteCheckpointDocID();
        runReplication(pullReplication);
        Log.d(TAG, "pullReplication finished");

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                List<DocumentChange> changes = event.getChanges();
                for (DocumentChange documentChange : changes) {
                    Log.d(TAG, "doc change callback: %s", documentChange.getDocumentId());
                }
            }
        });


        // assert that we now have both docs in local db
        assertNotNull(database);
        Document doc1 = database.getDocument(mockDoc1.getDocId());
        assertNotNull(doc1);
        assertNotNull(doc1.getCurrentRevisionId());
        assertTrue(doc1.getCurrentRevisionId().equals(mockDoc1.getDocRev()));
        assertNotNull(doc1.getProperties());
        assertEquals(mockDoc1.getJsonMap(), doc1.getUserProperties());
        Document doc2 = database.getDocument(mockDoc2.getDocId());
        assertNotNull(doc2);
        assertNotNull(doc2.getCurrentRevisionId());
        assertNotNull(doc2.getProperties());
        assertTrue(doc2.getCurrentRevisionId().equals(mockDoc2.getDocRev()));
        assertEquals(mockDoc2.getJsonMap(), doc2.getUserProperties());

        // assert that docs have attachments (if applicable)
        if (addAttachments) {
            attachmentAsserts(mockDoc1.getAttachmentName(), doc1);
            attachmentAsserts(mockDoc2.getAttachmentName(), doc2);
        }

        // make assertions about outgoing requests from replicator -> mock
        RecordedRequest getCheckpointRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
        assertNotNull(getCheckpointRequest);
        assertEquals("bar", getCheckpointRequest.getHeader("foo"));
        assertTrue(getCheckpointRequest.getMethod().equals("GET"));
        assertTrue(getCheckpointRequest.getPath().matches(MockHelper.PATH_REGEX_CHECKPOINT));
        RecordedRequest getChangesFeedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
        if (serverType == MockDispatcher.ServerType.SYNC_GW) {
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));
        } else {
            assertTrue(getChangesFeedRequest.getMethod().equals("GET"));
        }
        assertTrue(getChangesFeedRequest.getPath().matches(MockHelper.PATH_REGEX_CHANGES));

        // wait until the mock webserver receives a PUT checkpoint request with doc #2's sequence
        Log.d(TAG, "waiting for PUT checkpoint %s", mockDoc2.getDocSeq());
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, mockDoc2.getDocSeq());
        validateCheckpointRequestsRevisions(checkpointRequests);
        Log.d(TAG, "got PUT checkpoint %s", mockDoc2.getDocSeq());

        // assert our local sequence matches what is expected
        String lastSequence = database.lastSequenceWithCheckpointId(checkpointId);
        assertEquals(Integer.toString(mockDoc2.getDocSeq()), lastSequence);

        // assert completed count makes sense
        assertEquals(pullReplication.getChangesCount(), pullReplication.getCompletedChangesCount());

        // allow for either a single _bulk_get request or individual doc requests.
        // if the server is sync gateway, it is allowable for replicator to use _bulk_get
        RecordedRequest bulkGetRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_GET);
        if (bulkGetRequest != null) {
            String bulkGetBody = bulkGetRequest.getUtf8Body();
            assertTrue(bulkGetBody.contains(mockDoc1.getDocId()));
            assertTrue(bulkGetBody.contains(mockDoc2.getDocId()));
        } else {
            RecordedRequest doc1Request = dispatcher.takeRequest(mockDoc1.getDocPathRegex());
            assertTrue(doc1Request.getMethod().equals("GET"));
            assertTrue(doc1Request.getPath().matches(mockDoc1.getDocPathRegex()));
            RecordedRequest doc2Request = dispatcher.takeRequest(mockDoc2.getDocPathRegex());
            assertTrue(doc2Request.getMethod().equals("GET"));
            assertTrue(doc2Request.getPath().matches(mockDoc2.getDocPathRegex()));
        }

        // Shut down the server. Instances cannot be reused.
        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);

        return returnVal;

    }

    /**
     *
     * Simulate the following:
     *
     * - Add a few docs and do a pull replication
     * - One doc on sync gateway is now updated
     * - Do a second pull replication
     * - Assert we get the updated doc and save it locally
     *
     */
    public Map<String, Object> mockMultiplePull(boolean shutdownMockWebserver, MockDispatcher.ServerType serverType) throws Exception {

        String doc1Id = "doc1";

        // create mockwebserver and custom dispatcher
        boolean addAttachments = false;

        // do a pull replication
        Map<String, Object> serverAndDispatcher = mockSinglePull(false, serverType, addAttachments);

        MockWebServer server = (MockWebServer) serverAndDispatcher.get("server");
        MockDispatcher dispatcher = (MockDispatcher) serverAndDispatcher.get("dispatcher");

        // clear out any possible residue left from previous test, eg, mock responses queued up as
        // any recorded requests that have been logged.
        dispatcher.reset();

        String doc1Rev = "2-2e38";
        int doc1Seq = 3;
        String checkpointRev = "0-1";
        String checkpointLastSequence = "2";

        // checkpoint GET response w/ seq = 2
        MockCheckpointGet mockCheckpointGet = new MockCheckpointGet();
        mockCheckpointGet.setOk("true");
        mockCheckpointGet.setRev(checkpointRev);
        mockCheckpointGet.setLastSequence(checkpointLastSequence);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointGet);

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        MockChangesFeed.MockChangedDoc mockChangedDoc1 = new MockChangesFeed.MockChangedDoc()
                .setSeq(doc1Seq)
                .setDocId(doc1Id)
                .setChangedRevIds(Arrays.asList(doc1Rev));
        mockChangesFeed.add(mockChangedDoc1);
        MockResponse fakeChangesResponse = mockChangesFeed.generateMockResponse();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, fakeChangesResponse);

        // doc1 response
        Map<String, Object> doc1JsonMap = MockHelper.generateRandomJsonMap();
        MockDocumentGet mockDocumentGet = new MockDocumentGet()
                .setDocId(doc1Id)
                .setRev(doc1Rev)
                .setJsonMap(doc1JsonMap);
        String doc1PathRegex = "/db/doc1.*";
        dispatcher.enqueueResponse(doc1PathRegex, mockDocumentGet.generateMockResponse());

        // checkpoint PUT response
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointGet.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        runReplication(pullReplication);

        // assert that we now have both docs in local db
        assertNotNull(database);
        Document doc1 = database.getDocument(doc1Id);
        assertNotNull(doc1);
        assertNotNull(doc1.getCurrentRevisionId());
        assertTrue(doc1.getCurrentRevisionId().startsWith("2-"));
        assertEquals(doc1JsonMap, doc1.getUserProperties());

        // make assertions about outgoing requests from replicator -> mock
        RecordedRequest getCheckpointRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
        assertNotNull(getCheckpointRequest);
        assertTrue(getCheckpointRequest.getMethod().equals("GET"));
        assertTrue(getCheckpointRequest.getPath().matches(MockHelper.PATH_REGEX_CHECKPOINT));
        RecordedRequest getChangesFeedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);

        if (serverType == MockDispatcher.ServerType.SYNC_GW) {
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));

        } else {
            assertTrue(getChangesFeedRequest.getMethod().equals("GET"));
        }
        assertTrue(getChangesFeedRequest.getPath().matches(MockHelper.PATH_REGEX_CHANGES));
        if (serverType == MockDispatcher.ServerType.SYNC_GW) {
            Map <String, Object> jsonMap = Manager.getObjectMapper().readValue(getChangesFeedRequest.getUtf8Body(), Map.class);
            assertTrue(jsonMap.containsKey("since"));
            Integer since = (Integer) jsonMap.get("since");
            assertEquals(2, since.intValue());
        }

        RecordedRequest doc1Request = dispatcher.takeRequest(doc1PathRegex);
        assertTrue(doc1Request.getMethod().equals("GET"));
        assertTrue(doc1Request.getPath().matches("/db/doc1\\?rev=2-2e38.*"));

        // wait until the mock webserver receives a PUT checkpoint request with doc #2's sequence
        int expectedLastSequence = doc1Seq;
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, expectedLastSequence);
        assertEquals(1, checkpointRequests.size());

        // assert our local sequence matches what is expected
        String lastSequence = database.lastSequenceWithCheckpointId(pullReplication.remoteCheckpointDocID());
        assertEquals(Integer.toString(expectedLastSequence), lastSequence);

        // assert completed count makes sense
        assertEquals(pullReplication.getChangesCount(), pullReplication.getCompletedChangesCount());

        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);

        return returnVal;

    }

    public Map<String, Object> mockContinuousPull(boolean shutdownMockWebserver, MockDispatcher.ServerType serverType) throws Exception {

        assertTrue(serverType == MockDispatcher.ServerType.COUCHDB);

        final int numMockRemoteDocs = 20;  // must be multiple of 10!
        final AtomicInteger numDocsPulledLocally = new AtomicInteger(0);

        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(serverType);
        int numDocsPerChangesResponse = numMockRemoteDocs / 10;
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockRemoteDocs, numDocsPerChangesResponse);

        server.play();

        final CountDownLatch receivedAllDocs = new CountDownLatch(1);

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);

        final CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        pullReplication.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));

        final CountDownLatch replicationIdleSignal = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdleSignal);
        pullReplication.addChangeListener(idleObserver);

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                List<DocumentChange> changes = event.getChanges();
                for (DocumentChange change : changes) {
                    numDocsPulledLocally.addAndGet(1);
                }
                if (numDocsPulledLocally.get() == numMockRemoteDocs) {
                    receivedAllDocs.countDown();
                }
            }
        });

        pullReplication.start();

        // wait until we received all mock docs or timeout occurs
        boolean success = receivedAllDocs.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        // make sure all docs in local db
        Map<String, Object> allDocs = database.getAllDocs(new QueryOptions());
        Integer totalRows = (Integer) allDocs.get("total_rows");
        List rows = (List) allDocs.get("rows");
        assertEquals(numMockRemoteDocs, totalRows.intValue());
        assertEquals(numMockRemoteDocs, rows.size());

        // wait until idle
        success = replicationIdleSignal.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        // cleanup / shutdown
        pullReplication.stop();

        success = replicationDoneSignal.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        // wait until the mock webserver receives a PUT checkpoint request with last do's sequence,
        // this avoids ugly and confusing exceptions in the logs.
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, numMockRemoteDocs - 1);
        validateCheckpointRequestsRevisions(checkpointRequests);

        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);
        return returnVal;

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/257
     *
     * - Create local document with attachment
     * - Start continuous pull replication
     * - MockServer returns _changes with new rev of document
     * - MockServer returns doc multipart response: https://gist.github.com/tleyden/bf36f688d0b5086372fd
     * - Delete doc cache (not sure if needed)
     * - Fetch doc fresh from database
     * - Verify that it still has attachments
     *
     */
    public void testAttachmentsDeletedOnPull() throws Exception {

        String doc1Id = "doc1";
        int doc1Rev2Generation = 2;
        String doc1Rev2Digest = "b";
        String doc1Rev2 = String.format("%d-%s", doc1Rev2Generation, doc1Rev2Digest);
        int doc1Seq1 = 1;
        String doc1AttachName = "attachment.png";
        String contentType = "image/png";

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        server.play();

        // add some documents - verify it has an attachment
        Document doc1 = createDocumentForPushReplication(doc1Id, doc1AttachName, contentType);
        String doc1Rev1 = doc1.getCurrentRevisionId();
        database.clearDocumentCache();
        doc1 = database.getDocument(doc1.getId());
        assertTrue(doc1.getCurrentRevision().getAttachments().size() > 0);

        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        // checkpoint PUT response
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // add response to 1st _changes request
        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(
                doc1Id, doc1Rev2, doc1Seq1);
        Map<String, Object> newProperties = new HashMap<String, Object>(doc1.getProperties());
        newProperties.put("_rev", doc1Rev2);
        mockDocument1.setJsonMap(newProperties);
        mockDocument1.setAttachmentName(doc1AttachName);

        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument1));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // add sticky _changes response to feed=longpoll that just blocks for 60 seconds to emulate
        // server that doesn't have any new changes
        MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
        mockChangesFeedNoResponse.setDelayMs(60 * 1000);
        mockChangesFeedNoResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

        // add response to doc get
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDocument1);
        mockDocumentGet.addAttachmentFilename(mockDocument1.getAttachmentName());
        mockDocumentGet.setIncludeAttachmentPart(false);
        Map<String, Object> revHistory = new HashMap<String, Object>();
        revHistory.put("start", doc1Rev2Generation);
        List ids = Arrays.asList(
                RevisionInternal.digestFromRevID(doc1Rev2),
                RevisionInternal.digestFromRevID(doc1Rev1)
        );
        revHistory.put("ids",ids);
        mockDocumentGet.setRevHistoryMap(revHistory);
        dispatcher.enqueueResponse(mockDocument1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // create and start pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);
        pullReplication.start();

        // wait for the next PUT checkpoint request/response
        waitForPutCheckpointRequestWithSeq(dispatcher, 1);

        stopReplication(pullReplication);

        // clear doc cache
        database.clearDocumentCache();

        // make sure doc has attachments
        Document doc1Fetched = database.getDocument(doc1.getId());
        assertTrue(doc1Fetched.getCurrentRevision().getAttachments().size() > 0);

        server.shutdown();


    }

    /**
     * This is essentially a regression test for a deadlock
     * that was happening when the LiveQuery#onDatabaseChanged()
     * was calling waitForUpdateThread(), but that thread was
     * waiting on connection to be released by the thread calling
     * waitForUpdateThread().  When the deadlock bug was present,
     * this test would trigger the deadlock and never finish.
     *
     * TODO: sporadic assertion failure when checking rev field of PUT checkpoint requests
     */
    public void testPullerWithLiveQuery() throws Throwable {

        View view = database.getView("testPullerWithLiveQueryView");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("_id") != null) {
                    emitter.emit(document.get("_id"), null);
                }
            }
        }, null, "1");

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        LiveQuery allDocsLiveQuery = view.createQuery().toLiveQuery();
        allDocsLiveQuery.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                int numTimesCalled = 0;
                if (event.getError() != null) {
                    throw new RuntimeException(event.getError());
                }
                if (event.getRows().getCount() == 2) {
                    countDownLatch.countDown();
                }
            }
        });

        // kick off live query
        allDocsLiveQuery.start();

        // do pull replication against mock
        mockSinglePull(true, MockDispatcher.ServerType.SYNC_GW, true);

        // make sure we were called back with both docs
        boolean success = countDownLatch.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        // clean up
        allDocsLiveQuery.stop();

    }


    /**
     * Make sure that if a continuous push gets an error
     * pushing a doc, it will keep retrying it rather than giving up right away.
     *
     * @throws Exception
     */
    public void testContinuousPushRetryBehavior() throws Exception {

        RemoteRequestRetry.RETRY_DELAY_MS = 5;       // speed up test execution (inner loop retry delay)

        ReplicationInternal.RETRY_DELAY_SECONDS = 1; // speed up test execution (outer loop retry delay)
        ReplicationInternal.MAX_RETRIES = 3;         // spped up test execution (outer loop retry count)

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(500);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
         mockRevsDiff.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        // _bulk_docs response -- 503 errors
        MockResponse mockResponse = new MockResponse().setResponseCode(503);
        WrappedSmartMockResponse mockBulkDocs = new WrappedSmartMockResponse(mockResponse, false);
        mockBulkDocs.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

        server.play();

        // create replication
        Replication replication = database.createPushReplication(server.getUrl("/db"));
        replication.setContinuous(true);
        CountDownLatch replicationIdle = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdle);
        replication.addChangeListener(idleObserver);
        replication.start();

        // wait until idle
        boolean success = replicationIdle.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        replication.removeChangeListener(idleObserver);

        // create a doc in local db
        Document doc1 = createDocumentForPushReplication("doc1", null, null);

        // we should expect to at least see numAttempts attempts at doing POST to _bulk_docs
        // 1st attempt
        // numAttempts are number of times retry in 1 attempt.
        int numAttempts = RemoteRequestRetry.MAX_RETRIES + 1; // total number of attempts = 4 (1 initial + MAX_RETRIES)
        for (int i=0; i < numAttempts; i++) {
            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(request);
            dispatcher.takeRecordedResponseBlocking(request);
        }

        // By 12/16/2014, CBL core java tries RemoteRequestRetry.MAX_RETRIES + 1 see above.
        // Without fixing #299, following code should cause hang.

        // outer retry loop
        for(int j = 0; j < ReplicationInternal.MAX_RETRIES; j++){
            // inner retry loop
            for (int i=0; i < numAttempts; i++) {
                RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNotNull(request);
                dispatcher.takeRecordedResponseBlocking(request);
            }
        }
        // gave up replication!!!

        stopReplication(replication);
        server.shutdown();
    }


    public void testMockSinglePush() throws Exception {

        boolean shutdownMockWebserver = true;

        mockSinglePush(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW);

    }


    /**
     * Do a push replication
     *
     * - Create docs in local db
     *   - One with no attachment
     *   - One with small attachment
     *   - One with large attachment
     *
     */

    public Map<String, Object> mockSinglePush(boolean shutdownMockWebserver, MockDispatcher.ServerType serverType) throws Exception {

        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";
        String doc4Id = "doc4";
        String doc2PathRegex = String.format("/db/%s.*", doc2Id);
        String doc3PathRegex = String.format("/db/%s.*", doc3Id);
        String doc2AttachName = "attachment.png";
        String doc3AttachName = "attachment2.png";
        String contentType = "image/png";

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(serverType);
        server.play();

        // add some documents
        Document doc1 = createDocumentForPushReplication(doc1Id, null, null);
        Document doc2 = createDocumentForPushReplication(doc2Id, doc2AttachName, contentType);
        Document doc3 = createDocumentForPushReplication(doc3Id, doc3AttachName, contentType);
        Document doc4 = createDocumentForPushReplication(doc4Id, null, null);
        doc4.delete();

        // checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(50);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        // _bulk_docs response -- everything stored
        MockBulkDocs mockBulkDocs = new MockBulkDocs();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

        // doc PUT responses for docs with attachments
        MockDocumentPut mockDoc2Put = new MockDocumentPut()
                .setDocId(doc2Id)
                .setRev(doc2.getCurrentRevisionId());
        dispatcher.enqueueResponse(doc2PathRegex, mockDoc2Put.generateMockResponse());
        MockDocumentPut mockDoc3Put = new MockDocumentPut()
                .setDocId(doc3Id)
                .setRev(doc3.getCurrentRevisionId());
        dispatcher.enqueueResponse(doc3PathRegex, mockDoc3Put.generateMockResponse());

        // run replication
        Replication replication = database.createPushReplication(server.getUrl("/db"));
        replication.setContinuous(false);
        if (serverType != MockDispatcher.ServerType.SYNC_GW) {
            replication.setCreateTarget(true);
            Assert.assertTrue(replication.shouldCreateTarget());
        }
        runReplication(replication);

        // make assertions about outgoing requests from replicator -> mock
        RecordedRequest getCheckpointRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
        assertTrue(getCheckpointRequest.getMethod().equals("GET"));
        assertTrue(getCheckpointRequest.getPath().matches(MockHelper.PATH_REGEX_CHECKPOINT));
        RecordedRequest revsDiffRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_REVS_DIFF);
        assertTrue(revsDiffRequest.getUtf8Body().contains(doc1Id));
        RecordedRequest bulkDocsRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
        assertTrue(bulkDocsRequest.getUtf8Body().contains(doc1Id));
        Map <String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(bulkDocsRequest.getUtf8Body(), Map.class);
        Map <String, Object> doc4Map = MockBulkDocs.findDocById(bulkDocsJson, doc4Id);
        assertTrue(((Boolean)doc4Map.get("_deleted")).booleanValue() == true);

        assertFalse(bulkDocsRequest.getUtf8Body().contains(doc2Id));
        RecordedRequest doc2putRequest = dispatcher.takeRequest(doc2PathRegex);
        assertTrue(doc2putRequest.getUtf8Body().contains(doc2Id));
        assertFalse(doc2putRequest.getUtf8Body().contains(doc3Id));
        RecordedRequest doc3putRequest = dispatcher.takeRequest(doc3PathRegex);
        assertTrue(doc3putRequest.getUtf8Body().contains(doc3Id));
        assertFalse(doc3putRequest.getUtf8Body().contains(doc2Id));

        // wait until the mock webserver receives a PUT checkpoint request

        int expectedLastSequence = 5;
        Log.d(TAG, "waiting for put checkpoint with lastSequence: %d", expectedLastSequence);
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, expectedLastSequence);
        Log.d(TAG, "done waiting for put checkpoint with lastSequence: %d", expectedLastSequence);
        validateCheckpointRequestsRevisions(checkpointRequests);

        // assert our local sequence matches what is expected
        String lastSequence = database.lastSequenceWithCheckpointId(replication.remoteCheckpointDocID());
        assertEquals(Integer.toString(expectedLastSequence), lastSequence);

        // assert completed count makes sense
        assertEquals(replication.getChangesCount(), replication.getCompletedChangesCount());

        // Shut down the server. Instances cannot be reused.
        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);

        return returnVal;

    }

    private void workAroundSaveCheckpointRaceCondition() throws InterruptedException {

        // sleep a bit to give it a chance to save checkpoint to db
        Thread.sleep(500);

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/55
     */
    public void testContinuousPushReplicationGoesIdle() throws Exception {

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // add docs
        Map<String,Object> properties1 = new HashMap<String,Object>();
        properties1.put("doc1", "testContinuousPushReplicationGoesIdle");
        final Document doc1 = createDocWithProperties(properties1);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        server.play();

        // checkpoint GET response w/ 404.  also receives checkpoint PUT's
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        // _bulk_docs response -- everything stored
        MockBulkDocs mockBulkDocs = new MockBulkDocs();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

        // replication to do initial sync up - has to be continuous replication so the checkpoint id
        // matches the next continuous replication we're gonna do later.

        Replication firstPusher = database.createPushReplication(server.getUrl("/db"));
        firstPusher.setContinuous(true);
        final String checkpointId = firstPusher.remoteCheckpointDocID();  // save the checkpoint id for later usage

        // start the continuous replication
        CountDownLatch replicationIdleSignal = new CountDownLatch(1);
        ReplicationIdleObserver replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
        firstPusher.addChangeListener(replicationIdleObserver);
        firstPusher.start();

        // wait until we get an IDLE event
        boolean successful = replicationIdleSignal.await(30, TimeUnit.SECONDS);
        assertTrue(successful);

        stopReplication(firstPusher);

        // wait until replication does PUT checkpoint with lastSequence=1
        int expectedLastSequence = 1;
        waitForPutCheckpointRequestWithSeq(dispatcher, expectedLastSequence);

        // the last sequence should be "1" at this point.  we will use this later
        final String lastSequence = database.lastSequenceWithCheckpointId(checkpointId);
        assertEquals("1", lastSequence);

        // start a second continuous replication
        Replication secondPusher = database.createPushReplication(server.getUrl("/db"));
        secondPusher.setContinuous(true);
        final String secondPusherCheckpointId = secondPusher.remoteCheckpointDocID();
        assertEquals(checkpointId, secondPusherCheckpointId);

        // remove current handler for the GET/PUT checkpoint request, and
        // install a new handler that returns the lastSequence from previous replication
        dispatcher.clearQueuedResponse(MockHelper.PATH_REGEX_CHECKPOINT);
        MockCheckpointGet mockCheckpointGet = new MockCheckpointGet();
        mockCheckpointGet.setLastSequence(lastSequence);
        mockCheckpointGet.setRev("0-2");
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointGet);

        // start second replication
        replicationIdleSignal = new CountDownLatch(1);
        replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
        secondPusher.addChangeListener(replicationIdleObserver);
        secondPusher.start();

        // wait until we get an IDLE event
        successful = replicationIdleSignal.await(30, TimeUnit.SECONDS);
        assertTrue(successful);
        stopReplication(secondPusher);

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/241
     *
     * - Set the "retry time" to a short number
     * - Setup mock server to return 404 for all _changes requests
     * - Start continuous replication
     * - Sleep for 5X retry time
     * - Assert that we've received at least two requests to _changes feed
     * - Stop replication + cleanup
     *
     */
    public void testContinuousReplication404Changes() throws Exception {

        int previous = PullerInternal.CHANGE_TRACKER_RESTART_DELAY_MS;
        PullerInternal.CHANGE_TRACKER_RESTART_DELAY_MS = 5;

        try {
            // create mockwebserver and custom dispatcher
            MockDispatcher dispatcher = new MockDispatcher();
            MockWebServer server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.play();

            // mock checkpoint GET response w/ 404
            MockResponse fakeCheckpointResponse = new MockResponse();
            MockHelper.set404NotFoundJson(fakeCheckpointResponse);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

            // mock _changes response
            for (int i=0; i<100; i++) {
                MockResponse mockChangesFeed = new MockResponse();
                MockHelper.set404NotFoundJson(mockChangesFeed);
                dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed);
            }

            // create new replication
            int retryDelaySeconds = 1;
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);

            // add done listener to replication
            CountDownLatch replicationDoneSignal = new CountDownLatch(1);
            ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
            pull.addChangeListener(replicationFinishedObserver);

            // start the replication
            pull.start();

            // wait until we get a few requests
            Log.d(TAG, "Waiting for a _changes request");
            RecordedRequest changesReq = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHANGES);
            Log.d(TAG, "Got first _changes request, waiting for another _changes request");
            changesReq = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHANGES);
            Log.d(TAG, "Got second _changes request, waiting for another _changes request");
            changesReq = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHANGES);
            Log.d(TAG, "Got third _changes request, stopping replicator");

            // the replication should still be running
            assertEquals(1, replicationDoneSignal.getCount());

            // cleanup
            stopReplication(pull);

            server.shutdown();

        } finally {
            PullerInternal.CHANGE_TRACKER_RESTART_DELAY_MS = previous;
        }


    }

    /**
     * Regression test for issue couchbase/couchbase-lite-android#174
     */
    public void testAllLeafRevisionsArePushed() throws Exception {

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderRevDiffsAllMissing();
        mockHttpClient.setResponseDelayMilliseconds(250);
        mockHttpClient.addResponderFakeLocalDocumentUpdate404();

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }

            @Override
            public void addCookies(List<Cookie> cookies) {

            }

            @Override
            public void deleteCookie(String name) {

            }

            @Override
            public CookieStore getCookieStore() {
                return null;
            }
        };
        manager.setDefaultHttpClientFactory(mockHttpClientFactory);

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
        List<HttpRequest> captured = mockHttpClient.getCapturedRequests();
        for (HttpRequest httpRequest : captured) {

            if (httpRequest instanceof HttpPost) {
                HttpPost httpPost = (HttpPost) httpRequest;
                if (httpPost.getURI().toString().endsWith("_revs_diff")) {
                    foundRevsDiff = true;
                    Map<String, Object> jsonMap = CustomizableMockHttpClient.getJsonMapFromRequest(httpPost);

                    // assert that it contains the expected revisions
                    List<String> revisionIds = (List) jsonMap.get(doc.getId());
                    assertEquals(2, revisionIds.size());
                    assertTrue(revisionIds.contains(rev4a.getId()));
                    assertTrue(revisionIds.contains(rev2b.getId()));
                }
            }
        }
        assertTrue(foundRevsDiff);


    }


    /**
     * Verify that when a conflict is resolved on (mock) Sync Gateway
     * and a pull replication is done, the conflict is resolved locally.
     *
     * - Create local docs in conflict
     * - Simulate sync gw responses that resolve the conflict
     * - Do pull replication
     * - Assert conflict is resolved locally
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/77
     *
     */
    public void testRemoteConflictResolution() throws Exception {

        // Create a document with two conflicting edits.
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);

        // make sure we can query the db to get the conflict
        Query allDocsQuery = database.createAllDocumentsQuery();
        allDocsQuery.setAllDocsMode(Query.AllDocsMode.ONLY_CONFLICTS);
        QueryEnumerator rows = allDocsQuery.run();
        boolean foundDoc = false;
        assertEquals(1, rows.getCount());
        for (Iterator<QueryRow> it = rows; it.hasNext();) {
            QueryRow row = it.next();
            if (row.getDocument().getId().equals(doc.getId())) {
                foundDoc = true;
            }
        }
        assertTrue(foundDoc);

        // make sure doc in conflict
        assertTrue(doc.getConflictingRevisions().size() > 1);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);

        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        int rev3PromotedGeneration = 3;
        String rev3PromotedDigest = "d46b";
        String rev3Promoted = String.format("%d-%s", rev3PromotedGeneration, rev3PromotedDigest);

        int rev3DeletedGeneration = 3;
        String rev3DeletedDigest = "e768";
        String rev3Deleted = String.format("%d-%s", rev3DeletedGeneration, rev3DeletedDigest);

        int seq = 4;

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        MockChangesFeed.MockChangedDoc mockChangedDoc = new MockChangesFeed.MockChangedDoc();
        mockChangedDoc.setDocId(doc.getId());
        mockChangedDoc.setSeq(seq);
        mockChangedDoc.setChangedRevIds(Arrays.asList(rev3Promoted, rev3Deleted));
        mockChangesFeed.add(mockChangedDoc);
        MockResponse response = mockChangesFeed.generateMockResponse();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, response);

        // docRev3Promoted response
        MockDocumentGet.MockDocument docRev3Promoted = new MockDocumentGet.MockDocument(doc.getId(), rev3Promoted, seq);
        docRev3Promoted.setJsonMap(MockHelper.generateRandomJsonMap());
        MockDocumentGet mockDocRev3PromotedGet = new MockDocumentGet(docRev3Promoted);
        Map<String, Object> rev3PromotedRevHistory = new HashMap<String, Object>();
        rev3PromotedRevHistory.put("start", rev3PromotedGeneration);
        List ids = Arrays.asList(
                rev3PromotedDigest,
                RevisionInternal.digestFromRevID(rev2a.getId()),
                RevisionInternal.digestFromRevID(rev2b.getId())
        );
        rev3PromotedRevHistory.put("ids", ids);
        mockDocRev3PromotedGet.setRevHistoryMap(rev3PromotedRevHistory);
        dispatcher.enqueueResponse(docRev3Promoted.getDocPathRegex(), mockDocRev3PromotedGet.generateMockResponse());

        // docRev3Deleted response
        MockDocumentGet.MockDocument docRev3Deleted = new MockDocumentGet.MockDocument(doc.getId(), rev3Deleted, seq);
        Map<String, Object> jsonMap = MockHelper.generateRandomJsonMap();
        jsonMap.put("_deleted", true);
        docRev3Deleted.setJsonMap(jsonMap);
        MockDocumentGet mockDocRev3DeletedGet = new MockDocumentGet(docRev3Deleted);
        Map<String, Object> rev3DeletedRevHistory = new HashMap<String, Object>();
        rev3DeletedRevHistory.put("start", rev3DeletedGeneration);
        ids = Arrays.asList(
                rev3DeletedDigest,
                RevisionInternal.digestFromRevID(rev2b.getId()),
                RevisionInternal.digestFromRevID(rev1.getId())
        );
        rev3DeletedRevHistory.put("ids", ids);
        mockDocRev3DeletedGet.setRevHistoryMap(rev3DeletedRevHistory);
        dispatcher.enqueueResponse(docRev3Deleted.getDocPathRegex(), mockDocRev3DeletedGet.generateMockResponse());

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        runReplication(pullReplication);
        assertNull(pullReplication.getLastError());

        // assertions about outgoing requests
        RecordedRequest changesRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
        assertNotNull(changesRequest);
        RecordedRequest docRev3DeletedRequest = dispatcher.takeRequest(docRev3Deleted.getDocPathRegex());
        assertNotNull(docRev3DeletedRequest);
        RecordedRequest docRev3PromotedRequest = dispatcher.takeRequest(docRev3Promoted.getDocPathRegex());
        assertNotNull(docRev3PromotedRequest);

        // Make sure the conflict was resolved locally.
        assertEquals(1, doc.getConflictingRevisions().size());

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/95
     */
    public void testPushReplicationCanMissDocs() throws Exception {

        assertEquals(0, database.getLastSequenceNumber());

        Map<String,Object> properties1 = new HashMap<String,Object>();
        properties1.put("doc1", "testPushReplicationCanMissDocs");
        final Document doc1 = createDocWithProperties(properties1);

        Map<String,Object> properties2 = new HashMap<String,Object>();
        properties1.put("doc2", "testPushReplicationCanMissDocs");
        final Document doc2 = createDocWithProperties(properties2);

        UnsavedRevision doc2UnsavedRev = doc2.createRevision();
        InputStream attachmentStream = getAsset("attachment.png");
        doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderFakeLocalDocumentUpdate404();
        mockHttpClient.setResponder("_bulk_docs", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                String json = "{\"error\":\"not_found\",\"reason\":\"missing\"}";
                return CustomizableMockHttpClient.generateHttpResponseObject(404, "NOT FOUND", json);
            }
        });

        mockHttpClient.setResponder(doc2.getId(), new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                Map<String, Object> responseObject = new HashMap<String, Object>();
                responseObject.put("id", doc2.getId());
                responseObject.put("ok", true);
                responseObject.put("rev", doc2.getCurrentRevisionId());
                return CustomizableMockHttpClient.generateHttpResponseObject(responseObject);
            }
        });

        // create a replication obeserver to wait until replication finishes
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);

        // create replication and add observer
        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(getReplicationURL());
        pusher.addChangeListener(replicationFinishedObserver);

        // save the checkpoint id for later usage
        String checkpointId = pusher.remoteCheckpointDocID();

        // kick off the replication
        pusher.start();

        // wait for it to finish
        boolean success = replicationDoneSignal.await(60, TimeUnit.SECONDS);
        assertTrue(success);
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

        Map<String,Object> properties1 = new HashMap<String,Object>();
        properties1.put("dynamic", 1);
        final Document doc = createDocWithProperties(properties1);
        SavedRevision doc1Rev = doc.getCurrentRevision();

        // Add attachment to document
        UnsavedRevision doc2UnsavedRev = doc.createRevision();
        InputStream attachmentStream = getAsset("attachment.png");
        doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();

        mockHttpClient.addResponderFakeLocalDocumentUpdate404();

        // http://url/db/foo (foo==docid)
        mockHttpClient.setResponder(doc.getId(), new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                Map<String, Object> responseObject = new HashMap<String, Object>();
                responseObject.put("id", doc.getId());
                responseObject.put("ok", true);
                responseObject.put("rev", doc.getCurrentRevisionId());
                return CustomizableMockHttpClient.generateHttpResponseObject(responseObject);
            }
        });

        // create replication and add observer
        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(getReplicationURL());

        runReplication(pusher);

        List<HttpRequest> captured = mockHttpClient.getCapturedRequests();
        for (HttpRequest httpRequest : captured) {
            // verify that there are no PUT requests with attachments
            if (httpRequest instanceof HttpPut) {
                HttpPut httpPut = (HttpPut) httpRequest;
                HttpEntity entity=httpPut.getEntity();
                //assertFalse("PUT request with updated doc properties contains attachment", entity instanceof MultipartEntity);
            }
        }

        mockHttpClient.clearCapturedRequests();

        Document oldDoc =database.getDocument(doc.getId());
        UnsavedRevision aUnsavedRev = oldDoc.createRevision();
        Map<String,Object> prop = new HashMap<String,Object>();
        prop.putAll(oldDoc.getProperties());
        prop.put("dynamic", (Integer) oldDoc.getProperty("dynamic") +1);
        aUnsavedRev.setProperties(prop);
        final SavedRevision savedRev=aUnsavedRev.save();

        mockHttpClient.setResponder(doc.getId(), new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                Map<String, Object> responseObject = new HashMap<String, Object>();
                responseObject.put("id", doc.getId());
                responseObject.put("ok", true);
                responseObject.put("rev", savedRev.getId());
                return CustomizableMockHttpClient.generateHttpResponseObject(responseObject);
            }
        });

        final String json = String.format("{\"%s\":{\"missing\":[\"%s\"],\"possible_ancestors\":[\"%s\",\"%s\"]}}",doc.getId(),savedRev.getId(),doc1Rev.getId(), doc2Rev.getId());
        mockHttpClient.setResponder("_revs_diff", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                return mockHttpClient.generateHttpResponseObject(json);
            }
        });

        pusher = database.createPushReplication(getReplicationURL());
        runReplication(pusher);


        captured = mockHttpClient.getCapturedRequests();
        for (HttpRequest httpRequest : captured) {
            // verify that there are no PUT requests with attachments
            if (httpRequest instanceof HttpPut) {
                HttpPut httpPut = (HttpPut) httpRequest;
                HttpEntity entity=httpPut.getEntity();
                assertFalse("PUT request with updated doc properties contains attachment", entity instanceof MultipartEntity);
            }
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/188
     */
    public void testServerDoesNotSupportMultipart() throws Exception {

        assertEquals(0, database.getLastSequenceNumber());

        Map<String,Object> properties1 = new HashMap<String,Object>();
        properties1.put("dynamic", 1);
        final Document doc = createDocWithProperties(properties1);
        SavedRevision doc1Rev = doc.getCurrentRevision();

        // Add attachment to document
        UnsavedRevision doc2UnsavedRev = doc.createRevision();
        InputStream attachmentStream = getAsset("attachment.png");
        doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();

        mockHttpClient.addResponderFakeLocalDocumentUpdate404();

        Queue<CustomizableMockHttpClient.Responder> responders = new LinkedList<CustomizableMockHttpClient.Responder>();

        //first http://url/db/foo (foo==docid)
        //Reject multipart PUT with response code 415
        responders.add(new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                String json = "{\"error\":\"Unsupported Media Type\",\"reason\":\"missing\"}";
                return CustomizableMockHttpClient.generateHttpResponseObject(415, "Unsupported Media Type", json);
            }
        });

        // second http://url/db/foo (foo==docid)
        // second call should be plain json, return good response
        responders.add(new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                Map<String, Object> responseObject = new HashMap<String, Object>();
                responseObject.put("id", doc.getId());
                responseObject.put("ok", true);
                responseObject.put("rev", doc.getCurrentRevisionId());
                return CustomizableMockHttpClient.generateHttpResponseObject(responseObject);
            }
        });

        ResponderChain responderChain = new ResponderChain(responders);
        mockHttpClient.setResponder(doc.getId(), responderChain);

        // create replication and add observer
        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(getReplicationURL());

        runReplication(pusher);

        List<HttpRequest> captured = mockHttpClient.getCapturedRequests();
        int entityIndex =0;
        for (HttpRequest httpRequest : captured) {
            // verify that there are no PUT requests with attachments
            if (httpRequest instanceof HttpPut) {
                HttpPut httpPut = (HttpPut) httpRequest;
                HttpEntity entity=httpPut.getEntity();
                if(entityIndex++ == 0) {
                    assertTrue("PUT request with attachment is not multipart", entity instanceof MultipartEntity);
                } else {
                    assertFalse("PUT request with attachment is multipart", entity instanceof MultipartEntity);
                }
            }
        }
    }


    public void testServerIsSyncGatewayVersion() {
        Replication pusher = database.createPushReplication(getReplicationURL());
        assertFalse(pusher.serverIsSyncGatewayVersion("0.01"));
        pusher.setServerType("Couchbase Sync Gateway/0.93");
        assertTrue(pusher.serverIsSyncGatewayVersion("0.92"));
        assertFalse(pusher.serverIsSyncGatewayVersion("0.94"));
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/243
     */
    public void testDifferentCheckpointsFilteredReplication() throws Exception {

        Replication pullerNoFilter = database.createPullReplication(getReplicationURL());
        String noFilterCheckpointDocId = pullerNoFilter.remoteCheckpointDocID();

        Replication pullerWithFilter1 = database.createPullReplication(getReplicationURL());
        pullerWithFilter1.setFilter("foo/bar");
        Map<String, Object> filterParams= new HashMap<String, Object>();
        filterParams.put("a", "aval");
        filterParams.put("b", "bval");
        List<String> docIds = Arrays.asList("doc3", "doc1", "doc2");
        pullerWithFilter1.setDocIds(docIds);
        assertEquals(docIds, pullerWithFilter1.getDocIds());
        pullerWithFilter1.setFilterParams(filterParams);

        String withFilterCheckpointDocId = pullerWithFilter1.remoteCheckpointDocID();
        assertFalse(withFilterCheckpointDocId.equals(noFilterCheckpointDocId));

        Replication pullerWithFilter2 = database.createPullReplication(getReplicationURL());
        pullerWithFilter2.setFilter("foo/bar");
        filterParams= new HashMap<String, Object>();
        filterParams.put("b", "bval");
        filterParams.put("a", "aval");
        pullerWithFilter2.setDocIds(Arrays.asList("doc2", "doc3", "doc1"));
        pullerWithFilter2.setFilterParams(filterParams);

        String withFilterCheckpointDocId2 = pullerWithFilter2.remoteCheckpointDocID();
        assertTrue(withFilterCheckpointDocId.equals(withFilterCheckpointDocId2));


    }

    public void testSetReplicationCookie() throws Exception {

        URL replicationUrl = getReplicationURL();
        Replication puller = database.createPullReplication(replicationUrl);
        String cookieName = "foo";
        String cookieVal = "bar";
        boolean isSecure = false;
        boolean httpOnly = false;

        // expiration date - 1 day from now
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int numDaysToAdd = 1;
        cal.add(Calendar.DATE, numDaysToAdd);
        Date expirationDate = cal.getTime();

        // set the cookie
        puller.setCookie(cookieName, cookieVal, "", expirationDate, isSecure, httpOnly);

        // make sure it made it into cookie store and has expected params
        CookieStore cookieStore = puller.getClientFactory().getCookieStore();
        List<Cookie> cookies = cookieStore.getCookies();
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        assertEquals(cookieName, cookie.getName());
        assertEquals(cookieVal, cookie.getValue());
        assertEquals(replicationUrl.getHost(), cookie.getDomain());
        assertEquals(replicationUrl.getPath(), cookie.getPath());
        assertEquals(expirationDate, cookie.getExpiryDate());
        assertEquals(isSecure, cookie.isSecure());

        // add a second cookie
        String cookieName2 = "foo2";
        puller.setCookie(cookieName2, cookieVal, "", expirationDate, isSecure, false);
        assertEquals(2, cookieStore.getCookies().size());

        // delete cookie
        puller.deleteCookie(cookieName2);

        // should only have the original cookie left
        assertEquals(1, cookieStore.getCookies().size());
        assertEquals(cookieName, cookieStore.getCookies().get(0).getName());


    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/376
     *
     * This test aims to demonstrate that when the changes feed returns purged documents the
     * replicator is able to fetch all other documents but unable to finish the replication
     * (STOPPED OR IDLE STATE)
     */
    public void testChangesFeedWithPurgedDoc() throws Exception {
        //generate documents ids
        String doc1Id = "doc1-" + System.currentTimeMillis();
        String doc2Id = "doc2-" + System.currentTimeMillis();
        String doc3Id = "doc3-" + System.currentTimeMillis();

        //generate mock documents
        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(
                doc1Id, "1-a", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(
                doc2Id, "1-b", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(
                doc3Id, "1-c", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);

        //add response to _local request
        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        //add response to _changes request
        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument1));
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument2));
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument3));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // doc1 response
        MockDocumentGet mockDocumentGet1 = new MockDocumentGet(mockDocument1);
        dispatcher.enqueueResponse(mockDocument1.getDocPathRegex(), mockDocumentGet1.generateMockResponse());

        // doc2 missing reponse
        MockResponse missingDocumentMockResponse = new MockResponse();
        MockHelper.set404NotFoundJson(missingDocumentMockResponse);
        dispatcher.enqueueResponse(mockDocument2.getDocPathRegex(), missingDocumentMockResponse);

        // doc3 response
        MockDocumentGet mockDocumentGet3 = new MockDocumentGet(mockDocument3);
        dispatcher.enqueueResponse(mockDocument3.getDocPathRegex(), mockDocumentGet3.generateMockResponse());

        // checkpoint PUT response
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // start mock server
        server.play();

        //create url for replication
        URL baseUrl = server.getUrl("/db");

        //create replication
        Replication pullReplication = database.createPullReplication(baseUrl);
        pullReplication.setContinuous(false);

        //add change listener to notify when the replication is finished
        CountDownLatch replicationFinishedContCountDownLatch = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver =
                new ReplicationFinishedObserver(replicationFinishedContCountDownLatch);
        pullReplication.addChangeListener(replicationFinishedObserver);

        //start replication
        pullReplication.start();

        boolean success = replicationFinishedContCountDownLatch.await(100, TimeUnit.SECONDS);
        assertTrue(success);

        if (pullReplication.getLastError() != null) {
            Log.d(TAG, "Replication had error: " + ((HttpResponseException) pullReplication.getLastError()).getStatusCode());
        }

        //assert document 1 was correctly pulled
        Document doc1 = database.getDocument(doc1Id);
        assertNotNull(doc1);
        assertNotNull(doc1.getCurrentRevision());

        //assert it was impossible to pull doc2
        Document doc2 = database.getDocument(doc2Id);
        assertNotNull(doc2);
        assertNull(doc2.getCurrentRevision());

        //assert it was possible to pull doc3
        Document doc3 = database.getDocument(doc3Id);
        assertNotNull(doc3);
        assertNotNull(doc3.getCurrentRevision());

        // wait until the replicator PUT's checkpoint with mockDocument3's sequence
        waitForPutCheckpointRequestWithSeq(dispatcher, mockDocument3.getDocSeq());

        //last saved seq must be equal to last pulled document seq
        String doc3Seq = Integer.toString(mockDocument3.getDocSeq());
        String lastSequence = database.lastSequenceWithCheckpointId(pullReplication.remoteCheckpointDocID());
        assertEquals(doc3Seq, lastSequence);

        //stop mock server
        server.shutdown();

    }

    /**
     * Reproduces https://github.com/couchbase/couchbase-lite-android/issues/167
     */
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
        mockHttpClient.addResponderFakeLocalDocumentUpdate404();

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                return mockHttpClient;
            }

            @Override
            public void addCookies(List<Cookie> cookies) {

            }

            @Override
            public void deleteCookie(String name) {

            }

            @Override
            public CookieStore getCookieStore() {
                return null;
            }
        };

        URL remote = getReplicationURL();

        manager.setDefaultHttpClientFactory(mockHttpClientFactory);
        Replication pusher = database.createPushReplication(remote);
        pusher.setContinuous(true);

        final CountDownLatch replicationCaughtUpSignal = new CountDownLatch(1);

        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                final int changesCount = event.getSource().getChangesCount();
                final int completedChangesCount = event.getSource().getCompletedChangesCount();
                String msg = String.format("changes: %d completed changes: %d", changesCount, completedChangesCount);
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
        doc.purge();

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
            for (int i=0; i < numDocsToSend; i++) {
                Map<String,Object> properties = new HashMap<String, Object>();
                properties.put("testPusherBatching", i);
                createDocumentWithProperties(database, properties);
            }

            // kick off a one time push replication to a mock
            final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
            mockHttpClient.addResponderFakeLocalDocumentUpdate404();
            HttpClientFactory mockHttpClientFactory = mockFactoryFactory(mockHttpClient);
            URL remote = getReplicationURL();

            manager.setDefaultHttpClientFactory(mockHttpClientFactory);
            Replication pusher = database.createPushReplication(remote);
            runReplication(pusher);
            assertNull(pusher.getLastError());

            int numDocsSent = 0;

            // verify that only INBOX_SIZE documents are included in any given bulk post request
            List<HttpRequest> capturedRequests = mockHttpClient.getCapturedRequests();
            for (HttpRequest capturedRequest : capturedRequests) {
                if (capturedRequest instanceof HttpPost) {
                    HttpPost capturedPostRequest = (HttpPost) capturedRequest;
                    if (capturedPostRequest.getURI().getPath().endsWith("_bulk_docs")) {
                        ArrayList docs = CustomizableMockHttpClient.extractDocsFromBulkDocsPost(capturedRequest);
                        String msg = "# of bulk docs pushed should be <= INBOX_CAPACITY";
                        assertTrue(msg, docs.size() <= ReplicationInternal.INBOX_CAPACITY);
                        numDocsSent += docs.size();
                    }
                }
            }

            assertEquals(numDocsToSend, numDocsSent);

        } finally {
            ReplicationInternal.INBOX_CAPACITY = previous;
        }

    }

    public void failingTestPullerGzipped() throws Throwable {

        // TODO: rewrite w/ MockWebserver
        /*String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);

        String attachmentName = "attachment.png";
        addDocWithId(doc1Id, attachmentName, true);

        doPullReplication();

        Log.d(TAG, "Fetching doc1 via id: " + doc1Id);
        Document doc1 = database.getDocument(doc1Id);
        assertNotNull(doc1);
        assertTrue(doc1.getCurrentRevisionId().startsWith("1-"));
        assertEquals(1, doc1.getProperties().get("foo"));

        Attachment attachment = doc1.getCurrentRevision().getAttachment(attachmentName);
        assertTrue(attachment.getLength() > 0);
        assertTrue(attachment.getGZipped());

        InputStream is = attachment.getContent();
        byte[] receivedBytes = TextUtils.read(is);
        is.close();

        InputStream attachmentStream = getAsset(attachmentName);
        byte[] actualBytes = TextUtils.read(attachmentStream);
        Assert.assertEquals(actualBytes.length, receivedBytes.length);
        Assert.assertEquals(actualBytes, receivedBytes);*/

    }

    /**
     * Verify that validation blocks are called correctly for docs
     * pulled from the sync gateway.
     *
     * - Add doc to (mock) sync gateway
     * - Add validation function that will reject that doc
     * - Do a pull replication
     * - Assert that the doc does _not_ make it into the db
     *
     */
    public void testValidationBlockCalled() throws Throwable {

        final MockDocumentGet.MockDocument mockDocument = new MockDocumentGet.MockDocument("doc1", "1-3e28", 1);
        mockDocument.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);


        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // doc response
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDocument);
        dispatcher.enqueueResponse(mockDocument.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // checkpoint PUT response
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, new MockCheckpointPut());

        // start mock server
        server.play();

        // Add Validation block
        database.setValidation("testValidationBlockCalled", new Validator() {
            @Override
            public void validate(Revision newRevision, ValidationContext context) {
                if (newRevision.getDocument().getId().equals(mockDocument.getDocId())) {
                    context.reject("Reject");
                }
            }
        });

        // run pull replication

        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        runReplication(pullReplication);

        waitForPutCheckpointRequestWithSeq(dispatcher, mockDocument.getDocSeq());

        // assert doc is not in local db
        Document doc = database.getDocument(mockDocument.getDocId());
        assertNull(doc.getCurrentRevision());  // doc should have been rejected by validation, and therefore not present

        server.shutdown();


    }


    /**
     * Attempting to reproduce couchtalk issue:
     *
     * https://github.com/couchbase/couchbase-lite-android/issues/312
     *
     * - Start continuous puller against mock SG w/ 50 docs
     * - After every 10 docs received, restart replication
     * - Make sure all 50 docs are received and stored in local db
     *
     * @throws Exception
     */
    public void testMockPullerRestart() throws Exception {

        final int numMockRemoteDocs = 20;  // must be multiple of 10!
        final AtomicInteger numDocsPulledLocally = new AtomicInteger(0);

        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        int numDocsPerChangesResponse = numMockRemoteDocs / 10;
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockRemoteDocs, numDocsPerChangesResponse);

        server.play();

        final CountDownLatch receivedAllDocs = new CountDownLatch(1);

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);

        // it should go idle twice, hence countdown latch = 2
        final CountDownLatch replicationIdleFirstTime = new CountDownLatch(1);
        final CountDownLatch replicationIdleSecondTime = new CountDownLatch(2);

        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getTransition() != null && event.getTransition().getDestination() == ReplicationState.IDLE) {
                    replicationIdleFirstTime.countDown();
                    replicationIdleSecondTime.countDown();
                }
            }
        });

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                List<DocumentChange> changes = event.getChanges();
                for (DocumentChange change : changes) {
                    numDocsPulledLocally.addAndGet(1);
                }
                if (numDocsPulledLocally.get() == numMockRemoteDocs) {
                    receivedAllDocs.countDown();
                }
            }
        });

        pullReplication.start();

        // wait until we received all mock docs or timeout occurs
        boolean success = receivedAllDocs.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        // wait until replication goes idle
        success = replicationIdleFirstTime.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        pullReplication.restart();

        // wait until replication goes idle again
        success = replicationIdleSecondTime.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        stopReplication(pullReplication);

        // cleanup / shutdown
        server.shutdown();


    }


    public void testRunReplicationWithError() throws Exception {

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                int statusCode = 406;
                mockHttpClient.addResponderFailAllRequests(statusCode);
                return mockHttpClient;
            }

            @Override
            public void addCookies(List<Cookie> cookies) {

            }

            @Override
            public void deleteCookie(String name) {

            }

            @Override
            public CookieStore getCookieStore() {
                return null;
            }
        };

        manager.setDefaultHttpClientFactory(mockHttpClientFactory);

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

    public void testBuildRelativeURLString() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replication = database.createPullReplication(new URL(dbUrlString));
        String relativeUrlString = replication.buildRelativeURLString("foo");

        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);

    }

    public void testBuildRelativeURLStringWithLeadingSlash() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replication = database.createPullReplication(new URL(dbUrlString));

        String relativeUrlString = replication.buildRelativeURLString("/foo");

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


    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void testPushReplicationRecoverableError() throws Exception {
        boolean expectReplicatorError = false;
        runPushReplicationWithTransientError("HTTP/1.1 503 Service Unavailable", expectReplicatorError);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void testPushReplicationNonRecoverableError() throws Exception {
        boolean expectReplicatorError = true;
        runPushReplicationWithTransientError("HTTP/1.1 404 Not Found", expectReplicatorError);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void runPushReplicationWithTransientError(String status, boolean expectReplicatorError) throws Exception {

        String doc1Id = "doc1";

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        server.play();

        // add some documents
        Document doc1 = createDocumentForPushReplication(doc1Id, null, null);

        // checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(50);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        // 1st _bulk_docs response -- transient error
        MockResponse response = new MockResponse().setStatus(status);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, response);

        // 2nd _bulk_docs response -- everything stored
        MockBulkDocs mockBulkDocs = new MockBulkDocs();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

        // run replication
        Replication pusher = database.createPushReplication(server.getUrl("/db"));
        pusher.setContinuous(false);
        runReplication(pusher);

        if (expectReplicatorError == true) {
            assertNotNull(pusher.getLastError());
        } else {
            assertNull(pusher.getLastError());
        }

        if (expectReplicatorError == false ) {

            int expectedLastSequence = 1;
            Log.d(TAG, "waiting for put checkpoint with lastSequence: %d", expectedLastSequence);
            List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, expectedLastSequence);
            Log.d(TAG, "done waiting for put checkpoint with lastSequence: %d", expectedLastSequence);
            validateCheckpointRequestsRevisions(checkpointRequests);

            // assert our local sequence matches what is expected
            String lastSequence = database.lastSequenceWithCheckpointId(pusher.remoteCheckpointDocID());
            assertEquals(Integer.toString(expectedLastSequence), lastSequence);

            // assert completed count makes sense
            assertEquals(pusher.getChangesCount(), pusher.getCompletedChangesCount());

        }


        // Shut down the server. Instances cannot be reused.
        server.shutdown();



    }


    /**
     * Verify that running a one-shot push replication will complete when run against a
     * mock server that throws io exceptions on every request.
     */
    public void testOneShotReplicationErrorNotification() throws Throwable {

        int previous = RemoteRequestRetry.RETRY_DELAY_MS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;

        try {
            final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
            mockHttpClient.addResponderThrowExceptionAllRequests();

            URL remote = getReplicationURL();

            manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
            Replication pusher = database.createPushReplication(remote);

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
            final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
            mockHttpClient.addResponderThrowExceptionAllRequests();

            URL remote = getReplicationURL();

            manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
            Replication pusher = database.createPushReplication(remote);
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

            boolean success = countDownLatch.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            stopReplication(pusher);
        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = previous;
        }

    }

    /**
     * Test for the goOffline() method.
     */
    public void testGoOffline() throws Exception {

        final int numMockDocsToServe = 2;

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.play();

        // mock documents to be pulled
        MockDocumentGet.MockDocument mockDoc1 = new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
        mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());
        mockDoc1.setAttachmentName("attachment.png");
        MockDocumentGet.MockDocument mockDoc2 = new MockDocumentGet.MockDocument("doc2", "1-563b", 2);
        mockDoc2.setJsonMap(MockHelper.generateRandomJsonMap());
        mockDoc2.setAttachmentName("attachment2.png");

        // fake checkpoint PUT and GET response w/ 404
        MockCheckpointPut fakeCheckpointResponse = new MockCheckpointPut();
        fakeCheckpointResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        // _changes response with docs
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // next _changes response will block (eg, longpoll reponse with no changes to return)
        MockChangesFeed mockChangesFeedEmpty = new MockChangesFeed();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedEmpty.generateMockResponse());

        // doc1 response
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc1);
        dispatcher.enqueueResponse(mockDoc1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // doc2 response
        mockDocumentGet = new MockDocumentGet(mockDoc2);
        dispatcher.enqueueResponse(mockDoc2.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // create replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);

        // add a change listener
        final CountDownLatch idleCountdownLatch = new CountDownLatch(1);
        final CountDownLatch receivedAllDocs = new CountDownLatch(1);
        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getTransition() != null && event.getTransition().getDestination() == ReplicationState.IDLE) {
                    idleCountdownLatch.countDown();
                }
                if (event.getCompletedChangeCount() == numMockDocsToServe) {
                    receivedAllDocs.countDown();
                }
            }
        });

        // start replication
        pullReplication.start();

        // wait until it goes into idle state
        boolean success = idleCountdownLatch.await(120, TimeUnit.SECONDS);
        assertTrue(success);

        // put the replication offline
        putReplicationOffline(pullReplication);

        // at this point, we shouldn't have received all of the docs yet.
        assertTrue(receivedAllDocs.getCount() > 0);

        // return some more docs on _changes feed
        MockChangesFeed mockChangesFeed2 = new MockChangesFeed();
        mockChangesFeed2.add(new MockChangesFeed.MockChangedDoc(mockDoc2));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed2.generateMockResponse());

        // put the replication online (should see the new docs)
        putReplicationOnline(pullReplication);

        // wait until we receive all the docs
        success = receivedAllDocs.await(120, TimeUnit.SECONDS);
        assertTrue(success);

        // wait until we try to PUT a checkpoint request with doc2's sequence
        waitForPutCheckpointRequestWithSeq(dispatcher, mockDoc2.getDocSeq());

        // make sure all docs in local db
        Map<String, Object> allDocs = database.getAllDocs(new QueryOptions());
        Integer totalRows = (Integer) allDocs.get("total_rows");
        List rows = (List) allDocs.get("rows");
        assertEquals(numMockDocsToServe, totalRows.intValue());
        assertEquals(numMockDocsToServe, rows.size());

        // cleanup
        stopReplication(pullReplication);
        server.shutdown();

    }

    private void putReplicationOffline(Replication replication) throws InterruptedException {

        Log.d(Log.TAG, "putReplicationOffline: %s", replication);


        // this was a useless test, the replication wasn't even started
        final CountDownLatch wentOffline = new CountDownLatch(1);
        Replication.ChangeListener changeListener = new ReplicationOfflineObserver(wentOffline);
        replication.addChangeListener(changeListener);

        replication.goOffline();
        boolean succeeded = wentOffline.await(30, TimeUnit.SECONDS);
        assertTrue(succeeded);

        replication.removeChangeListener(changeListener);

        Log.d(Log.TAG, "/ putReplicationOffline: %s", replication);


    }


    private void putReplicationOnline(Replication replication) throws InterruptedException {

        Log.d(Log.TAG, "putReplicationOnline: %s", replication);


        // this was a useless test, the replication wasn't even started
        final CountDownLatch wentOnline = new CountDownLatch(1);
        Replication.ChangeListener changeListener = new ReplicationActiveObserver(wentOnline);
        replication.addChangeListener(changeListener);

        replication.goOnline();

        boolean succeeded = wentOnline.await(30, TimeUnit.SECONDS);
        assertTrue(succeeded);

        replication.removeChangeListener(changeListener);

        Log.d(Log.TAG, "/putReplicationOnline: %s", replication);

    }


    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/253
     */
    public void testReplicationOnlineExtraneousChangeTrackers() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);

        // add sticky checkpoint GET response w/ 404
        MockCheckpointGet fakeCheckpointResponse = new MockCheckpointGet();
        fakeCheckpointResponse.set404(true);
        fakeCheckpointResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);


        // add sticky _changes response to feed=longpoll that just blocks for 60 seconds to emulate
        // server that doesn't have any new changes
        MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
        mockChangesFeedNoResponse.setDelayMs(60 * 1000);
        mockChangesFeedNoResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES_LONGPOLL, mockChangesFeedNoResponse);

        // add _changes response to feed=normal that returns empty _changes feed immediately
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        MockResponse mockResponse = mockChangesFeed.generateMockResponse();
        for (int i=0; i<500; i++) {  // TODO: use setSticky instead of workaround to add a ton of mock responses
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES_NORMAL, new WrappedSmartMockResponse(mockResponse));
        }

        // start mock server
        server.play();

        //create url for replication
        URL baseUrl = server.getUrl("/db");

        //create replication
        final Replication pullReplication = database.createPullReplication(baseUrl);
        pullReplication.setContinuous(true);
        pullReplication.start();

        // wait until we get a request to the _changes feed
        RecordedRequest changesReq = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHANGES_LONGPOLL);
        assertNotNull(changesReq);

        putReplicationOffline(pullReplication);

        // at this point since we called takeRequest earlier, our recorded _changes request queue should be empty
        assertNull(dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES_LONGPOLL));

        // put replication online 10 times
        for (int i = 0; i < 10; i++) {
            pullReplication.goOnline();
        }

        // sleep for a while to give things a chance to start
        Log.d(TAG, "sleeping for 2 seconds");
        Thread.sleep(2 * 1000);
        Log.d(TAG, "done sleeping");

        // how many _changes feed requests has the replicator made since going online?
        int numChangesRequests = 0;
        while ((changesReq = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES_LONGPOLL)) != null) {
            Log.d(TAG, "changesReq: %s", changesReq);
            numChangesRequests += 1;
        }

        // assert that there was only one _changes feed request
        assertEquals(1, numChangesRequests);

        // shutdown
        stopReplication(pullReplication);
        server.shutdown();


    }

    /**
     * Test goOffline() method in the context of a continuous pusher.
     *
     * - Kick off continuous push replication
     * - Add a local document
     * - Wait for document to be pushed
     * - Call goOffline()
     * - Add a 2nd local document
     * - Call goOnline()
     * - Wait for 2nd document to be pushed
     *
     * @throws Exception
     */
    public void testGoOfflinePusher() throws Exception {

        int previous = RemoteRequestRetry.RETRY_DELAY_MS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;

        try {
            // create local docs
            Map<String,Object> properties = new HashMap<String, Object>();
            properties.put("testGoOfflinePusher", "1");
            Document doc1 = createDocumentWithProperties(database, properties);

            // create mock server
            MockDispatcher dispatcher = new MockDispatcher();
            MockWebServer server = new MockWebServer();
            server.setDispatcher(dispatcher);
            server.play();

            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // _revs_diff response -- everything missing
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            mockRevsDiff.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // _bulk_docs response -- everything stored
            MockBulkDocs mockBulkDocs = new MockBulkDocs();
            mockBulkDocs.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            // create and start push replication
            Replication replicator = database.createPushReplication(server.getUrl("/db"));
            replicator.setContinuous(true);
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            ReplicationIdleObserver replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
            replicator.addChangeListener(replicationIdleObserver);
            replicator.start();

            // wait until replication goes idle
            boolean successful = replicationIdleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(successful);

            // wait until mock server gets the checkpoint PUT request
            boolean foundCheckpointPut = false;
            String expectedLastSequence = "1";
            while (!foundCheckpointPut) {
                RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
                if (request.getMethod().equals("PUT")) {
                    foundCheckpointPut = true;
                    Assert.assertTrue(request.getUtf8Body().indexOf(expectedLastSequence) != -1);
                    // wait until mock server responds to the checkpoint PUT request
                    dispatcher.takeRecordedResponseBlocking(request);
                }
            }

            putReplicationOffline(replicator);

            // during this time, any requests to server will fail, because we
            // are simulating being offline.  (whether or not the pusher should
            // even be _sending_ requests during this time is a different story)
            dispatcher.clearQueuedResponse(MockHelper.PATH_REGEX_REVS_DIFF);
            dispatcher.clearRecordedRequests(MockHelper.PATH_REGEX_REVS_DIFF);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, new SmartMockResponse() {
                @Override
                public MockResponse generateMockResponse(RecordedRequest request) {
                    return new MockResponse().setResponseCode(500);
                }

                @Override
                public boolean isSticky() {
                    return true;
                }

                @Override
                public long delayMs() {
                    return 0;
                }
            });

            // add a 2nd doc to local db
            properties = new HashMap<String, Object>();
            properties.put("testGoOfflinePusher", "2");
            Document doc2 = createDocumentWithProperties(database, properties);

            // currently, even when offline, adding a new doc will cause it to try pushing the
            // doc.  (this is questionable behavior, need to check against iOS).  It will retry
            // twice, so lets wait for two requests to /_revs_diff
            RecordedRequest revsDiffRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_REVS_DIFF);
            dispatcher.takeRecordedResponseBlocking(revsDiffRequest);
            revsDiffRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_REVS_DIFF);
            dispatcher.takeRecordedResponseBlocking(revsDiffRequest);

            putReplicationOnline(replicator);

            // we are going online again, so the mockwebserver should accept _revs_diff responses again
            dispatcher.clearQueuedResponse(MockHelper.PATH_REGEX_REVS_DIFF);
            dispatcher.clearRecordedRequests(MockHelper.PATH_REGEX_REVS_DIFF);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // wait until mock server gets the 2nd checkpoint PUT request
            foundCheckpointPut = false;
            expectedLastSequence = "2";
            while (!foundCheckpointPut) {
                RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
                if (request.getMethod().equals("PUT")) {
                    foundCheckpointPut = true;
                    Assert.assertTrue(request.getUtf8Body().indexOf(expectedLastSequence) != -1);
                    // wait until mock server responds to the checkpoint PUT request
                    dispatcher.takeRecordedResponseBlocking(request);
                }
            }

            // make some assertions about the outgoing _bulk_docs requests
            RecordedRequest bulkDocsRequest1 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(bulkDocsRequest1);
            assertBulkDocJsonContainsDoc(bulkDocsRequest1, doc1);
            RecordedRequest bulkDocsRequest2 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(bulkDocsRequest2);
            assertBulkDocJsonContainsDoc(bulkDocsRequest2, doc2);

            // cleanup
            stopReplication(replicator);
            server.shutdown();

        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = previous;
        }

    }

    /**
     * Verify that when a replication runs into an auth error, it stops
     * and the lastError() method returns that error.
     */
    public void testReplicatorErrorStatus() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // fake _session response
        MockSessionGet mockSessionGet = new MockSessionGet();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_SESSION, mockSessionGet.generateMockResponse());

        // fake _facebook response
        MockFacebookAuthPost mockFacebookAuthPost = new MockFacebookAuthPost();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_FACEBOOK_AUTH, mockFacebookAuthPost.generateMockResponse());

        // start mock server
        server.play();

        // register bogus fb token
        Authenticator facebookAuthenticator = AuthenticatorFactory.createFacebookAuthenticator("fake_access_token");

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setAuthenticator(facebookAuthenticator);
        pullReplication.setContinuous(false);
        runReplication(pullReplication);

        // run replicator and make sure it has an error
        assertNotNull(pullReplication.getLastError());
        assertTrue(pullReplication.getLastError() instanceof HttpResponseException);
        assertEquals(401 /* unauthorized */, ((HttpResponseException)pullReplication.getLastError()).getStatusCode());

        // assert that the replicator sent the requests we expected it to send
        RecordedRequest sessionReqeust = dispatcher.takeRequest(MockHelper.PATH_REGEX_SESSION);
        assertNotNull(sessionReqeust);
        RecordedRequest facebookRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_FACEBOOK_AUTH);
        assertNotNull(facebookRequest);
        dispatcher.verifyAllRecordedRequestsTaken();


    }


    public void testGetReplicator() throws Throwable {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint PUT or GET response (sticky)
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(500);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        server.play();

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", server.getUrl("/db").toExternalForm());

        Map<String,Object> headers = new HashMap<String,Object>();
        String coolieVal = "SyncGatewaySession=c38687c2696688a";
        headers.put("Cookie", coolieVal);
        properties.put("headers", headers);

        Replication replicator = manager.getReplicator(properties);
        assertNotNull(replicator);
        assertEquals(server.getUrl("/db").toExternalForm(), replicator.getRemoteUrl().toExternalForm());
        assertTrue(!replicator.isPull());
        assertFalse(replicator.isContinuous());
        assertFalse(replicator.isRunning());
        assertTrue(replicator.getHeaders().containsKey("Cookie"));
        assertEquals(replicator.getHeaders().get("Cookie"), coolieVal);

        // add replication observer
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        replicator.addChangeListener(replicationFinishedObserver);

        // start the replicator
        Log.d(TAG, "Starting replicator " + replicator);
        replicator.start();

        final CountDownLatch replicationStarted = new CountDownLatch(1);
        replicator.addChangeListener(new ReplicationActiveObserver(replicationStarted));

        boolean success = replicationStarted.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        // now lets lookup existing replicator and stop it
        Log.d(TAG, "Looking up replicator");
        properties.put("cancel", true);
        Replication activeReplicator = manager.getReplicator(properties);
        Log.d(TAG, "Found replicator " + activeReplicator + " and calling stop()");

        activeReplicator.stop();
        Log.d(TAG, "called stop(), waiting for it to finish");

        // wait for replication to finish
        boolean didNotTimeOut = replicationDoneSignal.await(180, TimeUnit.SECONDS);
        Log.d(TAG, "replicationDoneSignal.await done, didNotTimeOut: " + didNotTimeOut);

        assertTrue(didNotTimeOut);
        assertFalse(activeReplicator.isRunning());

        server.shutdown();


    }

    public void testGetReplicatorWithAuth() throws Throwable {

        Map<String,Object> authProperties = getReplicationAuthParsedJson();

        Map<String,Object> targetProperties = new HashMap<String,Object>();
        targetProperties.put("url", getReplicationURL().toExternalForm());
        targetProperties.put("auth", authProperties);

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", targetProperties);

        Replication replicator = manager.getReplicator(properties);
        assertNotNull(replicator);
        assertNotNull(replicator.getAuthenticator());
        assertTrue(replicator.getAuthenticator() instanceof FacebookAuthorizer);

    }

    /**
     *
     * When the server returns a 409 error to a PUT checkpoint response, make
     * sure it does the right thing:
     * - Pull latest remote checkpoint
     * - Try to push checkpiont again (this time passing latest rev)
     *
     * @throws Exception
     */
    public void testPutCheckpoint409Recovery() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // mock documents to be pulled
        MockDocumentGet.MockDocument mockDoc1 = new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
        mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());

        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // doc1 response
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc1);
        dispatcher.enqueueResponse(mockDoc1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // respond with 409 error to mock checkpoint PUT
        MockResponse checkpointResponse409 = new MockResponse();
        checkpointResponse409.setStatus("HTTP/1.1 409 CONFLICT");
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, checkpointResponse409);

        // the replicator should then try to do a checkpoint GET, and in this case
        // it should return a value with a rev id
        MockCheckpointGet mockCheckpointGet = new MockCheckpointGet();
        mockCheckpointGet.setOk("true");
        mockCheckpointGet.setRev("0-1");
        mockCheckpointGet.setLastSequence("0");
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointGet);

        // the replicator should then try a checkpoint PUT again
        // and we should respond with a 201
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));

        // I had to set this to continuous, because in a one-shot replication it tries to
        // save the checkpoint asynchronously as the replicator is shutting down, which
        // breaks the retry logic in the case a 409 conflict is returned by server.
        pullReplication.setContinuous(true);

        pullReplication.start();

        // we should have gotten two requests to PATH_REGEX_CHECKPOINT:
        // PUT -> 409 Conflict
        // PUT -> 201 Created
        for (int i=1; i<=2; i++) {
            Log.v(TAG, "waiting for PUT checkpoint: %d", i);
            waitForPutCheckpointRequestWithSeq(dispatcher, mockDoc1.getDocSeq());
            Log.d(TAG, "got PUT checkpoint: %d", i);
        }

        stopReplication(pullReplication);

        server.shutdown();


    }


    /**
     * Verify that Validation based Rejects revert the entire batch that the document is in
     * even if one of the documents fail the validation.
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/242
     *
     * @throws Exception
     */
    public void failingTestVerifyPullerInsertsDocsWithValidation() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, 2, 2);
        server.play();

        // Setup validation to reject document with id: doc1
        database.setValidation("validateOnlyDoc1", new Validator() {
            @Override
            public void validate(Revision newRevision, ValidationContext context) {
                if ("doc1".equals(newRevision.getDocument().getId())) {
                    context.reject();
                }
            }
        });

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        runReplication(pullReplication);

        assertNotNull(database);
        // doc1 should not be in the store because of validation
        assertNull(database.getExistingDocument("doc1"));

        // doc0 should be in the store, but it wont be because of the bug.
        assertNotNull(database.getExistingDocument("doc0"));

    }

    /**
     * Make sure calling puller.setChannels() causes the changetracker to send the correct
     * request to the sync gateway.
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/292
     */
    public void testChannelsFilter() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint PUT or GET response (sticky)
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setChannels(Arrays.asList("foo", "bar"));

        runReplication(pullReplication);

        // make assertions about outgoing requests from replicator -> mock
        RecordedRequest getChangesFeedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
        assertTrue(getChangesFeedRequest.getMethod().equals("POST"));
        String body = getChangesFeedRequest.getUtf8Body();
        Map <String, Object> jsonMap = Manager.getObjectMapper().readValue(body, Map.class);
        assertTrue(jsonMap.containsKey("filter"));
        String filter = (String) jsonMap.get("filter");
        assertEquals("sync_gateway/bychannel", filter);

        assertTrue(jsonMap.containsKey("channels"));
        String channels = (String) jsonMap.get("channels");
        assertTrue(channels.contains("foo"));
        assertTrue(channels.contains("bar"));

        server.shutdown();

    }

    /**
     * - Start continuous pull
     * - Mockwebserver responds that there are no changes
     * - Assert that puller goes into IDLE state
     *
     * https://github.com/couchbase/couchbase-lite-android/issues/445
     */
    public void testContinuousPullEntersIdleState() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        // add non-sticky changes response that returns no changes
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // add sticky _changes response that just blocks for 60 seconds to emulate
        // server that doesn't have any new changes
        MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
        mockChangesFeedNoResponse.setDelayMs(60 * 1000);
        mockChangesFeedNoResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

        server.play();

        // create pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);

        final CountDownLatch enteredIdleState = new CountDownLatch(1);
        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getSource().getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE) {
                    enteredIdleState.countDown();
                }
            }
        });

        // start pull replication
        pullReplication.start();

        boolean success = enteredIdleState.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        Log.d(TAG, "Got IDLE event, stopping replication");

        stopReplication(pullReplication);
        server.shutdown();

    }

    /**
     * Spotted in https://github.com/couchbase/couchbase-lite-java-core/issues/313
     * But there is another ticket that is linked off 313
     */
    public void testMockPullBulkDocsSyncGw() throws Exception {
        mockPullBulkDocs(MockDispatcher.ServerType.SYNC_GW);
    }


    public void mockPullBulkDocs(MockDispatcher.ServerType serverType) throws Exception {

        // set INBOX_CAPACITY to a smaller value so that processing times don't skew the test
        ReplicationInternal.INBOX_CAPACITY = 10;

        // serve 25 mock docs
        int numMockDocsToServe = (ReplicationInternal.INBOX_CAPACITY * 2) + (ReplicationInternal.INBOX_CAPACITY / 2);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(serverType);

        // mock documents to be pulled
        List<MockDocumentGet.MockDocument> mockDocs = MockHelper.getMockDocuments(numMockDocsToServe);

        // respond to all GET (responds with 404) and PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _changes response
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        for (MockDocumentGet.MockDocument mockDocument : mockDocs) {
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument));
        }
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // individual doc responses (expecting it to call _bulk_docs, but just in case)
        for (MockDocumentGet.MockDocument mockDocument : mockDocs) {
            MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDocument);
            dispatcher.enqueueResponse(mockDocument.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        }

        // _bulk_get response
        MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
        for (MockDocumentGet.MockDocument mockDocument : mockDocs) {
            mockBulkGet.addDocument(mockDocument);
        }
        mockBulkGet.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        runReplication(pullReplication);
        assertTrue(pullReplication.getLastError() == null);

        // wait until it pushes checkpoint of last doc
        MockDocumentGet.MockDocument lastDoc = mockDocs.get(mockDocs.size()-1);
        waitForPutCheckpointRequestWithSequence(dispatcher, lastDoc.getDocSeq());

        // dump out the outgoing requests for bulk docs
        BlockingQueue<RecordedRequest> bulkGetRequests = dispatcher.getRequestQueueSnapshot(MockHelper.PATH_REGEX_BULK_GET);
        Iterator<RecordedRequest> iterator = bulkGetRequests.iterator();
        while (iterator.hasNext()) {
            RecordedRequest bulkGetRequest = iterator.next();

            Map <String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(bulkGetRequest.getUtf8Body(), Map.class);
            List docs = (List) bulkDocsJson.get("docs");
            Log.d(TAG, "bulk get request: %s had %d docs", bulkGetRequest, docs.size());

            if (iterator.hasNext()) {
                // the bulk docs requests except for the last one should have max number of docs
                // relax this a bit, so that it at least has to have greater than or equal to half max number of docs
                assertTrue(docs.size() >= (ReplicationInternal.INBOX_CAPACITY / 2));
                if (docs.size() != ReplicationInternal.INBOX_CAPACITY) {
                    Log.w(TAG, "docs.size() %d != ReplicationInternal.INBOX_CAPACITY %d", docs.size(), ReplicationInternal.INBOX_CAPACITY);
                }
            }

        }

        // should not be any requests for individual docs
        for (MockDocumentGet.MockDocument mockDocument : mockDocs) {
            MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDocument);
            BlockingQueue<RecordedRequest> requestsForDoc = dispatcher.getRequestQueueSnapshot(mockDocument.getDocPathRegex());
            assertTrue(requestsForDoc == null || requestsForDoc.isEmpty());
        }

        server.shutdown();


    }

    /**
     * Make sure that after trying /db/_session, it should try /_session.
     *
     * Currently there is a bug where it tries /db/_session, and then
     * tries /db_session.
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/208
     */
    public void testCheckSessionAtPath() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);

        // session GET response w/ 404 to /db/_session
        MockResponse fakeSessionResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeSessionResponse);
        WrappedSmartMockResponse wrappedSmartMockResponse = new WrappedSmartMockResponse(fakeSessionResponse);
        wrappedSmartMockResponse.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_SESSION, wrappedSmartMockResponse);

        // session GET response w/ 200 OK to /_session
        MockResponse fakeSessionResponse2 = new MockResponse();
        Map<String, Object> responseJson = new HashMap<String, Object>();
        Map<String, Object> userCtx = new HashMap<String, Object>();
        userCtx.put("name", "foo");
        responseJson.put("userCtx", userCtx);
        fakeSessionResponse2.setBody(Manager.getObjectMapper().writeValueAsBytes(responseJson));
        MockHelper.set200OKJson(fakeSessionResponse2);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_SESSION_COUCHDB, fakeSessionResponse2);

        // respond to all GET/PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setAuthenticator(new FacebookAuthorizer("justinbieber@glam.co"));
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        pullReplication.addChangeListener(replicationFinishedObserver);
        pullReplication.start();

        // it should first try /db/_session
        dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_SESSION);

        // and then it should fallback to /_session
        dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_SESSION_COUCHDB);

        boolean success = replicationDoneSignal.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(success);

        server.shutdown();

    }

    /**
     *
     * - Start one shot replication
     * - Changes feed request returns error
     * - Change tracker stops
     * - Replication stops -- make sure ChangeListener gets error
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/334
     */
    public void testChangeTrackerError() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // 404 response to _changes feed (sticky)
        MockResponse mockChangesFeed = new MockResponse();
        MockHelper.set404NotFoundJson(mockChangesFeed);
        WrappedSmartMockResponse wrapped = new WrappedSmartMockResponse(mockChangesFeed);
        wrapped.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, wrapped);

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));

        final CountDownLatch changeEventError = new CountDownLatch(1);
        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getError() != null) {
                    changeEventError.countDown();
                }
            }
        });

        runReplication(pullReplication);
        Assert.assertTrue(pullReplication.getLastError() != null);

        boolean success = changeEventError.await(5, TimeUnit.SECONDS);
        Assert.assertTrue(success);

        server.shutdown();
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/358
     *
     * @related: https://github.com/couchbase/couchbase-lite-java-core/issues/55
     * related: testContinuousPushReplicationGoesIdle()
     *
     * test steps:
     * - start replicator
     * - make sure replicator becomes idle state
     * - add N docs
     * - when callback state == idle
     * - assert that mock has received N docs
     */
    public void testContinuousPushReplicationGoesIdleTwice() throws Exception{

        // /_local/*
        // /_revs_diff
        // /_bulk_docs
        // /_local/*
        final int EXPECTED_REQUEST_COUNT = 4;

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // 1. Setup MockWebServer

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        final MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint GET response w/ 404.  also receives checkpoint PUT's
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(500);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        // _bulk_docs response -- everything stored
        MockBulkDocs mockBulkDocs = new MockBulkDocs();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

        server.play();

        // 2. Create replication
        Replication replication = database.createPushReplication(server.getUrl("/db"));
        replication.setContinuous(true);
        CountDownLatch replicationIdle = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdle);
        replication.addChangeListener(idleObserver);
        replication.start();

        // 3. Wait until idle (make sure replicator becomes IDLE state)
        boolean success = replicationIdle.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        replication.removeChangeListener(idleObserver);

        // 4. make sure if /_local was called by replicator after start and before idle
        RecordedRequest request1 = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
        assertNotNull(request1);
        dispatcher.takeRecordedResponseBlocking(request1);
        assertEquals(1, server.getRequestCount());

        // 5. Add replication change listener for transition to IDLE
        class ReplicationTransitionToIdleObserver implements  Replication.ChangeListener{
            private CountDownLatch doneSignal;
            private CountDownLatch checkSignal;
            public ReplicationTransitionToIdleObserver(CountDownLatch doneSignal, CountDownLatch checkSignal) {
                this.doneSignal = doneSignal;
                this.checkSignal = checkSignal;
            }
            public void changed(Replication.ChangeEvent event) {
                Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] event => " + event.toString());
                if(event.getTransition()!=null){
                    if(event.getTransition().getSource() != event.getTransition().getDestination() &&
                       event.getTransition().getDestination() == ReplicationState.IDLE){
                        Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Transition to  IDLE");
                        Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Request Count => " + server.getRequestCount());

                        this.doneSignal.countDown();

                        // When replicator becomes IDLE state, check if all requests are completed
                        // assertEquals in inner class does not work....
                        // Note: sometimes server.getRequestCount() returns expected number - 1.
                        //       Is it timing issue?
                        if(EXPECTED_REQUEST_COUNT == server.getRequestCount() ||
                           EXPECTED_REQUEST_COUNT - 1 == server.getRequestCount()){
                            this.checkSignal.countDown();
                        }
                    }
                }
            }
        }
        CountDownLatch checkStateToIdle = new CountDownLatch(1);
        CountDownLatch checkRequestCount = new CountDownLatch(1);
        ReplicationTransitionToIdleObserver replicationTransitionToIdleObserver =
                new ReplicationTransitionToIdleObserver(checkStateToIdle, checkRequestCount);
        replication.addChangeListener(replicationTransitionToIdleObserver);
        Log.w(Log.TAG_SYNC, "Added listener for transition to IDLE");

        // 6. Add doc(s)
        for(int i = 1; i <= 1; i++) {
            Map<String, Object> properties1 = new HashMap<String, Object>();
            properties1.put("doc" + String.valueOf(i), "testContinuousPushReplicationGoesIdleTooSoon "+ String.valueOf(i));
            final Document doc = createDocWithProperties(properties1);
        }

        // 7. Wait until idle (make sure replicator becomes IDLE state from other state)
        // NOTE: 12/17/2014 - current code fails here because after adding listener, state never changed from IDLE
        //       By implementing stateMachine for Replication completely, address this failure.
        success = checkStateToIdle.await(20, TimeUnit.SECONDS); // check if state becomes IDLE from other state
        assertTrue(success);
        success = checkRequestCount.await(20, TimeUnit.SECONDS); // check if request count is 4 when state becomes IDLE
        assertTrue(success);

        // 8. Make sure some of requests are called
        // _diff_revs
        //RecordedRequest request2 = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_REVS_DIFF);
        //assertNotNull(request2);
        //dispatcher.takeRecordedResponseBlocking(request2);
        // _bulk_docs
        RecordedRequest request3 = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
        assertNotNull(request3);
        dispatcher.takeRecordedResponseBlocking(request3);
        // _local
        RecordedRequest request4 = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
        assertNotNull(request4);
        dispatcher.takeRecordedResponseBlocking(request4);
        // double check total request
        Log.w(Log.TAG_SYNC, "Total Requested Count before stop replicator => " + server.getRequestCount());
        assertEquals(EXPECTED_REQUEST_COUNT, server.getRequestCount());

        // 9. Stop replicator
        replication.removeChangeListener(replicationTransitionToIdleObserver);
        stopReplication(replication);

        // 10. Stop MockWebServer
        // NOTE: immediate MockWebServer shutdown causes IOException during stopping replicator.
        // server.shutdown();
    }


    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/358
     *
     * related: testContinuousPushReplicationGoesIdleTooSoon()
     *          testContinuousPushReplicationGoesIdle()
     *
     * test steps:
     * - add N docs
     * - start replicator
     * - when callback state == idle
     * - assert that mock has received N docs
     */
    public void testContinuousPushReplicationGoesIdleTooSoon() throws Exception{

        // smaller batch size so there are multiple requests to _bulk_docs
        ReplicationInternal.INBOX_CAPACITY = 5;
        int numDocs = ReplicationInternal.INBOX_CAPACITY * 5;

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // Add doc(s)
        // NOTE: more documents causes more HTTP calls. It could be more than 4 times...
        for(int i = 1; i <= numDocs; i++) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("doc" + String.valueOf(i), "testContinuousPushReplicationGoesIdleTooSoon "+ String.valueOf(i));
            final Document doc = createDocWithProperties(properties);
        }

        // Setup MockWebServer
        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        final MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        // checkpoint GET response w/ 404.  also receives checkpoint PUT's
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(500);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);
        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        mockRevsDiff.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);
        // _bulk_docs response -- everything stored
        MockBulkDocs mockBulkDocs = new MockBulkDocs();
        mockBulkDocs.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);
        server.play();

        // Create replicator
        Replication replication = database.createPushReplication(server.getUrl("/db"));
        replication.setContinuous(true);
        // special change listener for this test case.
        class ReplicationTransitionToIdleObserver implements  Replication.ChangeListener{
            private CountDownLatch enterIdleStateSignal;
            public ReplicationTransitionToIdleObserver(CountDownLatch enterIdleStateSignal) {
                this.enterIdleStateSignal = enterIdleStateSignal;
            }
            public void changed(Replication.ChangeEvent event) {
                Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] event => " + event.toString());
                if(event.getTransition()!=null){
                    if(event.getTransition().getSource() != event.getTransition().getDestination() &&
                            event.getTransition().getDestination() == ReplicationState.IDLE){
                        Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Transition to  IDLE");
                        Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Request Count => " + server.getRequestCount());

                        this.enterIdleStateSignal.countDown();


                    }
                }
            }
        }
        CountDownLatch enterIdleStateSignal        = new CountDownLatch(1);
        ReplicationTransitionToIdleObserver replicationTransitionToIdleObserver = new ReplicationTransitionToIdleObserver(enterIdleStateSignal);
        replication.addChangeListener(replicationTransitionToIdleObserver);
        replication.start();

        // Wait until idle (make sure replicator becomes IDLE state from other state)
        boolean  success = enterIdleStateSignal.await(20, TimeUnit.SECONDS);
        assertTrue(success);

        // Once the replicator is idle get a snapshot of all the requests its made to _bulk_docs endpoint
        int numDocsPushed = 0;
        BlockingQueue<RecordedRequest> requests = dispatcher.getRequestQueueSnapshot(MockHelper.PATH_REGEX_BULK_DOCS);
        for (RecordedRequest request : requests) {
            Log.i(Log.TAG_SYNC, "request: %s", request);
            Map <String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(request.getUtf8Body(), Map.class);
            List docs = (List) bulkDocsJson.get("docs");
            numDocsPushed += docs.size();
        }

        // Assert that all docs have already been pushed by the time it goes IDLE
        assertEquals(numDocs, numDocsPushed);

        // Stop replicator and MockWebServer
        stopReplication(replication);

        // wait until checkpoint is pushed, since it can happen _after_ replication is finished.
        // if this isn't done, there can be IOExceptions when calling server.shutdown()
        waitForPutCheckpointRequestWithSeq(dispatcher, (int) database.getLastSequenceNumber());

        server.shutdown();
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/352
     *
     * When retrying a replication, make sure to get session & checkpoint.
     *
     */
    public void testCheckSessionAndCheckpointWhenRetryingReplication() throws Exception{

        RemoteRequestRetry.RETRY_DELAY_MS = 5;       // speed up test execution (inner loop retry delay)

        ReplicationInternal.RETRY_DELAY_SECONDS = 1; // speed up test execution (outer loop retry delay)
        ReplicationInternal.MAX_RETRIES = 3;         // speed up test execution (outer loop retry count)


        String fakeEmail = "myfacebook@gmail.com";

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // set up request
        {
            // response for /db/_session
            MockSessionGet mockSessionGet = new MockSessionGet();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_SESSION, mockSessionGet.generateMockResponse());

            // response for /db/_facebook
            MockFacebookAuthPost mockFacebookAuthPost = new MockFacebookAuthPost();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_FACEBOOK_AUTH, mockFacebookAuthPost.generateMockResponseForSuccess(fakeEmail));

            // response for /db/_local/.*
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(500);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // response for /db/_revs_diff
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            mockRevsDiff.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // response for /db/_bulk_docs  -- 503 errors
            MockResponse mockResponse = new MockResponse().setResponseCode(503);
            WrappedSmartMockResponse mockBulkDocs = new WrappedSmartMockResponse(mockResponse, false);
            mockBulkDocs.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);
        }
        server.play();

        // register bogus fb token
        Authenticator facebookAuthenticator = AuthenticatorFactory.createFacebookAuthenticator("fake_access_token");

        // create replication
        Replication replication = database.createPushReplication(server.getUrl("/db"));
        replication.setAuthenticator(facebookAuthenticator);
        replication.setContinuous(true);
        CountDownLatch replicationIdle = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdle);
        replication.addChangeListener(idleObserver);
        replication.start();

        // wait until idle
        boolean success = replicationIdle.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        replication.removeChangeListener(idleObserver);

        // create a doc in local db
        Document doc1 = createDocumentForPushReplication("doc1", null, null);

        // initial request
        {
            // check /db/_session
            RecordedRequest sessionRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_SESSION);
            assertNotNull(sessionRequest);
            dispatcher.takeRecordedResponseBlocking(sessionRequest);

            // check /db/_facebook
            RecordedRequest facebookSessionRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_FACEBOOK_AUTH);
            assertNotNull(facebookSessionRequest);
            dispatcher.takeRecordedResponseBlocking(facebookSessionRequest);

            // check /db/_local/.*
            RecordedRequest checkPointRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            assertNotNull(checkPointRequest);
            dispatcher.takeRecordedResponseBlocking(checkPointRequest);

            // check /db/_revs_diff
            RecordedRequest revsDiffRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_REVS_DIFF);
            assertNotNull(revsDiffRequest);
            dispatcher.takeRecordedResponseBlocking(revsDiffRequest);

            // we should expect to at least see numAttempts attempts at doing POST to _bulk_docs
            // 1st attempt
            // numAttempts are number of times retry in 1 attempt.
            int numAttempts = RemoteRequestRetry.MAX_RETRIES + 1; // total number of attempts = 4 (1 initial + MAX_RETRIES)
            for (int i = 0; i < numAttempts; i++) {
                RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNotNull(request);
                dispatcher.takeRecordedResponseBlocking(request);
            }
        }

        // To test following, requires to fix #299 (improve retry behavior)

        // Retry requests
        // outer retry loop
        for(int j = 0; j < ReplicationInternal.MAX_RETRIES; j++){

            // MockSessionGet does not support isSticky
            MockSessionGet mockSessionGet = new MockSessionGet();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_SESSION, mockSessionGet.generateMockResponse());

            // MockFacebookAuthPost does not support isSticky
            MockFacebookAuthPost mockFacebookAuthPost = new MockFacebookAuthPost();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_FACEBOOK_AUTH, mockFacebookAuthPost.generateMockResponseForSuccess(fakeEmail));

            // *** Retry must include session & check point ***

            // check /db/_session
            RecordedRequest sessionRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_SESSION);
            assertNotNull(sessionRequest);
            dispatcher.takeRecordedResponseBlocking(sessionRequest);

            // check /db/_facebook
            RecordedRequest facebookSessionRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_FACEBOOK_AUTH);
            assertNotNull(facebookSessionRequest);
            dispatcher.takeRecordedResponseBlocking(facebookSessionRequest);

            // check /db/_local/.*
            RecordedRequest checkPointRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            assertNotNull(checkPointRequest);
            dispatcher.takeRecordedResponseBlocking(checkPointRequest);

            // check /db/_revs_diff
            RecordedRequest revsDiffRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_REVS_DIFF);
            assertNotNull(revsDiffRequest);
            dispatcher.takeRecordedResponseBlocking(revsDiffRequest);

            // we should expect to at least see numAttempts attempts at doing POST to _bulk_docs
            // 1st attempt
            // numAttempts are number of times retry in 1 attempt.
            int numAttempts = RemoteRequestRetry.MAX_RETRIES + 1; // total number of attempts = 4 (1 initial + MAX_RETRIES)
            for (int i = 0; i < numAttempts; i++) {
                RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNotNull(request);
                dispatcher.takeRecordedResponseBlocking(request);
            }
        }

        stopReplication(replication);

        //server.shutdown();
    }
    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/352
     *
     * Makes the replicator stop, even if it’s continuous, when it receives a permanent-type error
     */
    public void testStopReplicatorWhenRetryingReplicationWithPermanentError() throws Exception{
        RemoteRequestRetry.RETRY_DELAY_MS = 5;       // speed up test execution (inner loop retry delay)

        ReplicationInternal.RETRY_DELAY_SECONDS = 1; // speed up test execution (outer loop retry delay)
        ReplicationInternal.MAX_RETRIES = 3;         // speed up test execution (outer loop retry count)

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        // set up request
        {
            // response for /db/_local/.*
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(500);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // response for /db/_revs_diff
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            mockRevsDiff.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // response for /db/_bulk_docs  -- 400 Bad Request (not transient error)
            MockResponse mockResponse = new MockResponse().setResponseCode(400);
            WrappedSmartMockResponse mockBulkDocs = new WrappedSmartMockResponse(mockResponse, false);
            mockBulkDocs.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);
        }
        server.play();

        // create replication
        Replication replication = database.createPushReplication(server.getUrl("/db"));
        replication.setContinuous(true);
        // add replication observer for IDLE state
        CountDownLatch replicationIdle = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdle);
        replication.addChangeListener(idleObserver);
        // add replication observer for finished
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        replication.addChangeListener(replicationFinishedObserver);

        replication.start();

        // wait until idle
        boolean success = replicationIdle.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        replication.removeChangeListener(idleObserver);

        // create a doc in local db
        Document doc1 = createDocumentForPushReplication("doc1", null, null);

        // initial request
        {
            // check /db/_local/.*
            RecordedRequest checkPointRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            assertNotNull(checkPointRequest);
            dispatcher.takeRecordedResponseBlocking(checkPointRequest);

            // check /db/_revs_diff
            RecordedRequest revsDiffRequest = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_REVS_DIFF);
            assertNotNull(revsDiffRequest);
            dispatcher.takeRecordedResponseBlocking(revsDiffRequest);

            // we should observe only one POST to _bulk_docs request because error is not transient error
            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(request);
            dispatcher.takeRecordedResponseBlocking(request);
        }

        // Without fixing CBL Java Core #352, following code causes hang. 

        // wait for replication to finish
        boolean didNotTimeOut = replicationDoneSignal.await(180, TimeUnit.SECONDS);
        Log.d(TAG, "replicationDoneSignal.await done, didNotTimeOut: " + didNotTimeOut);
        assertFalse(replication.isRunning());

        server.shutdown();
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/383
     */
    public void testContinuousPullReplicationGoesIdleTwice() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

        // checkpoint PUT or GET response (sticky)
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // add non-sticky changes response that returns no changes
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // add  _changes response that just blocks for a few seconds to emulate
        // server that doesn't have any new changes
        MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
        mockChangesFeedNoResponse.setDelayMs(5 * 1000);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

        // real _changes response with doc1
        MockDocumentGet.MockDocument mockDoc1 = new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
        mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());
        mockChangesFeed = new MockChangesFeed();
        mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // the rest of the _changes responses should just block for 5 seconds and return nothing
        MockChangesFeedNoResponse mockChangesFeedNoResponse2 = new MockChangesFeedNoResponse();
        mockChangesFeedNoResponse2.setDelayMs(60 * 1000);
        mockChangesFeedNoResponse2.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse2);

        // doc1 response
        MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc1);
        dispatcher.enqueueResponse(mockDoc1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

        // _revs_diff response -- everything missing
        MockRevsDiff mockRevsDiff = new MockRevsDiff();
        mockRevsDiff.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

        server.play();

        // create pull replication
        Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
        pullReplication.setContinuous(true);

        final CountDownLatch enteredIdleState1 = new CountDownLatch(1);
        final CountDownLatch enteredIdleState2 = new CountDownLatch(1);
        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getSource().getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE) {
                    enteredIdleState1.countDown();
                }
            }
        });

        // start pull replication
        pullReplication.start();

        // wait until its IDLE
        Log.d(TAG, "wait until its IDLE #1");
        boolean success = enteredIdleState1.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        Log.d(TAG, "/wait until its IDLE #1");

        // change listener to see if its RUNNING
        final CountDownLatch enteredRunningState = new CountDownLatch(1);
        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getSource().getStatus() == Replication.ReplicationStatus.REPLICATION_ACTIVE) {
                    Log.d(TAG, "Replication is running");
                    enteredRunningState.countDown();
                }
            }
        });

        // wait until its RUNNING
        Log.d(TAG, "wait until its RUNNING");
        success = enteredRunningState.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        Log.d(TAG, "/wait until its RUNNING");

        // second IDLE change listener
        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getSource().getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE) {
                    Log.d(TAG, "Replication is IDLE");
                    enteredIdleState2.countDown();
                }
            }
        });

        // wait until its IDLE again
        Log.d(TAG, "wait until its IDLE #2");
        success = enteredIdleState2.await(30, TimeUnit.SECONDS);
        assertTrue(success);
        Log.d(TAG, "/wait until its IDLE #2");


        stopReplication(pullReplication);
        server.shutdown();

    }


}
