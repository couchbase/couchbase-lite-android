package com.couchbase.lite.replicator;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryOptions;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.ReplicationFilter;
import com.couchbase.lite.Revision;
import com.couchbase.lite.SavedRevision;
import com.couchbase.lite.Status;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.ValidationContext;
import com.couchbase.lite.Validator;
import com.couchbase.lite.View;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.auth.BasicAuthenticator;
import com.couchbase.lite.auth.FacebookAuthorizer;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockBulkDocs;
import com.couchbase.lite.mockserver.MockChangesFeed;
import com.couchbase.lite.mockserver.MockChangesFeedNoResponse;
import com.couchbase.lite.mockserver.MockCheckpointGet;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockDocumentAllDocs;
import com.couchbase.lite.mockserver.MockDocumentBulkGet;
import com.couchbase.lite.mockserver.MockDocumentGet;
import com.couchbase.lite.mockserver.MockDocumentPut;
import com.couchbase.lite.mockserver.MockFacebookAuthPost;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.MockRevsDiff;
import com.couchbase.lite.mockserver.MockSessionGet;
import com.couchbase.lite.mockserver.SmartMockResponseImpl;
import com.couchbase.lite.mockserver.WrappedSmartMockResponse;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.support.CouchbaseLiteHttpClientFactory;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.support.MultipartReader;
import com.couchbase.lite.support.MultipartReaderDelegate;
import com.couchbase.lite.support.RemoteRequestRetry;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;
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
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for the new state machine based replicator
 */
public class ReplicationTest extends LiteTestCaseWithDB {

    /**
     * TestCase(CreateReplicators) in ReplicationAPITests.m
     */
    public void testCreateReplicators() throws Exception {
        URL fakeRemoteURL = new URL("http://fake.fake/fakedb");

        // Create a replicaton:
        assertEquals(0, database.getAllReplications().size());
        Replication r1 = database.createPushReplication(fakeRemoteURL);
        assertNotNull(r1);

        // Check the replication's properties:
        assertEquals(database, r1.getLocalDatabase());
        assertEquals(fakeRemoteURL, r1.getRemoteUrl());
        assertFalse(r1.isPull());
        assertFalse(r1.isContinuous());
        assertFalse(r1.shouldCreateTarget());
        assertNull(r1.getFilter());
        assertNull(r1.getFilterParams());
        assertNull(r1.getDocIds());
        assertEquals(0, r1.getHeaders().size());

        // Check that the replication hasn't started running:
        assertFalse(r1.isRunning());
        assertEquals(Replication.ReplicationStatus.REPLICATION_STOPPED, r1.getStatus());
        assertEquals(0, r1.getChangesCount());
        assertEquals(0, r1.getCompletedChangesCount());
        assertNull(r1.getLastError());

        // Create another replication:
        Replication r2 = database.createPullReplication(fakeRemoteURL);
        assertNotNull(r2);
        assertTrue(r1 != r2);

        // Check the replication's properties:
        assertEquals(database, r2.getLocalDatabase());
        assertEquals(fakeRemoteURL, r2.getRemoteUrl());
        assertTrue(r2.isPull());

        Replication r3 = database.createPullReplication(fakeRemoteURL);
        assertNotNull(r3);
        assertTrue(r3 != r2);
        r3.setDocIds(Arrays.asList("doc1", "doc2"));

        Replication repl = database.getManager().getReplicator(r3.getProperties());
        assertEquals(r3.getDocIds(), repl.getDocIds());
    }

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
        MockWebServer server = new MockWebServer();
        try {
            MockDispatcher dispatcher = new MockDispatcher();
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
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
        } finally {
            server.shutdown();
        }
    }

    /**
     * Start continuous replication with a closed db.
     * <p/>
     * Expected behavior:
     * - Receive replication finished callback
     * - Replication lastError will contain an exception
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
     * <p/>
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
     * <p/>
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
     * <p/>
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
     * <p/>
     * - Single one-shot pull replication
     * - Against simulated sync gateway
     * - Remote docs have attachments
     * <p/>
     * TODO: sporadic assertion failure when checking rev field of PUT checkpoint requests
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
     * @param serverType            - should the mock return the Sync Gateway server type in
     *                              the "Server" HTTP Header?  this changes the behavior of the
     *                              replicator to use bulk_get and POST reqeusts for _changes feeds.
     * @param addAttachments        - should the mock sync gateway return docs with attachments?
     * @return a map that contains the mockwebserver (key="server") and the mock dispatcher
     * (key="dispatcher")
     */
    public Map<String, Object> mockSinglePull(boolean shutdownMockWebserver, MockDispatcher.ServerType serverType, boolean addAttachments) throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        try {
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

            // Empty _all_docs response to pass unit tests
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_ALL_DOCS, new MockDocumentAllDocs());

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
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));
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
            RecordedRequest request = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_GET);
            if (request != null) {
                String body = MockHelper.getUtf8Body(request);
                assertTrue(body.contains(mockDoc1.getDocId()));
                assertTrue(body.contains(mockDoc2.getDocId()));
            } else {
                RecordedRequest doc1Request = dispatcher.takeRequest(mockDoc1.getDocPathRegex());
                assertTrue(doc1Request.getMethod().equals("GET"));
                assertTrue(doc1Request.getPath().matches(mockDoc1.getDocPathRegex()));
                RecordedRequest doc2Request = dispatcher.takeRequest(mockDoc2.getDocPathRegex());
                assertTrue(doc2Request.getMethod().equals("GET"));
                assertTrue(doc2Request.getPath().matches(mockDoc2.getDocPathRegex()));
            }
        } finally {
            // Shut down the server. Instances cannot be reused.
            if (shutdownMockWebserver) {
                server.shutdown();
            }
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);
        return returnVal;
    }

    /**
     * Simulate the following:
     * <p/>
     * - Add a few docs and do a pull replication
     * - One doc on sync gateway is now updated
     * - Do a second pull replication
     * - Assert we get the updated doc and save it locally
     */
    public Map<String, Object> mockMultiplePull(boolean shutdownMockWebserver, MockDispatcher.ServerType serverType) throws Exception {

        String doc1Id = "doc1";

        // create mockwebserver and custom dispatcher
        boolean addAttachments = false;

        // do a pull replication
        Map<String, Object> serverAndDispatcher = mockSinglePull(false, serverType, addAttachments);

        MockWebServer server = (MockWebServer) serverAndDispatcher.get("server");
        MockDispatcher dispatcher = (MockDispatcher) serverAndDispatcher.get("dispatcher");
        try {
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

            // Empty _all_docs response to pass unit tests
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_ALL_DOCS, new MockDocumentAllDocs());

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
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));
            assertTrue(getChangesFeedRequest.getPath().matches(MockHelper.PATH_REGEX_CHANGES));
            if (serverType == MockDispatcher.ServerType.SYNC_GW) {
                Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(getChangesFeedRequest.getUtf8Body(), Map.class);
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
        } finally {
            if (shutdownMockWebserver) {
                server.shutdown();
            }
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
        try {
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

            long lastSeq = database.getLastSequenceNumber();
            Log.i(TAG, "lastSequence = %d", lastSeq);

            // wait until the mock webserver receives a PUT checkpoint request with last do's sequence,
            // this avoids ugly and confusing exceptions in the logs.
            List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, numMockRemoteDocs - 1);
            validateCheckpointRequestsRevisions(checkpointRequests);
        } finally {
            if (shutdownMockWebserver) {
                server.shutdown();
            }
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);
        return returnVal;
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/257
     * <p/>
     * - Create local document with attachment
     * - Start continuous pull replication
     * - MockServer returns _changes with new rev of document
     * - MockServer returns doc multipart response: https://gist.github.com/tleyden/bf36f688d0b5086372fd
     * - Delete doc cache (not sure if needed)
     * - Fetch doc fresh from database
     * - Verify that it still has attachments
     */
    public void testAttachmentsDeletedOnPull() throws Exception {

        String doc1Id = "doc1";
        int doc1Rev2Generation = 2;
        String doc1Rev2Digest = "b000";
        String doc1Rev2 = String.format("%d-%s", doc1Rev2Generation, doc1Rev2Digest);
        int doc1Seq1 = 1;
        String doc1AttachName = "attachment.png";
        String contentType = "image/png";

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        try {
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.play();

            // add some documents - verify it has an attachment
            Document doc1 = createDocumentForPushReplication(doc1Id, doc1AttachName, contentType);
            String doc1Rev1 = doc1.getCurrentRevisionId();
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
            revHistory.put("ids", ids);
            mockDocumentGet.setRevHistoryMap(revHistory);
            dispatcher.enqueueResponse(mockDocument1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

            // create and start pull replication
            Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);
            pullReplication.start();

            // wait for the next PUT checkpoint request/response
            waitForPutCheckpointRequestWithSeq(dispatcher, 1);

            stopReplication(pullReplication);

            // make sure doc has attachments
            Document doc1Fetched = database.getDocument(doc1.getId());
            assertTrue(doc1Fetched.getCurrentRevision().getAttachments().size() > 0);
        } finally {
            server.shutdown();
        }
    }

    /**
     * This is essentially a regression test for a deadlock
     * that was happening when the LiveQuery#onDatabaseChanged()
     * was calling waitForUpdateThread(), but that thread was
     * waiting on connection to be released by the thread calling
     * waitForUpdateThread().  When the deadlock bug was present,
     * this test would trigger the deadlock and never finish.
     * <p/>
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
        final int VALIDATION_RETRIES = 3;
        final int TEMP_RETRY_DELAY_MS = RemoteRequestRetry.RETRY_DELAY_MS;
        final int TEMP_RETRY_DELAY_SECONDS = ReplicationInternal.RETRY_DELAY_SECONDS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;       // speed up test execution (inner loop retry delay)
        ReplicationInternal.RETRY_DELAY_SECONDS = 1; // speed up test execution (outer loop retry delay)
        try {
            // create mockwebserver and custom dispatcher
            MockDispatcher dispatcher = new MockDispatcher();
            MockWebServer server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            try {
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
                for (int i = 0; i < numAttempts; i++) {
                    RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
                    assertNotNull(request);
                    dispatcher.takeRecordedResponseBlocking(request);
                }

                // outer retry loop
                for (int j = 0; j < VALIDATION_RETRIES; j++) {
                    // inner retry loop
                    for (int i = 0; i < numAttempts; i++) {
                        RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
                        assertNotNull(request);
                        dispatcher.takeRecordedResponseBlocking(request);
                    }
                }
                // gave up replication!!!

                stopReplication(replication);
            } finally {
                server.shutdown();
            }
        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = TEMP_RETRY_DELAY_MS;
            ReplicationInternal.RETRY_DELAY_SECONDS = TEMP_RETRY_DELAY_SECONDS;
        }
    }

    public void testMockSinglePush() throws Exception {
        boolean shutdownMockWebserver = true;
        mockSinglePush(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW);
    }

    /**
     * Do a push replication
     * <p/>
     * - Create docs in local db
     * - One with no attachment
     * - One with small attachment
     * - One with large attachment
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
        try {
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
            assertTrue(MockHelper.getUtf8Body(revsDiffRequest).contains(doc1Id));

            RecordedRequest bulkDocsRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
            assertTrue(MockHelper.getUtf8Body(bulkDocsRequest).contains(doc1Id));
            Map<String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(MockHelper.getUtf8Body(bulkDocsRequest), Map.class);
            Map<String, Object> doc4Map = MockBulkDocs.findDocById(bulkDocsJson, doc4Id);
            assertTrue(((Boolean) doc4Map.get("_deleted")).booleanValue() == true);
            String str = MockHelper.getUtf8Body(bulkDocsRequest);
            Log.i(TAG, str);
            assertFalse(MockHelper.getUtf8Body(bulkDocsRequest).contains(doc2Id));

            RecordedRequest doc2putRequest = dispatcher.takeRequest(doc2PathRegex);
            CustomMultipartReaderDelegate delegate2 = new CustomMultipartReaderDelegate();
            MultipartReader reader2 = new MultipartReader(doc2putRequest.getHeader("Content-Type"), delegate2);
            reader2.appendData(doc2putRequest.getBody());
            String body2 = new String(delegate2.data, "UTF-8");
            assertTrue(body2.contains(doc2Id));
            assertFalse(body2.contains(doc3Id));

            RecordedRequest doc3putRequest = dispatcher.takeRequest(doc3PathRegex);
            CustomMultipartReaderDelegate delegate3 = new CustomMultipartReaderDelegate();
            MultipartReader reader3 = new MultipartReader(doc3putRequest.getHeader("Content-Type"), delegate3);
            reader3.appendData(doc3putRequest.getBody());
            String body3 = new String(delegate3.data, "UTF-8");
            assertTrue(body3.contains(doc3Id));
            assertFalse(body3.contains(doc2Id));

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

        } finally {
            // Shut down the server. Instances cannot be reused.
            if (shutdownMockWebserver) {
                server.shutdown();
            }
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);

        return returnVal;
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/55
     */
    public void testContinuousPushReplicationGoesIdle() throws Exception {

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // add docs
        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("doc1", "testContinuousPushReplicationGoesIdle");
        final Document doc1 = createDocWithProperties(properties1);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
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
        } finally {
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/241
     * <p/>
     * - Set the "retry time" to a short number
     * - Setup mock server to return 404 for all _changes requests
     * - Start continuous replication
     * - Sleep for 5X retry time
     * - Assert that we've received at least two requests to _changes feed
     * - Stop replication + cleanup
     */
    public void testContinuousReplication404Changes() throws Exception {

        int previous = PullerInternal.CHANGE_TRACKER_RESTART_DELAY_MS;
        PullerInternal.CHANGE_TRACKER_RESTART_DELAY_MS = 5;

        try {
            // create mockwebserver and custom dispatcher
            MockDispatcher dispatcher = new MockDispatcher();
            MockWebServer server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            try {
                server.play();

                // mock checkpoint GET response w/ 404
                MockResponse fakeCheckpointResponse = new MockResponse();
                MockHelper.set404NotFoundJson(fakeCheckpointResponse);
                dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

                // mock _changes response
                for (int i = 0; i < 100; i++) {
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
            } finally {
                server.shutdown();
            }

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
     * <p/>
     * - Create local docs in conflict
     * - Simulate sync gw responses that resolve the conflict
     * - Do pull replication
     * - Assert conflict is resolved locally
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/77
     */
    public void failingTestRemoteConflictResolution() throws Exception {

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
        for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
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
        try {

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
        } finally {
            server.shutdown();
        }
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
                HttpEntity entity = httpPut.getEntity();
                //assertFalse("PUT request with updated doc properties contains attachment", entity instanceof MultipartEntity);
            }
        }

        mockHttpClient.clearCapturedRequests();

        Document oldDoc = database.getDocument(doc.getId());
        UnsavedRevision aUnsavedRev = oldDoc.createRevision();
        Map<String, Object> prop = new HashMap<String, Object>();
        prop.putAll(oldDoc.getProperties());
        prop.put("dynamic", (Integer) oldDoc.getProperty("dynamic") + 1);
        aUnsavedRev.setProperties(prop);
        final SavedRevision savedRev = aUnsavedRev.save();

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

        final String json = String.format("{\"%s\":{\"missing\":[\"%s\"],\"possible_ancestors\":[\"%s\",\"%s\"]}}", doc.getId(), savedRev.getId(), doc1Rev.getId(), doc2Rev.getId());
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
                HttpEntity entity = httpPut.getEntity();
                assertFalse("PUT request with updated doc properties contains attachment", entity instanceof MultipartEntity);
            }
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/188
     */
    public void testServerDoesNotSupportMultipart() throws Exception {

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
        int entityIndex = 0;
        for (HttpRequest httpRequest : captured) {
            // verify that there are no PUT requests with attachments
            if (httpRequest instanceof HttpPut) {
                HttpPut httpPut = (HttpPut) httpRequest;
                HttpEntity entity = httpPut.getEntity();
                if (entityIndex++ == 0) {
                    assertTrue("PUT request with attachment is not multipart", entity instanceof MultipartEntity);
                } else {
                    assertFalse("PUT request with attachment is multipart", entity instanceof MultipartEntity);
                }
            }
        }
    }

    public void testServerIsSyncGatewayVersion() throws Exception {
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
        Map<String, Object> filterParams = new HashMap<String, Object>();
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
        filterParams = new HashMap<String, Object>();
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
     * <p/>
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
                doc1Id, "1-a000", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(
                doc2Id, "1-b000", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(
                doc3Id, "1-c000", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        try {

            //add response to _local request
            // checkpoint GET response w/ 404
            MockResponse fakeCheckpointResponse = new MockResponse();
            MockHelper.set404NotFoundJson(fakeCheckpointResponse);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

            // Empty _all_docs response to pass unit tests
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_ALL_DOCS, new MockDocumentAllDocs());

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
        } finally {
            //stop mock server
            server.shutdown();
        }
    }

    /**
     * Reproduces https://github.com/couchbase/couchbase-lite-android/issues/167
     */
    public void testPushPurgedDoc() throws Throwable {

        int numBulkDocRequests = 0;
        HttpPost lastBulkDocsRequest = null;

        Map<String, Object> properties = new HashMap<String, Object>();
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
            if (capturedRequest instanceof HttpPost && ((HttpPost) capturedRequest).getURI().toString().endsWith("_bulk_docs")) {
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
        Thread.sleep(5 * 1000);

        // we should not have gotten any more _bulk_docs requests, because
        // the replicator should not have pushed anything else.
        // (in the case of the bug, it was trying to push the purged revision)
        numBulkDocRequests = 0;
        for (HttpRequest capturedRequest : mockHttpClient.getCapturedRequests()) {
            if (capturedRequest instanceof HttpPost && ((HttpPost) capturedRequest).getURI().toString().endsWith("_bulk_docs")) {
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
     * <p/>
     * - Add doc to (mock) sync gateway
     * - Add validation function that will reject that doc
     * - Do a pull replication
     * - Assert that the doc does _not_ make it into the db
     */
    public void testValidationBlockCalled() throws Throwable {

        final MockDocumentGet.MockDocument mockDocument = new MockDocumentGet.MockDocument("doc1", "1-3e28", 1);
        mockDocument.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {


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
        } finally {
            server.shutdown();
        }
    }

    /**
     * Attempting to reproduce couchtalk issue:
     * <p/>
     * https://github.com/couchbase/couchbase-lite-android/issues/312
     * <p/>
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
        try {
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
        } finally {
            // cleanup / shutdown
            server.shutdown();
        }
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

        Database db = startDatabase();
        URL fakeRemoteURL = new URL("http://couchbase.com/no_such_db");
        Replication r1 = db.createPullReplication(fakeRemoteURL);

        assertTrue(r1.getChannels().isEmpty());
        r1.setFilter("foo/bar");
        assertTrue(r1.getChannels().isEmpty());
        Map<String, Object> filterParams = new HashMap<String, Object>();
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
        filterParams = new HashMap<String, Object>();
        filterParams.put("channels", "NBC,MTV");
        assertEquals(filterParams, r1.getFilterParams());

        r1.setChannels(null);
        assertEquals(r1.getFilter(), null);
        assertEquals(null, r1.getFilterParams());
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
        try {
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

            if (expectReplicatorError == false) {

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
        } finally {
            // Shut down the server. Instances cannot be reused.
            server.shutdown();
        }
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
        try {
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
                    Log.i(Log.TAG_SYNC, "event.getCompletedChangeCount() = " + event.getCompletedChangeCount());
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
            boolean success = idleCountdownLatch.await(60, TimeUnit.SECONDS);
            assertTrue(success);

            // WORKAROUND: With CBL Java on Jenkins, Replicator becomes IDLE state before processing doc1. (NOT 100% REPRODUCIBLE)
            // NOTE: 03/20/2014 This is also observable with on Standard Android emulator with ARM. (NOT 100% REPRODUCIBLE)
            // TODO: Need to fix: https://github.com/couchbase/couchbase-lite-java-core/issues/446
            // NOTE: Build.BRAND.equalsIgnoreCase("generic") is only for Android, not for regular Java.
            //       So, till solve IDLE state issue, always wait 5 seconds.
            try {
                Thread.sleep(5 * 1000);
            } catch (Exception e) {
            }

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
            success = receivedAllDocs.await(60, TimeUnit.SECONDS);
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
        } finally {
            server.shutdown();
        }
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

        Log.d(Log.TAG, "/putReplicationOffline: %s", replication);
    }

    private void putReplicationOnline(Replication replication) throws InterruptedException {

        Log.d(Log.TAG, "putReplicationOnline: %s", replication);

        // this was a useless test, the replication wasn't even started
        final CountDownLatch wentOnline = new CountDownLatch(1);
        Replication.ChangeListener changeListener = new ReplicationRunningObserver(wentOnline);
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
        try {

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
            for (int i = 0; i < 500; i++) {  // TODO: use setSticky instead of workaround to add a ton of mock responses
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
        } finally {
            server.shutdown();
        }
    }

    /**
     * Test goOffline() method in the context of a continuous pusher.
     * <p/>
     * - 1. Add a local document
     * - 2. Kick off continuous push replication
     * - 3. Wait for document to be pushed
     * - 4. Call goOffline()
     * - 6. Call goOnline()
     * - 5. Add a 2nd local document
     * - 7. Wait for 2nd document to be pushed
     *
     * @throws Exception
     */
    public void testGoOfflinePusher() throws Exception {
        int previous = RemoteRequestRetry.RETRY_DELAY_MS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;

        try {
            // 1. Add a local document
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("testGoOfflinePusher", "1");
            Document doc1 = createDocumentWithProperties(database, properties);

            // create mock server
            MockWebServer server = new MockWebServer();
            try {
                MockDispatcher dispatcher = new MockDispatcher();
                server.setDispatcher(dispatcher);
                server.play();

                // checkpoint PUT response (sticky)
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

                // 2. Kick off continuous push replication
                Replication replicator = database.createPushReplication(server.getUrl("/db"));
                replicator.setContinuous(true);
                CountDownLatch replicationIdleSignal = new CountDownLatch(1);
                ReplicationIdleObserver replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
                replicator.addChangeListener(replicationIdleObserver);
                replicator.start();

                // 3. Wait for document to be pushed

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

                // make some assertions about the outgoing _bulk_docs requests for first doc
                RecordedRequest bulkDocsRequest1 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNotNull(bulkDocsRequest1);
                assertBulkDocJsonContainsDoc(bulkDocsRequest1, doc1);

                // 4. Call goOffline()
                putReplicationOffline(replicator);

                // 5. Add a 2nd local document
                properties = new HashMap<String, Object>();
                properties.put("testGoOfflinePusher", "2");
                Document doc2 = createDocumentWithProperties(database, properties);

                // make sure if push replicator does not send request during offline.
                try {
                    Thread.sleep(1000 * 3);
                } catch (Exception ex) {
                }
                // make sure not receive _bulk_docs during offline.
                RecordedRequest bulkDocsRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNull(bulkDocsRequest);

                // 6. Call goOnline()
                putReplicationOnline(replicator);

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

                // make some assertions about the outgoing _bulk_docs requests for second doc
                RecordedRequest bulkDocsRequest2 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNotNull(bulkDocsRequest2);
                assertBulkDocJsonContainsDoc(bulkDocsRequest2, doc2);

                // cleanup
                stopReplication(replicator);
            } finally {
                server.shutdown();
            }
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
        try {

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
            assertEquals(401 /* unauthorized */, ((HttpResponseException) pullReplication.getLastError()).getStatusCode());

            // assert that the replicator sent the requests we expected it to send
            RecordedRequest sessionReqeust = dispatcher.takeRequest(MockHelper.PATH_REGEX_SESSION);
            assertNotNull(sessionReqeust);
            RecordedRequest facebookRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_FACEBOOK_AUTH);
            assertNotNull(facebookRequest);
            dispatcher.verifyAllRecordedRequestsTaken();
        } finally {
            server.shutdown();
        }
    }


    public void testGetReplicatorWithCustomHeader() throws Throwable {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(500);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            server.play();

            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("source", DEFAULT_TEST_DB);

            // target with custom headers (cookie)
            Map<String, Object> headers = new HashMap<String, Object>();
            String coolieVal = "SyncGatewaySession=c38687c2696688a";
            headers.put("Cookie", coolieVal);

            Map<String, Object> targetProperties = new HashMap<String, Object>();
            targetProperties.put("url", server.getUrl("/db").toExternalForm());
            targetProperties.put("headers", headers);

            properties.put("target", targetProperties);

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
            replicator.addChangeListener(new ReplicationRunningObserver(replicationStarted));

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
        } finally {
            server.shutdown();
        }
    }

    public void testGetReplicator() throws Throwable {
        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(500);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            server.play();

            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("source", DEFAULT_TEST_DB);
            properties.put("target", server.getUrl("/db").toExternalForm());

            Replication replicator = manager.getReplicator(properties);
            assertNotNull(replicator);
            assertEquals(server.getUrl("/db").toExternalForm(), replicator.getRemoteUrl().toExternalForm());
            assertTrue(!replicator.isPull());
            assertFalse(replicator.isContinuous());
            assertFalse(replicator.isRunning());

            // add replication observer
            CountDownLatch replicationDoneSignal = new CountDownLatch(1);
            ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
            replicator.addChangeListener(replicationFinishedObserver);

            // start the replicator
            Log.d(TAG, "Starting replicator " + replicator);
            replicator.start();

            final CountDownLatch replicationStarted = new CountDownLatch(1);
            replicator.addChangeListener(new ReplicationRunningObserver(replicationStarted));

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
        } finally {
            server.shutdown();
        }
    }

    public void testGetReplicatorWithAuth() throws Throwable {

        Map<String, Object> authProperties = getReplicationAuthParsedJson();

        Map<String, Object> targetProperties = new HashMap<String, Object>();
        targetProperties.put("url", getReplicationURL().toExternalForm());
        targetProperties.put("auth", authProperties);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", targetProperties);

        Replication replicator = manager.getReplicator(properties);
        assertNotNull(replicator);
        assertNotNull(replicator.getAuthenticator());
        assertTrue(replicator.getAuthenticator() instanceof FacebookAuthorizer);
    }

    /**
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
        try {

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
            for (int i = 1; i <= 2; i++) {
                Log.v(TAG, "waiting for PUT checkpoint: %d", i);
                waitForPutCheckpointRequestWithSeq(dispatcher, mockDoc1.getDocSeq());
                Log.d(TAG, "got PUT checkpoint: %d", i);
            }

            stopReplication(pullReplication);
        } finally {
            server.shutdown();
        }
    }


    /**
     * Verify that Validation based Rejects revert the entire batch that the document is in
     * even if one of the documents fail the validation.
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/242
     *
     * @throws Exception
     */
    public void testVerifyPullerInsertsDocsWithValidation() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        // Empty _all_docs response to pass unit tests
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_ALL_DOCS, new MockDocumentAllDocs());
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, 2, 2);

        try {
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
        } finally {
            server.shutdown();
        }
    }

    /**
     * Make sure calling puller.setChannels() causes the changetracker to send the correct
     * request to the sync gateway.
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/292
     */
    public void testChannelsFilter() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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
            Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(body, Map.class);
            assertTrue(jsonMap.containsKey("filter"));
            String filter = (String) jsonMap.get("filter");
            assertEquals("sync_gateway/bychannel", filter);

            assertTrue(jsonMap.containsKey("channels"));
            String channels = (String) jsonMap.get("channels");
            assertTrue(channels.contains("foo"));
            assertTrue(channels.contains("bar"));
        } finally {
            server.shutdown();
        }
    }

    /**
     * - Start continuous pull
     * - Mockwebserver responds that there are no changes
     * - Assert that puller goes into IDLE state
     * <p/>
     * https://github.com/couchbase/couchbase-lite-android/issues/445
     */
    public void testContinuousPullEntersIdleState() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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
                    if (event.getTransition().getDestination() == ReplicationState.IDLE) {
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
        } finally {
            server.shutdown();
        }
    }

    /**
     * Spotted in https://github.com/couchbase/couchbase-lite-java-core/issues/313
     * But there is another ticket that is linked off 313
     */
    public void failingTestMockPullBulkDocsSyncGw() throws Exception {
        mockPullBulkDocs(MockDispatcher.ServerType.SYNC_GW);
    }

    public void mockPullBulkDocs(MockDispatcher.ServerType serverType) throws Exception {

        // set INBOX_CAPACITY to a smaller value so that processing times don't skew the test
        int defaultCapacity = ReplicationInternal.INBOX_CAPACITY;
        ReplicationInternal.INBOX_CAPACITY = 10;
        int defaultDelay = ReplicationInternal.PROCESSOR_DELAY;
        ReplicationInternal.PROCESSOR_DELAY = ReplicationInternal.PROCESSOR_DELAY * 10;

        // serve 25 mock docs
        int numMockDocsToServe = (ReplicationInternal.INBOX_CAPACITY * 2) + (ReplicationInternal.INBOX_CAPACITY / 2);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(serverType);
        try {

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
            runReplication(pullReplication, 3 * 60);
            assertTrue(pullReplication.getLastError() == null);

            // wait until it pushes checkpoint of last doc
            MockDocumentGet.MockDocument lastDoc = mockDocs.get(mockDocs.size() - 1);
            waitForPutCheckpointRequestWithSequence(dispatcher, lastDoc.getDocSeq());

            // dump out the outgoing requests for bulk docs
            BlockingQueue<RecordedRequest> bulkGetRequests = dispatcher.getRequestQueueSnapshot(MockHelper.PATH_REGEX_BULK_GET);
            Iterator<RecordedRequest> iterator = bulkGetRequests.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                RecordedRequest request = iterator.next();
                byte[] body = MockHelper.getUncompressedBody(request);
                Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(body);
                List docs = (List) jsonMap.get("docs");
                Log.w(TAG, "bulk get request: %s had %d docs", request, docs.size());
                // except first one and last one, docs.size() should be (neary) equal with INBOX_CAPACTITY.
                if (iterator.hasNext() && !first) {
                    // the bulk docs requests except for the last one should have max number of docs
                    // relax this a bit, so that it at least has to have greater than or equal to half max number of docs
                    assertTrue(docs.size() >= (ReplicationInternal.INBOX_CAPACITY / 2));
                    if (docs.size() != ReplicationInternal.INBOX_CAPACITY) {
                        Log.w(TAG, "docs.size() %d != ReplicationInternal.INBOX_CAPACITY %d", docs.size(), ReplicationInternal.INBOX_CAPACITY);
                    }
                }
                first = false;
            }
        } finally {
            ReplicationInternal.INBOX_CAPACITY = defaultCapacity;
            ReplicationInternal.PROCESSOR_DELAY = defaultDelay;
            server.shutdown();
        }
    }

    /**
     * Make sure that after trying /db/_session, it should try /_session.
     * <p/>
     * Currently there is a bug where it tries /db/_session, and then
     * tries /db_session.
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/208
     */
    public void testCheckSessionAtPath() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        try {

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
        } finally {
            server.shutdown();
        }
    }

    /**
     * - Start one shot replication
     * - Changes feed request returns error
     * - Change tracker stops
     * - Replication stops -- make sure ChangeListener gets error
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/334
     */
    public void testChangeTrackerError() throws Exception {

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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
        } finally {
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/358
     *
     * @related: https://github.com/couchbase/couchbase-lite-java-core/issues/55
     * related: testContinuousPushReplicationGoesIdle()
     * <p/>
     * test steps:
     * - start replicator
     * - make sure replicator becomes idle state
     * - add N docs
     * - when callback state == idle
     * - assert that mock has received N docs
     */
    public void testContinuousPushReplicationGoesIdleTwice() throws Exception {

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
        try {

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
            class ReplicationTransitionToIdleObserver implements Replication.ChangeListener {
                private CountDownLatch doneSignal;
                private CountDownLatch checkSignal;

                public ReplicationTransitionToIdleObserver(CountDownLatch doneSignal, CountDownLatch checkSignal) {
                    this.doneSignal = doneSignal;
                    this.checkSignal = checkSignal;
                }

                public void changed(Replication.ChangeEvent event) {
                    Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] event => " + event.toString());
                    if (event.getTransition() != null) {
                        if (event.getTransition().getSource() != event.getTransition().getDestination() &&
                                event.getTransition().getDestination() == ReplicationState.IDLE) {
                            Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Transition to  IDLE");
                            Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Request Count => " + server.getRequestCount());

                            this.doneSignal.countDown();

                            // When replicator becomes IDLE state, check if all requests are completed
                            // assertEquals in inner class does not work....
                            // Note: sometimes server.getRequestCount() returns expected number - 1.
                            //       Is it timing issue?
                            if (EXPECTED_REQUEST_COUNT == server.getRequestCount() ||
                                    EXPECTED_REQUEST_COUNT - 1 == server.getRequestCount()) {
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
            for (int i = 1; i <= 1; i++) {
                Map<String, Object> properties1 = new HashMap<String, Object>();
                properties1.put("doc" + String.valueOf(i), "testContinuousPushReplicationGoesIdleTooSoon " + String.valueOf(i));
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
            // _bulk_docs
            RecordedRequest request3 = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(request3);
            dispatcher.takeRecordedResponseBlocking(request3);

            // double check total request
            Log.w(Log.TAG_SYNC, "Total Requested Count before stop replicator => " + server.getRequestCount());
            assertTrue(EXPECTED_REQUEST_COUNT == server.getRequestCount() ||
                    EXPECTED_REQUEST_COUNT - 1 == server.getRequestCount());

            // 9. Stop replicator
            replication.removeChangeListener(replicationTransitionToIdleObserver);
            stopReplication(replication);
        } finally {
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/358
     * <p/>
     * related: testContinuousPushReplicationGoesIdleTooSoon()
     * testContinuousPushReplicationGoesIdle()
     * <p/>
     * test steps:
     * - add N docs
     * - start replicator
     * - when callback state == idle
     * - assert that mock has received N docs
     */
    public void failingTestContinuousPushReplicationGoesIdleTooSoon() throws Exception {

        // smaller batch size so there are multiple requests to _bulk_docs
        int previous = ReplicationInternal.INBOX_CAPACITY;
        ReplicationInternal.INBOX_CAPACITY = 5;
        int numDocs = ReplicationInternal.INBOX_CAPACITY * 5;

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // Add doc(s)
        // NOTE: more documents causes more HTTP calls. It could be more than 4 times...
        for (int i = 1; i <= numDocs; i++) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("doc" + String.valueOf(i), "testContinuousPushReplicationGoesIdleTooSoon " + String.valueOf(i));
            final Document doc = createDocWithProperties(properties);
        }

        // Setup MockWebServer
        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        final MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
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
            class ReplicationTransitionToIdleObserver implements Replication.ChangeListener {
                private CountDownLatch enterIdleStateSignal;

                public ReplicationTransitionToIdleObserver(CountDownLatch enterIdleStateSignal) {
                    this.enterIdleStateSignal = enterIdleStateSignal;
                }

                public void changed(Replication.ChangeEvent event) {
                    Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] event => " + event.toString());
                    if (event.getTransition() != null) {
                        if (event.getTransition().getSource() != event.getTransition().getDestination() &&
                                event.getTransition().getDestination() == ReplicationState.IDLE) {
                            Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Transition to  IDLE");
                            Log.w(Log.TAG_SYNC, "[ChangeListener.changed()] Request Count => " + server.getRequestCount());

                            this.enterIdleStateSignal.countDown();
                        }
                    }
                }
            }
            CountDownLatch enterIdleStateSignal = new CountDownLatch(1);
            ReplicationTransitionToIdleObserver replicationTransitionToIdleObserver = new ReplicationTransitionToIdleObserver(enterIdleStateSignal);
            replication.addChangeListener(replicationTransitionToIdleObserver);
            replication.start();

            // Wait until idle (make sure replicator becomes IDLE state from other state)
            boolean success = enterIdleStateSignal.await(20, TimeUnit.SECONDS);
            assertTrue(success);

            // Once the replicator is idle get a snapshot of all the requests its made to _bulk_docs endpoint
            int numDocsPushed = 0;
            BlockingQueue<RecordedRequest> requests = dispatcher.getRequestQueueSnapshot(MockHelper.PATH_REGEX_BULK_DOCS);
            for (RecordedRequest request : requests) {
                Log.i(Log.TAG_SYNC, "request: %s", request);
                byte[] body = MockHelper.getUncompressedBody(request);
                Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(body);
                List docs = (List) jsonMap.get("docs");
                numDocsPushed += docs.size();
            }

            // WORKAROUND: CBL Java Unit Test on Jenkins rarely fails following.
            // TODO: Need to fix: https://github.com/couchbase/couchbase-lite-java-core/issues/446
            // It seems threading issue exists, and replicator becomes IDLE even tasks in batcher.
            if (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {
                // Assert that all docs have already been pushed by the time it goes IDLE
                assertEquals(numDocs, numDocsPushed);
            }

            // Stop replicator and MockWebServer
            stopReplication(replication);

            // wait until checkpoint is pushed, since it can happen _after_ replication is finished.
            // if this isn't done, there can be IOExceptions when calling server.shutdown()
            waitForPutCheckpointRequestWithSeq(dispatcher, (int) database.getLastSequenceNumber());

        } finally {
            server.shutdown();
            ReplicationInternal.INBOX_CAPACITY = previous;
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/352
     * <p/>
     * When retrying a replication, make sure to get session & checkpoint.
     */
    public void testCheckSessionAndCheckpointWhenRetryingReplication() throws Exception {
        final int VALIDATION_RETRIES = 3;
        final int TEMP_RETRY_DELAY_MS = RemoteRequestRetry.RETRY_DELAY_MS;
        final int TEMP_RETRY_DELAY_SECONDS = ReplicationInternal.RETRY_DELAY_SECONDS;
        try {
            RemoteRequestRetry.RETRY_DELAY_MS = 5;       // speed up test execution (inner loop retry delay)
            ReplicationInternal.RETRY_DELAY_SECONDS = 1; // speed up test execution (outer loop retry delay)

            String fakeEmail = "myfacebook@gmail.com";

            // create mockwebserver and custom dispatcher
            MockDispatcher dispatcher = new MockDispatcher();
            MockWebServer server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            try {

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
                for (int j = 0; j < VALIDATION_RETRIES; j++) {

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
            } finally {
                server.shutdown();
            }
        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = TEMP_RETRY_DELAY_MS;
            ReplicationInternal.RETRY_DELAY_SECONDS = TEMP_RETRY_DELAY_SECONDS;
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/352
     * <p/>
     * Makes the replicator stop, even if it is continuous, when it receives a permanent-type error
     */
    public void failingTestStopReplicatorWhenRetryingReplicationWithPermanentError() throws Exception {
        final int TEMP_RETRY_DELAY_MS = RemoteRequestRetry.RETRY_DELAY_MS;
        final int TEMP_RETRY_DELAY_SECONDS = ReplicationInternal.RETRY_DELAY_SECONDS;
        RemoteRequestRetry.RETRY_DELAY_MS = 5;       // speed up test execution (inner loop retry delay)
        ReplicationInternal.RETRY_DELAY_SECONDS = 1; // speed up test execution (outer loop retry delay)
        try {
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
        } finally {
            RemoteRequestRetry.RETRY_DELAY_MS = TEMP_RETRY_DELAY_MS;
            ReplicationInternal.RETRY_DELAY_SECONDS = TEMP_RETRY_DELAY_SECONDS;
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/356
     */
    public void testReplicationRestartPreservesValues() throws Exception {

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // add docs
        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("doc1", "testContinuousPushReplicationGoesIdle");
        final Document doc1 = createDocWithProperties(properties1);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
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

            // create continuos replication
            Replication pusher = database.createPushReplication(server.getUrl("/db"));
            pusher.setContinuous(true);

            // add filter properties to the replicator
            String filterName = "app/clientIdAndTablesSchemeDocIdFilter";
            pusher.setFilter(filterName);
            Map<String, Object> filterParams = new HashMap<String, Object>();
            String filterParam = "tablesSchemeDocId";
            String filterVal = "foo";
            filterParams.put(filterParam, filterVal);
            pusher.setFilterParams(filterParams);

            // doc ids
            pusher.setDocIds(Arrays.asList(doc1.getId()));

            // custom authenticator
            BasicAuthenticator authenticator = new BasicAuthenticator("foo", "bar");
            pusher.setAuthenticator(authenticator);

            // custom request headers
            Map<String, Object> requestHeaders = new HashMap<String, Object>();
            requestHeaders.put("foo", "bar");
            pusher.setHeaders(requestHeaders);

            // create target
            pusher.setCreateTarget(true);

            // start the continuous replication
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            ReplicationIdleObserver replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
            pusher.addChangeListener(replicationIdleObserver);
            pusher.start();

            // wait until we get an IDLE event
            boolean successful = replicationIdleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(successful);

            // restart the replication
            CountDownLatch replicationIdleSignal2 = new CountDownLatch(1);
            ReplicationIdleObserver replicationIdleObserver2 = new ReplicationIdleObserver(replicationIdleSignal2);
            pusher.addChangeListener(replicationIdleObserver2);
            pusher.restart();

            // wait until we get another IDLE event
            successful = replicationIdleSignal2.await(30, TimeUnit.SECONDS);
            assertTrue(successful);

            // verify the restarted replication still has the values we set up earlier
            assertEquals(filterName, pusher.getFilter());
            assertTrue(pusher.getFilterParams().size() == 1);
            assertEquals(filterVal, pusher.getFilterParams().get(filterParam));
            assertTrue(pusher.isContinuous());
            assertEquals(Arrays.asList(doc1.getId()), pusher.getDocIds());
            assertEquals(authenticator, pusher.getAuthenticator());
            assertEquals(requestHeaders, pusher.getHeaders());
            assertTrue(pusher.shouldCreateTarget());
        } finally {
            server.shutdown();
        }
    }

    /**
     * The observed problem:
     * <p/>
     * - 1. Start continuous pull
     * - 2. Wait until it goes IDLE (this works fine)
     * - 3. Add a new document directly to the Sync Gateway
     * - 4. The continuous pull goes from IDLE -> RUNNING
     * - 5. Wait until it goes IDLE again (this doesn't work, it never goes back to IDLE)
     * <p/>
     * The test case below simulates the above scenario using a mock sync gateway.
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/383
     */
    public void testContinuousPullReplicationGoesIdleTwice() throws Exception {
        Log.d(TAG, "TEST START");

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // add non-sticky changes response that returns no changes
            // this will cause the pull replicator to go into the IDLE state
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            // add _changes response that just blocks for a few seconds to emulate
            // server that doesn't have any new changes.  while the puller is blocked on this request
            // to the _changes feed, the test will add a new changes listener that waits until it goes
            // into the RUNNING state
            MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
            // It seems 5 sec delay might not be necessary. It reduce test duration 5 sec
            //mockChangesFeedNoResponse.setDelayMs(5 * 1000);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

            // 3.
            // after the above changes feed response returns after 5 seconds, the next time
            // the puller gets the _changes feed, return a response that there is 1 new doc.
            // this will cause the puller to go from IDLE -> RUNNING
            MockDocumentGet.MockDocument mockDoc1 = new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
            mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());
            mockChangesFeed = new MockChangesFeed();
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            // at this point, the mock _changes feed is done simulating new docs on the sync gateway
            // since we've done enough to reproduce the problem.  so at this point, just make the changes
            // feed block for a long time.
            MockChangesFeedNoResponse mockChangesFeedNoResponse2 = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse2.setDelayMs(6000 * 1000);  // block for > 1hr
            mockChangesFeedNoResponse2.setSticky(true);  // continue this behavior indefinitely
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
            final Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);

            final CountDownLatch enteredIdleState1 = new CountDownLatch(1);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.IDLE) {
                        Log.d(TAG, "Replication is IDLE 1");
                        enteredIdleState1.countDown();
                    }
                }
            });

            // 1. start pull replication
            pullReplication.start();

            // 2. wait until its IDLE
            boolean success = enteredIdleState1.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            // 3. see server side preparation

            // change listener to see if its RUNNING
            // we can't add this earlier, because the countdown latch would get
            // triggered too early (the other approach would be to set the countdown
            // latch to a higher number)
            final CountDownLatch enteredRunningState = new CountDownLatch(1);
            final CountDownLatch enteredIdleState2 = new CountDownLatch(1);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.RUNNING) {
                        if (enteredRunningState.getCount() > 0) {
                            Log.d(TAG, "Replication is RUNNING");
                            enteredRunningState.countDown();
                        }
                    }
                    // second IDLE change listener
                    // handling IDLE event here. It seems IDLE event was fired before set IDLE event handler
                    else if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.IDLE) {
                        if (enteredRunningState.getCount() <= 0 && enteredIdleState2.getCount() > 0) {
                            Log.d(TAG, "Replication is IDLE 2");
                            enteredIdleState2.countDown();
                        }
                    }
                }
            });

            // 4. wait until its RUNNING
            Log.d(TAG, "WAIT for RUNNING");
            success = enteredRunningState.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            // 5. wait until its IDLE again.  before the fix, it would never go IDLE again, and so
            // this would timeout and the test would fail.
            Log.d(TAG, "WAIT for IDLE");
            success = enteredIdleState2.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            Log.d(TAG, "STOP REPLICATOR");

            // clean up
            stopReplication(pullReplication);

            Log.d(TAG, "STOP MOCK SERVER");
        } finally {
            server.shutdown();
        }


        Log.d(TAG, "TEST DONE");
    }

    /**
     * Test case that makes sure STOPPED notification is sent only once with continuous pull replication
     * https://github.com/couchbase/couchbase-lite-android/issues/442
     */
    public void testContinuousPullReplicationSendStoppedOnce() throws Exception {
        Log.d(TAG, "TEST START");

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // add non-sticky changes response that returns no changes
            // this will cause the pull replicator to go into the IDLE state
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            server.play();

            // create pull replication
            Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);

            final CountDownLatch enteredIdleState = new CountDownLatch(1);
            final CountDownLatch enteredStoppedState = new CountDownLatch(2);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getTransition().getDestination() == ReplicationState.IDLE) {
                        Log.d(TAG, "Replication is IDLE");
                        enteredIdleState.countDown();
                    } else if (event.getTransition().getDestination() == ReplicationState.STOPPED) {
                        event.getTransition().getDestination();
                        Log.d(TAG, "Replication is STOPPED");
                        enteredStoppedState.countDown();
                    }
                }
            });

            // 1. start pull replication
            pullReplication.start();

            // 2. wait until its IDLE
            boolean success = enteredIdleState.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            // 3. stop pull replication
            stopReplication(pullReplication);

            // 4. wait until its RUNNING
            Log.d(TAG, "WAIT for STOPPED");
            //success = enteredStoppedState.await(Replication.DEFAULT_MAX_TIMEOUT_FOR_SHUTDOWN + 30, TimeUnit.SECONDS); // replicator maximum shutdown timeout 60 sec + additional 30 sec for other stuff
            // NOTE: 90 sec is too long for unit test. chnaged to 30 sec
            // NOTE2: 30 sec is still too long for unit test. changed to 15sec.
            success = enteredStoppedState.await(15, TimeUnit.SECONDS); // replicator maximum shutdown timeout 60 sec + additional 30 sec for other stuff
            // if STOPPED notification was sent twice, enteredStoppedState becomes 0.
            assertEquals(1, enteredStoppedState.getCount());
            assertFalse(success);
        } finally {
            Log.d(TAG, "STOP MOCK SERVER");
            server.shutdown();
        }

        Log.d(TAG, "TEST DONE");
    }

    /**
     * Test case that makes sure STOPPED notification is sent only once with one time pull replication
     * https://github.com/couchbase/couchbase-lite-android/issues/442
     */
    public void testOneTimePullReplicationSendStoppedOnce() throws Exception {
        Log.d(TAG, "TEST START");

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // add non-sticky changes response that returns no changes
            // this will cause the pull replicator to go into the IDLE state
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            server.play();

            // create pull replication
            Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(false);

            // handle STOPPED notification
            final CountDownLatch enteredStoppedState = new CountDownLatch(2);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getTransition().getDestination() == ReplicationState.STOPPED) {
                        Log.d(TAG, "Replication is STOPPED");
                        enteredStoppedState.countDown();
                    }
                }
            });

            // 1. start pull replication
            pullReplication.start();

            // 2. wait until its RUNNING
            Log.d(TAG, "WAIT for STOPPED");
            boolean success = enteredStoppedState.await(15, TimeUnit.SECONDS);
            // if STOPPED notification was sent twice, enteredStoppedState becomes 0.
            assertEquals(1, enteredStoppedState.getCount());
            assertFalse(success);
        } finally {
            Log.d(TAG, "STOP MOCK SERVER");
            server.shutdown();
        }

        Log.d(TAG, "TEST DONE");
    }

    /**
     * Issue:  Pull Replicator does not send IDLE state after check point
     * https://github.com/couchbase/couchbase-lite-java-core/issues/389
     * <p/>
     * 1. Wait till pull replicator becomes IDLE state
     * 2. Update change event handler for handling ACTIVE and IDLE
     * 3. Create document into local db
     * 4. Based on local doc information, prepare mock change response for 1st /_changes request
     * 5. Prepare next mock change response for 2nd /_changes request (blocking for while)
     * 6. wait for Replication IDLE -> ACTIVE -> IDLE
     */
    public void testPullReplicatonSendIdleStateAfterCheckPoint() throws Exception {
        Log.d(TAG, "TEST START");

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // checkpoint PUT or GET response (sticky) (for both push and pull)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // add non-sticky changes response that returns no changes  (for pull)
            // this will cause the pull replicator to go into the IDLE state
            MockChangesFeed mockChangesFeedEmpty = new MockChangesFeed();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedEmpty.generateMockResponse());

            // start mock server
            server.play();

            // create pull replication
            Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);

            // handler to wait for IDLE
            final CountDownLatch pullInitialIdleState = new CountDownLatch(1);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.IDLE) {
                        pullInitialIdleState.countDown();
                    }
                }
            });

            // start pull replication
            //pushReplication.start();
            pullReplication.start();

            // 1. Wait till replicator becomes IDLE
            boolean success = pullInitialIdleState.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            // clear out existing queued mock responses to make room for new ones
            dispatcher.clearQueuedResponse(MockHelper.PATH_REGEX_CHANGES);


            // 2. Update change event handler for handling ACTIVE and IDLE
            final CountDownLatch runningSignal = new CountDownLatch(1);
            final CountDownLatch idleSignal = new CountDownLatch(1);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    Log.i(TAG, "[changed] PULL -> " + event);
                    if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.IDLE) {
                        // make sure pull replicator becomes IDLE after ACTIVE state.
                        // so ignore any IDLE state before ACTIVE.
                        if (runningSignal.getCount() == 0) {
                            idleSignal.countDown();
                        }
                    } else if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.RUNNING) {
                        runningSignal.countDown();
                    }
                }
            });

            // 3. Create document into local db
            Document doc = database.createDocument();
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("key", "1");
            doc.putProperties(props);

            // 4. Based on local doc information, prepare mock change response for 1st /_changes request
            String docId = doc.getId();
            String revId = doc.getCurrentRevisionId();
            int lastSeq = (int) database.getLastSequenceNumber();

            MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(docId, revId, lastSeq + 1);
            mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument1));
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            // 5. Prepare next mock change response for 2nd /_changes request (blocking for while)
            MockChangesFeedNoResponse mockChangesFeedNoResponse2 = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse2.setDelayMs(60 * 1000);
            mockChangesFeedNoResponse2.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse2);

            // 6. wait for Replication IDLE -> RUNNING -> IDLE
            success = runningSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);
            success = idleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            // stop pull replication
            stopReplication(pullReplication);
        } finally {
            server.shutdown();
        }

        Log.d(TAG, "TEST DONE");
    }

    /**
     * Sync (pull replication) fails on document with a lot of revisions and attachments
     * https://github.com/couchbase/couchbase-lite-java-core/issues/415
     */
    public void testPullReplicatonWithManyAttachmentRevisions() throws Exception {
        Log.d(TAG, "TEST START: testPullReplicatonWithManyAttachmentRevisions()");

        String docID = "11111";
        String key = "key";
        String value = "one-one-one-one";
        String attachmentName = "attachment.png";

        // create initial document (Revision 1-xxxx)
        Map<String, Object> props1 = new HashMap<String, Object>();
        props1.put("_id", docID);
        props1.put(key, value);
        RevisionInternal rev = new RevisionInternal(props1);
        Status status = new Status();
        RevisionInternal savedRev = database.putRevision(rev, null, false, status);
        String rev1ID = savedRev.getRevID();

        // add attachment to doc (Revision 2-xxxx)
        Document doc = database.getDocument(docID);
        UnsavedRevision newRev = doc.createRevision();
        InputStream attachmentStream = getAsset(attachmentName);
        newRev.setAttachment(attachmentName, "image/png", attachmentStream);
        SavedRevision saved = newRev.save(true);
        String rev2ID = doc.getCurrentRevisionId();

        // Create 5 revisions with 50 conflicts each
        int j = 3;
        for (; j < 5; j++) {
            // Create a conflict, won by the new revision:
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("_id", docID);
            props.put("_rev", j + "-0000");
            props.put(key, value);
            RevisionInternal leaf = new RevisionInternal(props);
            database.forceInsert(leaf, new ArrayList<String>(), null);

            for (int i = 0; i < 49; i++) {
                // Create a conflict, won by the new revision:
                Map<String, Object> props_conflict = new HashMap<String, Object>();
                props_conflict.put("_id", docID);
                String revStr = String.format("%d-%04d", j, i);
                props_conflict.put("_rev", revStr);
                props_conflict.put(key, value);
                // attachment
                byte[] attach1 = "This is the body of attach1".getBytes();
                String base64 = Base64.encodeBytes(attach1);
                Map<String, Object> attachment = new HashMap<String, Object>();
                attachment.put("content_type", "text/plain");
                attachment.put("data", base64);
                Map<String, Object> attachmentDict = new HashMap<String, Object>();
                attachmentDict.put("test_attachment", attachment);
                props_conflict.put("_attachments", attachmentDict);
                // end of attachment
                RevisionInternal leaf_conflict = new RevisionInternal(props_conflict);
                List<String> revHistory = new ArrayList<String>();
                revHistory.add(leaf_conflict.getRevID());
                for (int k = j - 1; k > 2; k--) {
                    revHistory.add(String.format("%d-0000", k));
                }
                revHistory.add(rev2ID);
                revHistory.add(rev1ID);
                database.forceInsert(leaf_conflict, revHistory, null);
            }
        }

        String docId = doc.getId();
        String revId = j + "-00";
        int lastSeq = (int) database.getLastSequenceNumber();


        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // checkpoint PUT or GET response (sticky) (for both push and pull)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            MockChangesFeed mockChangesFeedEmpty = new MockChangesFeed();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedEmpty.generateMockResponse());

            // start mock server
            server.play();

            // create pull replication
            Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);

            final CountDownLatch idleSignal1 = new CountDownLatch(1);
            final CountDownLatch idleSignal2 = new CountDownLatch(2);
            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    Log.i(TAG, event.toString());
                    if (event.getError() != null) {
                        Assert.fail("Should not have any error....");
                    }
                    if (event.getTransition() != null &&
                            event.getTransition().getDestination() == ReplicationState.IDLE) {
                        idleSignal1.countDown();
                        idleSignal2.countDown();
                    }
                }
            });

            // start pull replication
            pullReplication.start();

            boolean success = idleSignal1.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            //
            MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(docId, revId, lastSeq + 1);
            mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());

            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument1));
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            // doc response
            MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDocument1);
            dispatcher.enqueueResponse(mockDocument1.getDocPathRegex(), mockDocumentGet.generateMockResponse());

            // check /db/docid?...
            RecordedRequest request = dispatcher.takeRequestBlocking(mockDocument1.getDocPathRegex(), 30 * 1000);
            Log.i(TAG, request.toString());
            Map<String, String> queries = query2map(request.getPath());
            String atts_since = URLDecoder.decode(queries.get("atts_since"), "UTF-8");
            List<String> json = (List<String>) str2json(atts_since);
            Log.i(TAG, json.toString());
            assertNotNull(json);
            // atts_since parameter should be limit to PullerInternal.MAX_NUMBER_OF_ATTS_SINCE
            assertTrue(json.size() == PullerInternal.MAX_NUMBER_OF_ATTS_SINCE);

            boolean success2 = idleSignal2.await(30, TimeUnit.SECONDS);
            assertTrue(success2);

            // stop pull replication
            stopReplication(pullReplication);
        } finally {
            server.shutdown();
        }

        Log.d(TAG, "TEST END: testPullReplicatonWithManyAttachmentRevisions()");
    }

    public static Object str2json(String value) {
        Object result = null;
        try {
            result = Manager.getObjectMapper().readValue(value, Object.class);
        } catch (Exception e) {
            Log.w("Unable to parse JSON Query", e);
        }
        return result;
    }

    public static Map<String, String> query2map(String queryString) {
        Map<String, String> queries = new HashMap<String, String>();
        for (String component : queryString.split("&")) {
            int location = component.indexOf('=');
            if (location > 0) {
                String key = component.substring(0, location);
                String value = component.substring(location + 1);
                queries.put(key, value);
            }
        }
        return queries;
    }

    class CustomMultipartReaderDelegate implements MultipartReaderDelegate {
        public Map<String, String> headers = null;
        public byte[] data = null;
        public boolean gzipped = false;
        public boolean bJson = false;

        @Override
        public void startedPart(Map<String, String> headers) {
            gzipped = headers.get("Content-Encoding") != null && headers.get("Content-Encoding").contains("gzip");
            bJson = headers.get("Content-Type") != null && headers.get("Content-Type").contains("application/json");
        }

        @Override
        public void appendToPart(byte[] data) {
            if (gzipped && bJson) {
                this.data = Utils.decompressByGzip(data);
            } else if (bJson) {
                this.data = data;
            }
        }

        @Override
        public void appendToPart(final byte[] data, int off, int len) {
            byte[] b = Arrays.copyOfRange(data, off, len - off);
            appendToPart(b);
        }

        @Override
        public void finishedPart() {
        }
    }

    /**
     * Push Replication, never receive REPLICATION_ACTIVE status
     * https://github.com/couchbase/couchbase-lite-android/issues/451
     */
    public void testPushReplActiveState() throws Exception {
        Log.d(TAG, "TEST START: testPushReplActiveState()");

        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
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

            //
            Replication pullReplication = database.createPushReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);
            final String checkpointId = pullReplication.remoteCheckpointDocID();  // save the checkpoint id for later usage

            // Event handler for IDLE
            CountDownLatch idleSignal = new CountDownLatch(1);
            ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(idleSignal);
            pullReplication.addChangeListener(idleObserver);

            // start the continuous replication
            pullReplication.start();

            // wait until we get an IDLE event
            boolean successful = idleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(successful);
            pullReplication.removeChangeListener(idleObserver);

            // Event handler for ACTIVE
            CountDownLatch activeSignal = new CountDownLatch(1);
            ReplicationRunningObserver activeObserver = new ReplicationRunningObserver(activeSignal);
            pullReplication.addChangeListener(activeObserver);

            // Event handler for IDLE2
            CountDownLatch idleSignal2 = new CountDownLatch(1);
            ReplicationIdleObserver idleObserver2 = new ReplicationIdleObserver(idleSignal2);
            pullReplication.addChangeListener(idleObserver2);

            // add docs
            Map<String, Object> properties1 = new HashMap<String, Object>();
            properties1.put("doc1", "testPushReplActiveState");
            final Document doc1 = createDocWithProperties(properties1);

            // wait until we get an ACTIVE event
            successful = activeSignal.await(30, TimeUnit.SECONDS);
            assertTrue(successful);
            pullReplication.removeChangeListener(activeObserver);

            // check _bulk_docs
            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(request);
            assertTrue(MockHelper.getUtf8Body(request).contains("testPushReplActiveState"));

            // wait until we get an IDLE event
            successful = idleSignal2.await(30, TimeUnit.SECONDS);
            assertTrue(successful);
            pullReplication.removeChangeListener(idleObserver2);

            // stop pull replication
            stopReplication(pullReplication);
        } finally {
            server.shutdown();
        }

        Log.d(TAG, "TEST END: testPushReplActiveState()");
    }

    /**
     * Error after close DB client
     * https://github.com/couchbase/couchbase-lite-java/issues/52
     */
    public void testStop() throws Exception {
        Log.d(Log.TAG, "START testStop()");

        boolean success = false;

        // create mock server
        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        try {
            server.play();

            // checkpoint PUT or GET response (sticky) (for both push and pull)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // create pull replication & start it
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);
            final CountDownLatch pullIdleState = new CountDownLatch(1);
            ReplicationIdleObserver pullIdleObserver = new ReplicationIdleObserver(pullIdleState);
            pull.addChangeListener(pullIdleObserver);
            pull.start();

            // create push replication & start it
            Replication push = database.createPullReplication(server.getUrl("/db"));
            push.setContinuous(true);
            final CountDownLatch pushIdleState = new CountDownLatch(1);
            ReplicationIdleObserver pushIdleObserver = new ReplicationIdleObserver(pushIdleState);
            push.addChangeListener(pushIdleObserver);
            push.start();

            // wait till both push and pull replicators become idle.
            success = pullIdleState.await(30, TimeUnit.SECONDS);
            assertTrue(success);
            pull.removeChangeListener(pullIdleObserver);
            success = pushIdleState.await(30, TimeUnit.SECONDS);
            assertTrue(success);
            push.removeChangeListener(pushIdleObserver);

            // stop both pull and push replicators
            stopReplication(pull);
            stopReplication(push);

            boolean observedCBLRequestWorker = false;

            // First give 5 sec to clean thread status.
            try {
                Thread.sleep(5 * 1000);
            } catch (Exception e) {
            }
            // all threads which are associated with replicators should be terminated.
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread t : threadSet) {
                if (t.isAlive()) {
                    observedCBLRequestWorker = true;
                    if (t.getName().indexOf("CBLRequestWorker") != -1) {
                        observedCBLRequestWorker = true;
                        break;
                    }
                }
            }

            // second attemtpt, if still observe CBLRequestWorker thread, makes error
            if (observedCBLRequestWorker) {
                // give 10 sec to clean thread status.
                try {
                    Thread.sleep(10 * 1000);
                } catch (Exception e) {
                }

                // all threads which are associated with replicators should be terminated.
                Set<Thread> threadSet2 = Thread.getAllStackTraces().keySet();
                for (Thread t : threadSet2) {
                    if (t.isAlive()) {
                        assertEquals(-1, t.getName().indexOf("CBLRequestWorker"));
                    }
                }
            }
        } finally {
            // shutdown mock server
            server.shutdown();
        }

        Log.d(Log.TAG, "END testStop()");
    }

    /**
     * http://developer.couchbase.com/mobile/develop/references/couchbase-lite/couchbase-lite/replication/replication/index.html#mapstring-string-filterparams--get-set-
     * <p/>
     * Params passed in filtered push throw a null exception in the filter function
     * https://github.com/couchbase/couchbase-lite-java-core/issues/533
     */
    public void testSetFilterParams() throws CouchbaseLiteException, IOException, InterruptedException {
        // make sure we are starting empty
        assertEquals(0, database.getLastSequenceNumber());


        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
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


            // create 10 documents and delete 5
            for (int i = 0; i < 10; i++) {
                Document doc = null;
                if (i % 2 == 0) {
                    doc = createDocument(i, true);
                } else {
                    doc = createDocument(i, false);
                }
                if (i % 2 == 0) {
                    try {
                        doc.delete();
                    } catch (CouchbaseLiteException e) {
                        e.printStackTrace();
                    }
                }
            }

            final CountDownLatch latch = new CountDownLatch(10);
            final CountDownLatch check = new CountDownLatch(10);
            database.setFilter("unDeleted", new ReplicationFilter() {
                @Override
                public boolean filter(SavedRevision savedRevision, Map<String, Object> params) {
                    if (params == null || !"hello".equals(params.get("name"))) {
                        check.countDown();
                    }
                    latch.countDown();
                    return !savedRevision.isDeletion();
                }
            });

            Replication pushReplication = database.createPushReplication(server.getUrl("/db"));
            pushReplication.setContinuous(false);
            pushReplication.setFilter("unDeleted");
            pushReplication.setFilterParams(Collections.<String, Object>singletonMap("name", "hello"));
            pushReplication.start();

            boolean success = latch.await(30, TimeUnit.SECONDS);
            assertTrue(success);
            assertEquals(10, check.getCount());
        } finally {
            server.shutdown();
        }
    }

    private Document createDocument(int number, boolean flag) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Calendar calendar = GregorianCalendar.getInstance();
        String currentTimeString = dateFormatter.format(calendar.getTime());

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("type", "test_doc");
        properties.put("created_at", currentTimeString);
        if (flag == true) {
            properties.put("name", "Waldo");
        }
        Document document = database.getDocument(String.valueOf(number));
        try {
            document.putProperties(properties);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        return document;
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/575
     */
    public void testRestartWithStoppedReplicator() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, 0, 0);
        try {
            server.play();


            // run pull replication
            Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
            pullReplication.setContinuous(true);

            // it should go idle twice, hence countdown latch = 2
            final CountDownLatch replicationIdleFirstTime = new CountDownLatch(1);
            final CountDownLatch replicationIdleSecondTime = new CountDownLatch(2);
            final CountDownLatch replicationStoppedFirstTime = new CountDownLatch(1);

            pullReplication.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getTransition() != null && event.getTransition().getDestination() == ReplicationState.IDLE) {
                        Log.i(Log.TAG, "IDLE");
                        replicationIdleFirstTime.countDown();
                        replicationIdleSecondTime.countDown();
                    } else if (event.getTransition() != null && event.getTransition().getDestination() == ReplicationState.STOPPED) {
                        Log.i(Log.TAG, "STOPPED");
                        replicationStoppedFirstTime.countDown();
                    }
                }
            });

            pullReplication.start();


            // wait until replication goes idle
            boolean success = replicationIdleFirstTime.await(60, TimeUnit.SECONDS);
            assertTrue(success);

            pullReplication.stop();

            // wait until replication stop
            success = replicationStoppedFirstTime.await(60, TimeUnit.SECONDS);
            assertTrue(success);

            pullReplication.restart();

            // wait until replication goes idle again
            success = replicationIdleSecondTime.await(60, TimeUnit.SECONDS);
            assertTrue(success);

            stopReplication(pullReplication);
        } finally {
            // cleanup / shutdown
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/696
     * in Unit-Tests/Replication_Tests.m
     * - (void)test17_RemovedRevision
     */
    public void test17_RemovedRevision() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        try {
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

            server.play();

            Document doc = database.getDocument("doc1");
            UnsavedRevision unsaved = doc.createRevision();
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("_removed", true);
            unsaved.setProperties(props);
            SavedRevision rev = unsaved.save();
            assertNotNull(rev);

            // create and start push replication
            Replication push = database.createPushReplication(server.getUrl("/db"));
            CountDownLatch latch = new CountDownLatch(1);
            push.addChangeListener(new ReplicationFinishedObserver(latch));
            push.start();

            assertTrue(push.isDocumentPending(doc));

            assertTrue(latch.await(30, TimeUnit.SECONDS));

            assertNull(push.lastError);
            assertEquals(0, push.getCompletedChangesCount());
            assertEquals(0, push.getChangesCount());
            assertFalse(push.isDocumentPending(doc));
        } finally {
            // cleanup / shutdown
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/696
     * in Unit-Tests/Replication_Tests.m
     * - (void)test18_PendingDocumentIDs
     */
    public void test18_PendingDocumentIDs() throws Exception {
        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        server.setDispatcher(dispatcher);
        try {
            server.play();

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

            Replication repl = database.createPushReplication(server.getUrl("/db"));
            assertNotNull(repl.getPendingDocumentIDs());
            assertEquals(0, repl.getPendingDocumentIDs().size());

            assertTrue(database.runInTransaction(
                    new TransactionalTask() {
                        @Override
                        public boolean run() {
                            for (int i = 1; i <= 10; i++) {
                                Document doc = database.getDocument(String.format("doc-%d", i));
                                Map<String, Object> props = new HashMap<String, Object>();
                                props.put("index", i);
                                props.put("bar", false);
                                try {
                                    doc.putProperties(props);
                                } catch (CouchbaseLiteException e) {
                                    fail(e.getMessage());
                                }
                            }
                            return true;
                        }
                    }
            ));


            assertEquals(10, repl.getPendingDocumentIDs().size());
            assertTrue(repl.isDocumentPending(database.getDocument("doc-1")));

            runReplication(repl);

            assertNotNull(repl.getPendingDocumentIDs());
            assertEquals(0, repl.getPendingDocumentIDs().size());
            assertFalse(repl.isDocumentPending(database.getDocument("doc-1")));


            assertTrue(database.runInTransaction(
                    new TransactionalTask() {
                        @Override
                        public boolean run() {
                            for (int i = 11; i <= 20; i++) {
                                Document doc = database.getDocument(String.format("doc-%d", i));
                                Map<String, Object> props = new HashMap<String, Object>();
                                props.put("index", i);
                                props.put("bar", false);
                                try {
                                    doc.putProperties(props);
                                } catch (CouchbaseLiteException e) {
                                    fail(e.getMessage());
                                }
                            }
                            return true;
                        }
                    }
            ));

            repl = database.createPushReplication(server.getUrl("/db"));
            assertNotNull(repl.getPendingDocumentIDs());
            assertEquals(10, repl.getPendingDocumentIDs().size());
            assertTrue(repl.isDocumentPending(database.getDocument("doc-11")));
            assertFalse(repl.isDocumentPending(database.getDocument("doc-1")));

            // pull replication
            repl = database.createPullReplication(server.getUrl("/db"));
            assertNull(repl.getPendingDocumentIDs());
            runReplication(repl);
            assertNull(repl.getPendingDocumentIDs());
        } finally {
            // cleanup / shutdown
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/328
     * <p/>
     * Without bug fix, we observe extra PUT /{db}/_local/xxx for each _bulk_docs request
     * <p/>
     * 1. Create 200 docs
     * 2. Start push replicator
     * 3. GET  /{db}/_local/xxx
     * 4. PUSH /{db}/_revs_diff x 2
     * 5. PUSH /{db}/_bulk_docs x 2
     * 6. PUT  /{db}/_local/xxx
     */
    public void testExcessiveCheckpointingDuringPushReplication() throws Exception {
        final int NUM_DOCS = 199;
        List<Document> docs = new ArrayList<Document>();

        // 1. Add more than 100 docs, as chunk size is 100
        for (int i = 0; i < NUM_DOCS; i++) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("testExcessiveCheckpointingDuringPushReplication", String.valueOf(i));
            Document doc = createDocumentWithProperties(database, properties);
            docs.add(doc);
        }

        // create mock server
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        try {
            server.play();

            // checkpoint GET response -> error

            // _revs_diff response -- everything missing
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            mockRevsDiff.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // _bulk_docs response -- everything stored
            MockBulkDocs mockBulkDocs = new MockBulkDocs();
            mockBulkDocs.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            // checkpoint PUT response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // 2. Kick off continuous push replication
            Replication replicator = database.createPushReplication(server.getUrl("/db"));
            replicator.setContinuous(true);
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            ReplicationIdleObserver replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
            replicator.addChangeListener(replicationIdleObserver);
            replicator.start();

            // 3. Wait for document to be pushed

            // NOTE: (Not 100% reproducible) With CBL Java on Jenkins (Super slow environment),
            //       Replicator becomes IDLE between batches for this case, after 100 push replicated.
            // TODO: Need to investigate

            // wait until replication goes idle
            boolean successful = replicationIdleSignal.await(60, TimeUnit.SECONDS);
            assertTrue(successful);

            // wait until mock server gets the checkpoint PUT request
            boolean foundCheckpointPut = false;
            String expectedLastSequence = String.valueOf(NUM_DOCS);
            while (!foundCheckpointPut) {
                RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
                if (request.getMethod().equals("PUT")) {
                    foundCheckpointPut = true;
                    String body = request.getUtf8Body();
                    Log.i("testExcessiveCheckpointingDuringPushReplication", "body => " + body);
                    // TODO: this is not valid if device can not handle all replication data at once
                    if (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {
                        assertTrue(body.indexOf(expectedLastSequence) != -1);
                    }
                    // wait until mock server responds to the checkpoint PUT request
                    dispatcher.takeRecordedResponseBlocking(request);
                }
            }

            // make some assertions about the outgoing _bulk_docs requests
            RecordedRequest bulkDocsRequest1 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(bulkDocsRequest1);

            if (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {
                RecordedRequest bulkDocsRequest2 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
                assertNotNull(bulkDocsRequest2);

                // TODO: this is not valid if device can not handle all replication data at once
                // order may not be guaranteed
                assertTrue(isBulkDocJsonContainsDoc(bulkDocsRequest1, docs.get(0)) || isBulkDocJsonContainsDoc(bulkDocsRequest2, docs.get(0)));
                assertTrue(isBulkDocJsonContainsDoc(bulkDocsRequest1, docs.get(100)) || isBulkDocJsonContainsDoc(bulkDocsRequest2, docs.get(100)));
            }
            // check if Android CBL client sent only one PUT /{db}/_local/xxxx request
            // previous check already consume this request, so queue size should be 0.
            BlockingQueue<RecordedRequest> queue = dispatcher.getRequestQueueSnapshot(MockHelper.PATH_REGEX_CHECKPOINT);
            assertEquals(0, queue.size());

            // cleanup
            stopReplication(replicator);
        } finally {
            server.shutdown();
        }
    }

    // NOTE: This test should be manually tested. This test uses delay, timeout, wait,...
    // this could break test on Jenkins because it run on VM with ARM emulator.
    // To run test, please remove "manual" from test method name.
    //
    // https://github.com/couchbase/couchbase-lite-java-core/issues/736
    // https://github.com/couchbase/couchbase-lite-net/issues/356
    public void manualTestBulkGetTimeout() throws Exception {
        final int TEMP_DEFAULT_CONNECTION_TIMEOUT_SECONDS = CouchbaseLiteHttpClientFactory.DEFAULT_CONNECTION_TIMEOUT_SECONDS;
        final int TEMP_DEFAULT_SO_TIMEOUT_SECONDS = CouchbaseLiteHttpClientFactory.DEFAULT_SO_TIMEOUT_SECONDS;
        final int TEMP_RETRY_DELAY_SECONDS = ReplicationInternal.RETRY_DELAY_SECONDS;
        try {
            // TIMEOUT 1 SEC
            CouchbaseLiteHttpClientFactory.DEFAULT_CONNECTION_TIMEOUT_SECONDS = 1;
            CouchbaseLiteHttpClientFactory.DEFAULT_SO_TIMEOUT_SECONDS = 1;
            ReplicationInternal.RETRY_DELAY_SECONDS = 0;

            // serve 3 mock docs
            int numMockDocsToServe = 2;

            // create mockwebserver and custom dispatcher
            MockDispatcher dispatcher = new MockDispatcher();
            MockWebServer server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

            try {

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
                SmartMockResponseImpl smartMockResponse = new SmartMockResponseImpl(mockChangesFeed.generateMockResponse());
                dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, smartMockResponse);

                // _bulk_get response
                MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
                for (MockDocumentGet.MockDocument mockDocument : mockDocs) {
                    mockBulkGet.addDocument(mockDocument);
                }
                // _bulk_get delays 4 SEC, which is longer custom timeout 5sec.
                // so this cause timeout.
                mockBulkGet.setDelayMs(4 * 1000);
                // makes sticky for retry reponse
                mockBulkGet.setSticky(true);
                dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

                // start mock server
                server.play();

                // run pull replication
                Replication pullReplication = database.createPullReplication(server.getUrl("/db"));
                runReplication(pullReplication, 3 * 60);
                assertNotNull(pullReplication.getLastError());
                assertTrue(pullReplication.getLastError() instanceof java.net.SocketTimeoutException);

                // dump out the outgoing requests for bulk docs
                BlockingQueue<RecordedRequest> bulkGetRequests = dispatcher.getRequestQueueSnapshot(MockHelper.PATH_REGEX_BULK_GET);
                assertTrue(bulkGetRequests.size() > 3); // verify at least 3 times retried
            } finally {
                server.shutdown();
            }
        } finally {
            CouchbaseLiteHttpClientFactory.DEFAULT_CONNECTION_TIMEOUT_SECONDS = TEMP_DEFAULT_CONNECTION_TIMEOUT_SECONDS;
            CouchbaseLiteHttpClientFactory.DEFAULT_SO_TIMEOUT_SECONDS = TEMP_DEFAULT_SO_TIMEOUT_SECONDS;
            ReplicationInternal.RETRY_DELAY_SECONDS = TEMP_RETRY_DELAY_SECONDS;
        }
    }

    // ReplicatorInternal.m: test_UseRemoteUUID
    public void testUseRemoteUUID() throws Exception {
        URL remoteURL1 = new URL("http://alice.local:55555/db");
        Replication r1 = database.createPullReplication(remoteURL1);
        r1.setRemoteUUID("cafebabe");
        String check1 = r1.replicationInternal.remoteCheckpointDocID();

        // Different URL, but same remoteUUID:
        URL remoteURL2 = new URL("http://alice17.local:44444/db");
        Replication r2 = database.createPullReplication(remoteURL2);
        r2.setRemoteUUID("cafebabe");
        String check2 = r2.replicationInternal.remoteCheckpointDocID();
        assertEquals(check1, check2);

        // Same UUID but different filter settings:
        Replication r3 = database.createPullReplication(remoteURL2);
        r3.setRemoteUUID("cafebabe");
        r3.setFilter("Melitta");
        String check3 = r3.replicationInternal.remoteCheckpointDocID();
        assertNotSame(check2, check3);
    }

    /**
     * This test is almost identical with
     * TestCase(CBL_Pusher_DocIDs) in CBLReplicator_Tests.m
     */
    public void testPushReplicationSetDocumentIDs() throws Exception {
        // Create documents:
        createDocumentForPushReplication("doc1", null, null);
        createDocumentForPushReplication("doc2", null, null);
        createDocumentForPushReplication("doc3", null, null);
        createDocumentForPushReplication("doc4", null, null);

        MockWebServer server = null;
        try {
            // Create mock server and play:
            MockDispatcher dispatcher = new MockDispatcher();
            server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.play();

            // Checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests:
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(50);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // _revs_diff response -- everything missing:
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // _bulk_docs response -- everything stored
            MockBulkDocs mockBulkDocs = new MockBulkDocs();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            // Create push replication:
            Replication replication = database.createPushReplication(server.getUrl("/db"));
            replication.setDocIds(Arrays.asList(new String[]{"doc2", "doc3"}));

            // check pending document IDs:
            Set<String> pendingDocIDs = replication.getPendingDocumentIDs();
            assertEquals(2, pendingDocIDs.size());
            assertFalse(pendingDocIDs.contains("doc1"));
            assertTrue(pendingDocIDs.contains("doc2"));
            assertTrue(pendingDocIDs.contains("doc3"));
            assertFalse(pendingDocIDs.contains("doc4"));

            // Run replication:
            runReplication(replication);

            // Check result:
            RecordedRequest bulkDocsRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(bulkDocsRequest);
            assertFalse(MockHelper.getUtf8Body(bulkDocsRequest).contains("doc1"));
            assertTrue(MockHelper.getUtf8Body(bulkDocsRequest).contains("doc2"));
            assertTrue(MockHelper.getUtf8Body(bulkDocsRequest).contains("doc3"));
            assertFalse(MockHelper.getUtf8Body(bulkDocsRequest).contains("doc4"));
        } finally {
            if (server != null)
                server.shutdown();
        }
    }

    public void testPullReplicationSetDocumentIDs() throws Exception {
        MockWebServer server = null;
        try {
            // Create mock server and play:
            MockDispatcher dispatcher = new MockDispatcher();
            server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.play();

            // checkpoint PUT or GET response (sticky):
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // _changes response:
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

            // Run pull replication:
            Replication replication = database.createPullReplication(server.getUrl("/db"));
            replication.setDocIds(Arrays.asList(new String[]{"doc2", "doc3"}));
            runReplication(replication);

            // Check changes feed request:
            RecordedRequest getChangesFeedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));
            String body = getChangesFeedRequest.getUtf8Body();
            Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(body, Map.class);
            assertTrue(jsonMap.containsKey("filter"));
            String filter = (String) jsonMap.get("filter");
            assertEquals("_doc_ids", filter);
            List<String> docIDs = (List<String>) jsonMap.get("doc_ids");
            assertNotNull(docIDs);
            assertEquals(2, docIDs.size());
            assertTrue(docIDs.contains("doc2"));
            assertTrue(docIDs.contains("doc3"));
        } finally {
            if (server != null)
                server.shutdown();
        }
    }

    public void testPullWithGzippedChangesFeed() throws Exception {
        MockWebServer server = null;
        try {
            // Create mock server and play:
            MockDispatcher dispatcher = new MockDispatcher();
            server = MockHelper.getMockWebServer(dispatcher);
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.play();

            // Mock documents to be pulled:
            MockDocumentGet.MockDocument mockDoc1 =
                    new MockDocumentGet.MockDocument("doc1", "1-5e38", 1);
            mockDoc1.setJsonMap(MockHelper.generateRandomJsonMap());
            MockDocumentGet.MockDocument mockDoc2 =
                    new MockDocumentGet.MockDocument("doc2", "1-563b", 2);
            mockDoc2.setJsonMap(MockHelper.generateRandomJsonMap());

            // // checkpoint GET response w/ 404:
            MockResponse fakeCheckpointResponse = new MockResponse();
            MockHelper.set404NotFoundJson(fakeCheckpointResponse);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

            // _changes response:
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc1));
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc2));
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES,
                    mockChangesFeed.generateMockResponse(/*gzip*/true));

            // doc1 response:
            MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc1);
            dispatcher.enqueueResponse(mockDoc1.getDocPathRegex(),
                    mockDocumentGet.generateMockResponse());

            // doc2 response:
            mockDocumentGet = new MockDocumentGet(mockDoc2);
            dispatcher.enqueueResponse(mockDoc2.getDocPathRegex(),
                    mockDocumentGet.generateMockResponse());

            // _bulk_get response:
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDoc1);
            mockBulkGet.addDocument(mockDoc2);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // Respond to all PUT Checkpoint requests
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(500);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // Setup database change listener:
            final List<String> changeDocIDs = new ArrayList<String>();
            database.addChangeListener(new Database.ChangeListener() {
                @Override
                public void changed(Database.ChangeEvent event) {
                    for (DocumentChange change : event.getChanges()) {
                        changeDocIDs.add(change.getDocumentId());
                    }
                }
            });

            // Run pull replication:
            Replication replication = database.createPullReplication(server.getUrl("/db"));
            runReplication(replication);

            // Check result:
            assertEquals(2, changeDocIDs.size());
            String[] docIDs = changeDocIDs.toArray(new String[changeDocIDs.size()]);
            Arrays.sort(docIDs);
            assertTrue(Arrays.equals(new String[]{"doc1", "doc2"}, docIDs));

            // Check changes feed request:
            RecordedRequest changesFeedRequest =
                    dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
            String acceptEncoding = changesFeedRequest.getHeader("Accept-Encoding");
            assertNotNull(acceptEncoding);
            assertTrue(acceptEncoding.contains("gzip"));
        } finally {
            if (server != null)
                server.shutdown();
        }
    }
}
