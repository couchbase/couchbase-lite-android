package com.couchbase.lite.replicator;

import com.couchbase.lite.Attachment;
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
import com.couchbase.lite.Status;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.ValidationContext;
import com.couchbase.lite.Validator;
import com.couchbase.lite.View;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.auth.FacebookAuthorizer;
import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.threading.BackgroundTask;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;

import java.io.ByteArrayInputStream;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicationTest extends LiteTestCase {

    public static final String TAG = "ReplicationTest";


    /**
     * Currently failing due to issue:
     * https://github.com/couchbase/couchbase-lite-java-core/issues/271
     *
     * This test demonstrates that when the replication finished observer is called the replicator
     * might have not processed all changes.
     * This test fails intermittently.
     */
    public void failingTestReplicationChangesCount() throws Exception {

        //create changes feed with mock documents
        //each mock document must have a delay to guarantee it is not the MockerServer response that is too fast
        //verify there isn't any change to process after stopped is invoked

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);

        // add sticky checkpoint GET response w/ 404
        MockCheckpointGet fakeCheckpointResponse = new MockCheckpointGet();
        fakeCheckpointResponse.set404(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

        //generate mock document gets responses with delay
        List<MockDocumentGet.MockDocument> mockGetDocuments =  new ArrayList<MockDocumentGet.MockDocument>();
        int lastDocSequence = 0;
        int numDocsToAdd = 20;
        for (int i = 0; i < numDocsToAdd; i++) {
            int docSeq = i + 1;
            final MockDocumentGet.MockDocument mockDocument = new MockDocumentGet.MockDocument(
                    "doc-" + String.valueOf(i) + String.valueOf(System.currentTimeMillis()),
                    "1-a",
                    docSeq
            );
            lastDocSequence = docSeq;
            mockDocument.setJsonMap(MockHelper.generateRandomJsonMap());
            mockGetDocuments.add(mockDocument);
        }

        //add response to _changes request
        // _changes feed with mock documents to fetch
        MockChangesFeed mockChangesFeed = new MockChangesFeed();
        for (MockDocumentGet.MockDocument mockDocument: mockGetDocuments) {
            //add document to changes feed
            mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDocument));

            //generate response for document fetch
            MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDocument);
            dispatcher.enqueueResponse(mockDocument.getDocPathRegex(), mockDocumentGet.generateMockResponse());
        }
        //add changes feed to server
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeed.generateMockResponse());

        // checkpoint PUT response
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(200);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        //start mock server
        server.play();

        //create url for replication
        URL baseUrl = server.getUrl("/db");

        //create replication
        final Replication pullReplication = (Replication) database.createPullReplication(baseUrl);
        pullReplication.setContinuous(false);

        //add change listener to notify when the replication is finished
        CountDownLatch replicationFinishedContCountDownLatch = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver =
                new ReplicationFinishedObserver(replicationFinishedContCountDownLatch);
        pullReplication.addChangeListener(replicationFinishedObserver);

        //start replication
        pullReplication.start();

        boolean success = replicationFinishedContCountDownLatch.await(200, TimeUnit.SECONDS);
        assertTrue(success);

        // we should expect to see a GET request for each document
        for (MockDocumentGet.MockDocument mockDocument: mockGetDocuments) {
            dispatcher.takeRequestBlocking(mockDocument.getDocPathRegex());
            Log.d(TAG, "Capture request for document: %s", mockDocument.getDocPathRegex());
        }

        // wait until mock server gets a PUT checkpoint with last doc seq
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, lastDocSequence);
        validateCheckpointRequestsRevisions(checkpointRequests);

        // assert our local sequence matches what is expected
        String lastSequence = database.lastSequenceWithCheckpointId(pullReplication.remoteCheckpointDocID());
        assertEquals(Integer.toString(lastDocSequence), lastSequence);

        // assert we have number of expected docs in local db
        Query queryAllDocs = database.createAllDocumentsQuery();
        QueryEnumerator queryEnumerator = queryAllDocs.run();
        assertEquals(numDocsToAdd, queryEnumerator.getCount());

        // verify change counts are what they are expected to be
        assertEquals(pullReplication.getChangesCount(), pullReplication.getCompletedChangesCount());

        pullReplication.stop();
        server.shutdown();
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
        final Replication pullReplication = (Replication) database.createPullReplication(baseUrl);
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
        Thread.sleep(5 * 1000);

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
        Replication pullReplication = (Replication) database.createPullReplication(baseUrl);
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

        //assert the replicator was able to finish
        assertEquals(Replication.ReplicationStatus.REPLICATION_STOPPED, pullReplication.getStatus());

        // wait until the replicator PUT's checkpoint with mockDocument3's sequence
        waitForPutCheckpointRequestWithSeq(dispatcher, mockDocument3.getDocSeq());

        //last saved seq must be equal to last pulled document seq
        String doc3Seq = Integer.toString(mockDocument3.getDocSeq());
        assertEquals(doc3Seq, pullReplication.getLastSequence());

        // TODO: fix https://github.com/couchbase/couchbase-lite-java-core/issues/252 and re-enable assertion
        // assertEquals(doc3Seq, database.lastSequenceWithCheckpointId(mockDocument3.getDocSeq()));

        //stop mock server
        server.shutdown();

    }

    public void waitForPutCheckpointRequestWithSeq(MockDispatcher dispatcher, int seq) {
        while (true) {
            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            if (request.getMethod().equals("PUT")) {
                String body = request.getUtf8Body();
                if (body.indexOf(Integer.toString(seq)) != -1) {
                    // block until response returned
                    dispatcher.takeRecordedResponseBlocking(request);
                    return;
                }
            }
        }
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
        pull.setRetryDelay(retryDelaySeconds);

        // add done listener to replication
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        pull.addChangeListener(replicationFinishedObserver);

        // start the replication
        pull.start();

        // sleep in order to give the replication some time to retry
        // and send multiple _changes requests.
        Thread.sleep(5 * 1000 * retryDelaySeconds);

        // the replication should still be running
        assertEquals(1, replicationDoneSignal.getCount());

        // assert that we've received at least two requests to the _changes feed
        RecordedRequest changesReq = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
        assertNotNull(changesReq);
        changesReq = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
        assertNotNull(changesReq);

        // cleanup
        Log.d(TAG, "Calling pull.stop()");
        pull.stop();
        boolean success = replicationDoneSignal.await(60, TimeUnit.SECONDS);
        assertTrue(success);
        server.shutdown();


    }


    /**
     * Verify that running a one-shot push replication will complete when run against a
     * mock server that returns 500 Internal Server errors on every request.
     */
    public void testOneShotReplicationErrorNotification() throws Throwable {

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderThrowExceptionAllRequests();

        URL remote = getReplicationURL();

        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(remote);

        runReplication(pusher);

        assertTrue(pusher.getLastError() != null);

    }

    /**
     * Verify that running a continuous push replication will emit a change while
     * in an error state when run against a mock server that returns 500 Internal Server
     * errors on every request.
     */
    public void testContinuousReplicationErrorNotification() throws Throwable {

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderThrowExceptionAllRequests();

        URL remote = getReplicationURL();

        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(remote);
        pusher.setContinuous(true);

        // add replication observer
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ReplicationErrorObserver replicationErrorObserver = new ReplicationErrorObserver(countDownLatch);
        pusher.addChangeListener(replicationErrorObserver);

        // start replication
        pusher.start();

        boolean success = countDownLatch.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        pusher.stop();

    }

    private HttpClientFactory mockFactoryFactory(final CustomizableMockHttpClient mockHttpClient) {
        return new HttpClientFactory() {
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
    }

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

        pusher.stop();


    }

    public void disabledTestPusherIntegration() throws Throwable {

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
        Assert.assertNull(repl.getDocIds());
        // TODO: CAssertNil(r1.headers); still not null!

        // Check that the replication hasn't started running:
        Assert.assertFalse(repl.isRunning());
        Assert.assertEquals(Replication.ReplicationStatus.REPLICATION_STOPPED, repl.getStatus());
        Assert.assertEquals(0, repl.getCompletedChangesCount());
        Assert.assertEquals(0, repl.getChangesCount());
        Assert.assertNull(repl.getLastError());

        runReplication(repl);

        // since we pushed two documents, should expect the changes count to be >= 2
        assertTrue(repl.getChangesCount() >= 2);
        assertTrue(repl.getCompletedChangesCount() >= 2);
        assertNull(repl.getLastError());

        // make sure doc1 is there
        verifyRemoteDocExists(remote, doc1Id);

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
        String repl2CheckpointId = repl2.remoteCheckpointDocID();
        runReplication(repl2);
        assertNull(repl2.getLastError());


        // make sure the doc has been added
        verifyRemoteDocExists(remote, doc3Id);

        // verify sequence stored in local db has been updated
        boolean isPush = true;
        assertEquals(repl2.getLastSequence(), database.getLastSequenceStored(repl2CheckpointId, isPush));

        // wait a few seconds in case reqeust to server to update checkpoint still in flight
        Thread.sleep(2000);

        // verify that the _local doc remote checkpoint has been updated and it matches
        String pathToCheckpointDoc = String.format("%s/_local/%s", remote.toExternalForm(), repl2CheckpointId);
        HttpResponse response = getRemoteDoc(new URL(pathToCheckpointDoc));
        Map<String, Object> json = extractJsonFromResponse(response);
        String remoteLastSequence = (String) json.get("lastSequence");
        assertEquals(repl2.getLastSequence(), remoteLastSequence);

        Log.d(TAG, "testPusher() finished");

    }

    private Map<String, Object> extractJsonFromResponse(HttpResponse response) throws IOException{
        InputStream is =  response.getEntity().getContent();
        return Manager.getObjectMapper().readValue(is, Map.class);
    }

    private String createDocumentsForPushReplication(String docIdTimestamp) throws CouchbaseLiteException {
        return createDocumentsForPushReplication(docIdTimestamp, "png");
    }

    private Document createDocumentForPushReplication(String docId, String attachmentFileName, String attachmentContentType) throws CouchbaseLiteException {

        Map<String, Object> docJsonMap = MockHelper.generateRandomJsonMap();
        Map<String, Object> docProperties = new HashMap<String, Object>();
        docProperties.put("_id", docId);
        docProperties.putAll(docJsonMap);
        Document document = database.getDocument(docId);
        UnsavedRevision revision = document.createRevision();
        revision.setProperties(docProperties);

        if (attachmentFileName != null) {
            revision.setAttachment(
                    attachmentFileName,
                    attachmentContentType,
                    getAsset(attachmentFileName)
            );
        }

        revision.save();
        return document;

    }

    private String createDocumentsForPushReplication(String docIdTimestamp, String attachmentType) throws CouchbaseLiteException {
        String doc1Id;
        String doc2Id;// Create some documents:
        Map<String, Object> doc1Properties = new HashMap<String, Object>();
        doc1Id = String.format("doc1-%s", docIdTimestamp);
        doc1Properties.put("_id", doc1Id);
        doc1Properties.put("foo", 1);
        doc1Properties.put("bar", false);

        Body body = new Body(doc1Properties);
        RevisionInternal rev1 = new RevisionInternal(body, database);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);
        assertEquals(Status.CREATED, status.getCode());

        doc1Properties.put("_rev", rev1.getRevId());
        doc1Properties.put("UPDATED", true);

        @SuppressWarnings("unused")
        RevisionInternal rev2 = database.putRevision(new RevisionInternal(doc1Properties, database), rev1.getRevId(), false, status);
        assertEquals(Status.CREATED, status.getCode());

        Map<String, Object> doc2Properties = new HashMap<String, Object>();
        doc2Id = String.format("doc2-%s", docIdTimestamp);
        doc2Properties.put("_id", doc2Id);
        doc2Properties.put("baz", 666);
        doc2Properties.put("fnord", true);

        database.putRevision(new RevisionInternal(doc2Properties, database), null, false, status);
        assertEquals(Status.CREATED, status.getCode());

        Document doc2 = database.getDocument(doc2Id);
        UnsavedRevision doc2UnsavedRev = doc2.createRevision();
        if (attachmentType.equals("png")) {
            InputStream attachmentStream = getAsset("attachment.png");
            doc2UnsavedRev.setAttachment("attachment.png", "image/png", attachmentStream);
        } else if (attachmentType.equals("txt")) {
            StringBuffer sb = new StringBuffer();
            for (int i=0; i<1000; i++) {
                sb.append("This is a large attachemnt.");
            }
            ByteArrayInputStream attachmentStream = new ByteArrayInputStream(sb.toString().getBytes());
            doc2UnsavedRev.setAttachment("attachment.txt", "text/plain", attachmentStream);
        } else {
            throw new RuntimeException("invalid attachment type: " + attachmentType);
        }
        SavedRevision doc2Rev = doc2UnsavedRev.save();
        assertNotNull(doc2Rev);

        return doc1Id;
    }

    private boolean isSyncGateway(URL remote) {
        return (remote.getPort() == 4984 || remote.getPort() == 80);
    }

    private HttpResponse getRemoteDoc(URL pathToDoc) throws MalformedURLException, IOException {

        HttpClient httpclient = new DefaultHttpClient();

        HttpResponse response = null;
        String responseString = null;
        response = httpclient.execute(new HttpGet(pathToDoc.toExternalForm()));
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
            throw new RuntimeException("Did not get 200 status doing GET to URL: " + pathToDoc);
        }
        return response;

    }

    /**
     * TODO: 1. refactor to use getRemoteDoc
     * TODO: 2. can just make synchronous http call, no need for background task
     *
     * @param remote
     * @param doc1Id
     * @throws MalformedURLException
     */
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
    }

    /**
     * Regression test for https://github.com/couchbase/couchbase-lite-java-core/issues/72
     */
    public void testPusherBatching() throws Throwable {

        // create a bunch (INBOX_CAPACITY * 2) local documents
        int numDocsToSend = Replication.INBOX_CAPACITY * 2;
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
                    assertTrue(msg, docs.size() <= Replication.INBOX_CAPACITY);
                    numDocsSent += docs.size();
                }
            }
        }

        assertEquals(numDocsToSend, numDocsSent);

    }

    public void failingTestPullerGzipped() throws Throwable {

        String docIdTimestamp = Long.toString(System.currentTimeMillis());
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
        byte[] receivedBytes = TextUtils.read(attachment.getContent());

        InputStream attachmentStream = getAsset(attachmentName);
        byte[] actualBytes = TextUtils.read(attachmentStream);
        Assert.assertEquals(actualBytes.length, receivedBytes.length);
        Assert.assertEquals(actualBytes, receivedBytes);

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
        Replication pullReplication = doPullReplication(server.getUrl("/db"));

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
    public void failingTestMockPullerRestart() throws Exception {


        final int numMockRemoteDocs = 50;  // must be multiple of 10!
        final int numDocsReceivedRestart = 10;  // when we've received this many docs, restart replicator

        final AtomicInteger numDocsPulledLocally = new AtomicInteger(0);

        MockDispatcher dispatcher = new MockDispatcher();
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        MockWebServer server = new GoOfflinePreloadedPullTarget(dispatcher, numMockRemoteDocs, 1).getMockWebServer();

        server.play();

        final CountDownLatch receivedAllDocs = new CountDownLatch(1);
        final CountDownLatch receivedSomeDocs = new CountDownLatch(1);

        // run pull replication
        final Replication repl = (Replication) database.createPullReplication(server.getUrl("/db"));
        repl.setContinuous(true);
        repl.start();

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                List<DocumentChange> changes = event.getChanges();
                for (DocumentChange change : changes) {
                    numDocsPulledLocally.addAndGet(1);
                }
                if (numDocsPulledLocally.get() > numDocsReceivedRestart) {
                    receivedSomeDocs.countDown();
                }
                if (numDocsPulledLocally.get() == numMockRemoteDocs) {
                    receivedAllDocs.countDown();
                }
            }
        });

        // wait until we received a few docs
        boolean success = receivedSomeDocs.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        stopReplication(repl);

        repl.start();

        // wait until we received all mock docs or timeout occurs
        success = receivedAllDocs.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        // make sure all docs in local db
        Map<String, Object> allDocs = database.getAllDocs(new QueryOptions());
        Integer totalRows = (Integer) allDocs.get("total_rows");
        List rows = (List) allDocs.get("rows");
        assertEquals(numMockRemoteDocs, totalRows.intValue());
        assertEquals(numMockRemoteDocs, rows.size());

        // cleanup / shutdown
        stopReplication(repl);
        server.shutdown();


    }



    /**
     * Pull replication test:
     *
     * - Single one-shot pull replication
     * - Against simulated sync gateway
     * - Remote docs have attachments
     */
    public void testMockSinglePullSyncGwAttachments() throws Exception {

        boolean shutdownMockWebserver = true;
        boolean addAttachments = true;

        mockSinglePull(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW, addAttachments);

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
     * - Against simulated couchdb
     * - Remote docs do not have attachments
     */
    public void testMockSinglePullCouchDb() throws Exception {

        boolean shutdownMockWebserver = true;
        boolean addAttachments = false;


        mockSinglePull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB, addAttachments);

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

        // respond to all PUT Checkpoint requests
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        mockCheckpointPut.setDelayMs(500);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

        // start mock server
        server.play();

        // run pull replication
        Replication pullReplication = doPullReplication(server.getUrl("/db"));

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
        assertTrue(getCheckpointRequest.getMethod().equals("GET"));
        assertTrue(getCheckpointRequest.getPath().matches(MockHelper.PATH_REGEX_CHECKPOINT));
        RecordedRequest getChangesFeedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
        if (serverType == MockDispatcher.ServerType.SYNC_GW) {
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));

        } else {
            assertTrue(getChangesFeedRequest.getMethod().equals("GET"));
        }
        assertTrue(getChangesFeedRequest.getPath().matches(MockHelper.PATH_REGEX_CHANGES));
        RecordedRequest doc1Request = dispatcher.takeRequest(mockDoc1.getDocPathRegex());
        assertTrue(doc1Request.getMethod().equals("GET"));
        assertTrue(doc1Request.getPath().matches(mockDoc1.getDocPathRegex()));
        RecordedRequest doc2Request = dispatcher.takeRequest(mockDoc2.getDocPathRegex());
        assertTrue(doc2Request.getMethod().equals("GET"));
        assertTrue(doc2Request.getPath().matches(mockDoc2.getDocPathRegex()));

        // wait until the mock webserver receives a PUT checkpoint request with doc #2's sequence
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, mockDoc2.getDocSeq());
        validateCheckpointRequestsRevisions(checkpointRequests);
        assertEquals(1, checkpointRequests.size());

        // assert our local sequence matches what is expected
        String lastSequence = database.lastSequenceWithCheckpointId(pullReplication.remoteCheckpointDocID());
        assertEquals(Integer.toString(mockDoc2.getDocSeq()), lastSequence);

        // assert completed count makes sense
        assertEquals(pullReplication.getChangesCount(), pullReplication.getCompletedChangesCount());

        // Shut down the server. Instances cannot be reused.
        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);

        return returnVal;

    }


    private void validateCheckpointRequestsRevisions(List<RecordedRequest> checkpointRequests) {
        try {
            int i = 0;
            for (RecordedRequest request : checkpointRequests) {
                Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(request.getUtf8Body(), Map.class);
                if (i == 0) {
                    // the first request is not expected to have a _rev field
                    assertFalse(jsonMap.containsKey("_rev"));
                } else {
                    assertTrue(jsonMap.containsKey("_rev"));
                    // TODO: make sure that each _rev is in sequential order, eg: "0-1", "0-2", etc..
                }
                i += 1;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private List<RecordedRequest> waitForPutCheckpointRequestWithSequence(MockDispatcher dispatcher, int expectedLastSequence) throws IOException {

        List<RecordedRequest> recordedRequests = new ArrayList<RecordedRequest>();

        // wait until mock server gets a checkpoint PUT request with expected lastSequence
        boolean foundExpectedLastSeq = false;
        String expectedLastSequenceStr = String.format("%s", expectedLastSequence);

        while (!foundExpectedLastSeq) {

            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            if (request.getMethod().equals("PUT")) {

                recordedRequests.add(request);

                Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(request.getUtf8Body(), Map.class);
                if (jsonMap.containsKey("lastSequence") && ((String)jsonMap.get("lastSequence")).equals(expectedLastSequenceStr)) {
                    foundExpectedLastSeq = true;
                }

                // wait until mock server responds to the checkpoint PUT request.
                // not sure if this is strictly necessary, but might prevent race conditions.
                dispatcher.takeRecordedResponseBlocking(request);
            }
        }

        return recordedRequests;

    }

    public void testMockContinuousPullCouchDb() throws Exception {
        boolean shutdownMockWebserver = true;
        mockContinuousPull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB);
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
        final Replication repl = (Replication) database.createPullReplication(server.getUrl("/db"));
        repl.setContinuous(true);
        repl.start();

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

        // wait until we received all mock docs or timeout occurs
        boolean success = receivedAllDocs.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        // make sure all docs in local db
        Map<String, Object> allDocs = database.getAllDocs(new QueryOptions());
        Integer totalRows = (Integer) allDocs.get("total_rows");
        List rows = (List) allDocs.get("rows");
        assertEquals(numMockRemoteDocs, totalRows.intValue());
        assertEquals(numMockRemoteDocs, rows.size());

        // cleanup / shutdown
        stopReplication(repl);
        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);
        return returnVal;

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

        // checkpoint GET response w/ 404
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, fakeCheckpointResponse);

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
        List<RecordedRequest> checkpointRequests = waitForPutCheckpointRequestWithSequence(dispatcher, expectedLastSequence);
        assertEquals(1, checkpointRequests.size());

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

    private void attachmentAsserts(String docAttachName, Document doc) throws IOException, CouchbaseLiteException {
        Attachment attachment = doc.getCurrentRevision().getAttachment(docAttachName);
        assertNotNull(attachment);
        byte[] testAttachBytes = MockDocumentGet.getAssetByteArray(docAttachName);
        int attachLength = testAttachBytes.length;
        assertEquals(attachLength, attachment.getLength());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(attachment.getContent());
        byte[] actualAttachBytes = baos.toByteArray();
        assertEquals(testAttachBytes.length, actualAttachBytes.length);
        for (int i=0; i<actualAttachBytes.length; i++) {
            boolean ithByteEqual = actualAttachBytes[i] == testAttachBytes[i];
            if (!ithByteEqual) {
                Log.d(Log.TAG, "mismatch");
            }
            assertTrue(ithByteEqual);
        }
    }


    public void testMockMultiplePullSyncGw() throws Exception {

        boolean shutdownMockWebserver = true;

        mockMultiplePull(shutdownMockWebserver, MockDispatcher.ServerType.SYNC_GW);

    }

    public void testMockMultiplePullCouchDb() throws Exception {

        boolean shutdownMockWebserver = true;

        mockMultiplePull(shutdownMockWebserver, MockDispatcher.ServerType.COUCHDB);

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
        Replication repl = (Replication) database.createPullReplication(server.getUrl("/db"));
        runReplication(repl);

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
        String lastSequence = database.lastSequenceWithCheckpointId(repl.remoteCheckpointDocID());
        assertEquals(Integer.toString(expectedLastSequence), lastSequence);

        // assert completed count makes sense
        assertEquals(repl.getChangesCount(), repl.getCompletedChangesCount());

        if (shutdownMockWebserver) {
            server.shutdown();
        }

        Map<String, Object> returnVal = new HashMap<String, Object>();
        returnVal.put("server", server);
        returnVal.put("dispatcher", dispatcher);

        return returnVal;

    }

    /**
     * Note: db should be empty for this to work
     * Note: this test should be disabled by default
     */
    public void pullerIntegrationTest() throws Throwable {

        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        final String doc2Id = String.format("doc2-%s", docIdTimestamp);

        Log.d(TAG, "Adding " + doc1Id + " directly to sync gateway");
        addDocWithId(doc1Id, "attachment.png", false);
        Log.d(TAG, "Adding " + doc2Id + " directly to sync gateway");
        addDocWithId(doc2Id, "attachment2.png", false);

        Replication pullReplication = doPullReplication();

        // allow time for replication to be "done done"
        Thread.sleep(5 * 1000);

        // assertions regarding locally saved last sequence
        String lastSequence = database.lastSequenceWithCheckpointId(pullReplication.remoteCheckpointDocID());
        assertEquals("3", lastSequence);

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
        pushDocumentToSyncGateway(doc1.getId(), docJson);

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

    /**
     * This is essentially a regression test for a deadlock
     * that was happening when the LiveQuery#onDatabaseChanged()
     * was calling waitForUpdateThread(), but that thread was
     * waiting on connection to be released by the thread calling
     * waitForUpdateThread().  When the deadlock bug was present,
     * this test would trigger the deadlock and never finish.
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


    private Replication doPullReplication() {
        URL remote = getReplicationURL();
        return doPullReplication(remote);
    }

    private Replication doPullReplication(URL url) {
        return doPullReplication(url, false);
    }

    private Replication doPullReplication(URL url, boolean allowError) {
        return doPullReplication(url, allowError, false);
    }

    private Replication doPullReplication(URL url, boolean allowError, boolean continuous) {

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);

        final Replication repl = (Replication) database.createPullReplication(url);
        repl.setContinuous(continuous);

        Log.d(TAG, "Doing pull replication with: " + repl);
        runReplication(repl);
        if (!allowError) {
            Throwable lastError = repl.getLastError();
            if (lastError instanceof HttpResponseException) {
                HttpResponseException httpResponseException = (HttpResponseException) lastError;
                Log.d(TAG, "httpResponseException: %s", httpResponseException);
            }
            assertNull(lastError);

        }
        Log.d(TAG, "Finished pull replication with: " + repl);

        return repl;

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

        // add replication observer
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        replicator.addChangeListener(replicationFinishedObserver);

        // start the replicator
        Log.d(TAG, "Starting replicator " + replicator);
        replicator.start();

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

    public void testRunReplicationWithError() throws Exception {

        HttpClientFactory mockHttpClientFactory = new HttpClientFactory() {
            @Override
            public HttpClient getHttpClient() {
                CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
                int statusCode = 500;
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
        runReplication(pullReplication, true);

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

    /*public void failingTestMockBulkPullSyngGw() throws Exception {
        mockBulkPull(MockDispatcher.ServerType.SYNC_GW);
    }


    public void failingTestMockBulkPullCouchDb() throws Exception {
        mockBulkPull(MockDispatcher.ServerType.COUCHDB);
    }*/

    /**
     *
     * TODO: finish this test and close https://github.com/couchbase/couchbase-lite-android/issues/360
     *
     * When the pull replication needs to pull more than MAX_REVS_TO_GET_IN_BULK, it
     * uses a different strategy.
     *
     * - Against CouchDB it calls _all_docs
     * - Against Sync Gw it calls _bulk_get
     *

    public void mockBulkPull(MockDispatcher.ServerType serverType) throws Exception {

        // TODO: the preloadedPullTargetServer needs to handle _all_docs requests in the
        // TODO: case of CouchDB, and _bulk_get in the case of Sync GW

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = Puller.MAX_REVS_TO_GET_IN_BULK + 5;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, Integer.MAX_VALUE);
        dispatcher.setServerType(serverType);
        server.setDispatcher(dispatcher);
        server.play();

        // run pull replication
        Replication pullReplication = doPullReplication(server.getUrl("/db"), false);

        // assertions
        Map<String, Object> allDocs = database.getAllDocs(new QueryOptions());
        assertEquals(numMockDocsToServe, allDocs.size());

        server.shutdown();

    }*/


    static class GoOfflinePreloadedPullTarget extends MockPreloadedPullTarget {

        public GoOfflinePreloadedPullTarget(MockDispatcher dispatcher, int numMockDocsToServe, int numDocsPerChangesResponse) {
            super(dispatcher, numMockDocsToServe, numDocsPerChangesResponse);
        }

        @Override
        public MockWebServer getMockWebServer() {
            MockWebServer server = MockHelper.getMockWebServer(dispatcher);

            List<MockDocumentGet.MockDocument> mockDocs = getMockDocuments();

            addCheckpointResponse();

            // add this a few times to be robust against cases where it
            // doesn't make _changes requests in exactly expected order
            addChangesResponse(mockDocs);
            addChangesResponse(mockDocs);
            addChangesResponse(mockDocs);

            addMockDocuments(mockDocs);

            return server;
        }
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

        // add a 2nd doc to local db
        properties = new HashMap<String, Object>();
        properties.put("testGoOfflinePusher", "2");
        Document doc2 = createDocumentWithProperties(database, properties);

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

    }

    /*

    Assert that the bulk docs json in request contains given doc.

    Example bulk docs json.

     {
       "docs":[
         {
           "_id":"b7f5664c-7f84-4ddf-9abc-de4e3f376ae4",
           ..
         }
       ]
     }
     */
    private void assertBulkDocJsonContainsDoc(RecordedRequest request, Document doc) throws Exception {

        Map <String, Object> bulkDocsJson = Manager.getObjectMapper().readValue(request.getUtf8Body(), Map.class);
        List docs = (List) bulkDocsJson.get("docs");
        Map<String, Object> firstDoc = (Map<String, Object>) docs.get(0);
        assertEquals(doc.getId(), firstDoc.get("_id"));



    }

    /**
     * Test for the goOffline() method.
     *
     * - Kick off one-shot pull replication
     * - After first doc(s) received, put replication offline
     * - Wait for a few seconds
     * - Put replication back online
     * - Make sure replication finishes
     */
    public void testGoOffline() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 50;
        MockDispatcher dispatcher = new MockDispatcher();

        MockWebServer server = new GoOfflinePreloadedPullTarget(dispatcher, numMockDocsToServe, 1).getMockWebServer();
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        server.play();

        Replication replicator = database.createPullReplication(server.getUrl("/db"));
        replicator.setContinuous(true);

        final CountDownLatch firstDocReceived = new CountDownLatch(1);
        final CountDownLatch allDocsReceived = new CountDownLatch(numMockDocsToServe);

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                List<DocumentChange> changes = event.getChanges();
                for (DocumentChange change : changes) {
                    if (change.getDocumentId().startsWith("doc")) {
                        firstDocReceived.countDown();
                        allDocsReceived.countDown();
                    }
                }
            }
        });

        replicator.start();

        boolean success = firstDocReceived.await(120, TimeUnit.SECONDS);
        assertTrue(success);

        putReplicationOffline(replicator);
        Assert.assertTrue(replicator.getStatus() == Replication.ReplicationStatus.REPLICATION_OFFLINE);

        Thread.sleep(5 * 1000);
        putReplicationOnline(replicator);

        success = allDocsReceived.await(120, TimeUnit.SECONDS);
        assertTrue(success);

        // make sure all docs in local db
        Map<String, Object> allDocs = database.getAllDocs(new QueryOptions());
        Integer totalRows = (Integer) allDocs.get("total_rows");
        List rows = (List) allDocs.get("rows");
        assertEquals(numMockDocsToServe, totalRows.intValue());
        assertEquals(numMockDocsToServe, rows.size());

        // cleanup
        stopReplication(replicator);
        server.shutdown();


    }


    public void testBuildRelativeURLString() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replicator = new Pusher(database, new URL(dbUrlString), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("foo");

        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);

    }

    public void testBuildRelativeURLStringWithLeadingSlash() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replicator = new Pusher(database, new URL(dbUrlString), false, null);
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
        Replication puller = database.createPullReplication(remote);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        puller.setHeaders(headers);

        runReplication(puller);
        assertNotNull(puller.getLastError());

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
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);

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
        Replication pullReplication = doPullReplication(server.getUrl("/db"), false);

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


    public void testOnlineOfflinePusher() throws Exception {

        URL remote = getReplicationURL();

        // mock sync gateway
        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderFakeLocalDocumentUpdate404();
        mockHttpClient.addResponderRevDiffsSmartResponder();

        HttpClientFactory mockHttpClientFactory = mockFactoryFactory(mockHttpClient);
        manager.setDefaultHttpClientFactory(mockHttpClientFactory);

        // create a replication observer
        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);

        // create a push replication
        Replication pusher = database.createPushReplication(remote);
        Log.d(Database.TAG, "created pusher: " + pusher);
        pusher.addChangeListener(replicationFinishedObserver);
        pusher.setContinuous(true);
        pusher.start();

        for (int i=0; i<5; i++) {

            Log.d(Database.TAG, "testOnlineOfflinePusher, i: " + i);

            final String docFieldName = "testOnlineOfflinePusher" + i;

            // put the replication offline
            putReplicationOffline(pusher);

            // add a response listener to wait for a bulk_docs request from the pusher
            final CountDownLatch gotBulkDocsRequest = new CountDownLatch(1);
            CustomizableMockHttpClient.ResponseListener bulkDocsListener = new CustomizableMockHttpClient.ResponseListener() {
                @Override
                public void responseSent(HttpUriRequest httpUriRequest, HttpResponse response) {
                    if (httpUriRequest.getURI().getPath().endsWith("_bulk_docs")) {
                        Log.d(TAG, "testOnlineOfflinePusher responselistener called with _bulk_docs");

                        ArrayList docs = CustomizableMockHttpClient.extractDocsFromBulkDocsPost(httpUriRequest);
                        Log.d(TAG, "docs: " + docs);

                        for (Object docObject : docs) {
                            Map<String, Object> doc = (Map) docObject;
                            if (doc.containsKey(docFieldName)) {
                                Log.d(TAG, "Found expected doc in _bulk_docs: " + doc);
                                gotBulkDocsRequest.countDown();
                            } else {
                                Log.d(TAG, "Ignore doc in _bulk_docs: " + doc);
                            }
                        }

                    }

                }
            };
            mockHttpClient.addResponseListener(bulkDocsListener);

            // add a document
            String docFieldVal = "foo" + i;
            Map<String,Object> properties = new HashMap<String, Object>();
            properties.put(docFieldName, docFieldVal);
            createDocumentWithProperties(database, properties);

            // put the replication online, which should trigger it to send outgoing bulk_docs request
            putReplicationOnline(pusher);

            // wait until we get a bulk docs request
            Log.d(Database.TAG, "waiting for bulk docs request with " + docFieldName);
            boolean succeeded = gotBulkDocsRequest.await(90, TimeUnit.SECONDS);
            assertTrue(succeeded);
            Log.d(Database.TAG, "got bulk docs request with " + docFieldName);
            mockHttpClient.removeResponseListener(bulkDocsListener);

            mockHttpClient.clearCapturedRequests();

        }

        Log.d(Database.TAG, "calling pusher.stop()");
        pusher.stop();
        Log.d(Database.TAG, "called pusher.stop()");

        // wait for replication to finish
        Log.d(Database.TAG, "waiting for replicationDoneSignal");
        boolean didNotTimeOut = replicationDoneSignal.await(90, TimeUnit.SECONDS);
        Log.d(Database.TAG, "done waiting for replicationDoneSignal.  didNotTimeOut: " + didNotTimeOut);
        assertTrue(didNotTimeOut);
        assertFalse(pusher.isRunning());


    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void testPushReplicationRecoverableError() throws Exception {
        int statusCode = 503;
        String statusMsg = "Transient Error";
        boolean expectReplicatorError = false;
        runPushReplicationWithTransientError(statusCode, statusMsg, expectReplicatorError);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void testPushReplicationRecoverableIOException() throws Exception {
        int statusCode = -1;  // code to tell it to throw an IOException
        String statusMsg = null;
        boolean expectReplicatorError = false;
        runPushReplicationWithTransientError(statusCode, statusMsg, expectReplicatorError);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void testPushReplicationNonRecoverableError() throws Exception {
        int statusCode = 404;
        String statusMsg = "NOT FOUND";
        boolean expectReplicatorError = true;
        runPushReplicationWithTransientError(statusCode, statusMsg, expectReplicatorError);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/247
     */
    public void runPushReplicationWithTransientError(int statusCode, String statusMsg, boolean expectReplicatorError) throws Exception {

        Map<String,Object> properties1 = new HashMap<String,Object>();
        properties1.put("doc1", "testPushReplicationTransientError");
        createDocWithProperties(properties1);

        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderFakeLocalDocumentUpdate404();

        CustomizableMockHttpClient.Responder sentinal = CustomizableMockHttpClient.fakeBulkDocsResponder();
        Queue<CustomizableMockHttpClient.Responder> responders = new LinkedList<CustomizableMockHttpClient.Responder>();
        responders.add(CustomizableMockHttpClient.transientErrorResponder(statusCode, statusMsg));
        ResponderChain responderChain = new ResponderChain(responders, sentinal);
        mockHttpClient.setResponder("_bulk_docs", responderChain);

        // create a replication observer to wait until replication finishes
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

        if (expectReplicatorError == true) {
            assertNotNull(pusher.getLastError());
        } else {
            assertNull(pusher.getLastError());
        }

        // workaround for the fact that the replicationDoneSignal.wait() call will unblock before all
        // the statements in Replication.stopped() have even had a chance to execute.
        // (specifically the ones that come after the call to notifyChangeListeners())
        Thread.sleep(500);

        String localLastSequence = database.lastSequenceWithCheckpointId(checkpointId);

        if (expectReplicatorError == true) {
            assertNull(localLastSequence);
        } else {
            assertNotNull(localLastSequence);
        }

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

        // create a mock http client that serves as a mocked out sync gateway
        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();

        // replication to do initial sync up - has to be continuous replication so the checkpoint id
        // matches the next continuous replication we're gonna do later.
        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication firstPusher = database.createPushReplication(getReplicationURL());
        firstPusher.setContinuous(true);
        final String checkpointId = firstPusher.remoteCheckpointDocID();  // save the checkpoint id for later usage

        // intercept checkpoint PUT request and return a 201 response with expected json
        mockHttpClient.setResponder("_local", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                String id = String.format("_local/%s", checkpointId);
                String json = String.format("{\"id\":\"%s\",\"ok\":true,\"rev\":\"0-2\"}", id);
                return CustomizableMockHttpClient.generateHttpResponseObject(201, "OK", json);
            }
        });

        // start the continuous replication
        CountDownLatch replicationIdleSignal = new CountDownLatch(1);
        ReplicationIdleObserver replicationIdleObserver = new ReplicationIdleObserver(replicationIdleSignal);
        firstPusher.addChangeListener(replicationIdleObserver);
        firstPusher.start();

        // wait until we get an IDLE event
        boolean successful = replicationIdleSignal.await(30, TimeUnit.SECONDS);
        assertTrue(successful);
        stopReplication(firstPusher);

        // the last sequence should be "1" at this point.  we will use this later
        final String lastSequence = database.lastSequenceWithCheckpointId(checkpointId);
        assertEquals("1", lastSequence);

        // start a second continuous replication
        Replication secondPusher = database.createPushReplication(getReplicationURL());
        secondPusher.setContinuous(true);
        final String secondPusherCheckpointId = secondPusher.remoteCheckpointDocID();
        assertEquals(checkpointId, secondPusherCheckpointId);

        // when this goes to fetch the checkpoint, return the last sequence from the previous replication
        mockHttpClient.setResponder("_local", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                String id = String.format("_local/%s", secondPusherCheckpointId);
                String json = String.format("{\"id\":\"%s\",\"ok\":true,\"rev\":\"0-2\",\"lastSequence\":\"%s\"}", id, lastSequence);
                return CustomizableMockHttpClient.generateHttpResponseObject(200, "OK", json);
            }
        });

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


    private Document createDocWithProperties(Map<String, Object> properties1) throws CouchbaseLiteException {
        Document doc1 = database.createDocument();
        UnsavedRevision revUnsaved = doc1.createRevision();
        revUnsaved.setUserProperties(properties1);
        SavedRevision rev = revUnsaved.save();
        assertNotNull(rev);
        return doc1;
    }

    public void disabledTestCheckpointingWithServerError() throws Exception {

        /**
         * From https://github.com/couchbase/couchbase-lite-android/issues/108#issuecomment-36802239
         * "This ensures it will only save the last sequence in the local database once it
         * has saved it on the server end."
         *
         * This test is marked as disabled because it does not behave as described above, and so the
         * test fails.  Not necessarily a "bug", but a delta in expected behavior by some users vs
         * actual behavior.
         */

        String remoteCheckpointDocId;
        String lastSequenceWithCheckpointIdInitial;
        String lastSequenceWithCheckpointIdFinal;

        URL remote = getReplicationURL();

        // add docs
        String docIdTimestamp = Long.toString(System.currentTimeMillis());
        createDocumentsForPushReplication(docIdTimestamp);

        // do push replication against mock replicator that fails to save remote checkpoint
        final CustomizableMockHttpClient mockHttpClient = new CustomizableMockHttpClient();
        mockHttpClient.addResponderFakeLocalDocumentUpdate404();

        manager.setDefaultHttpClientFactory(mockFactoryFactory(mockHttpClient));
        Replication pusher = database.createPushReplication(remote);

        remoteCheckpointDocId = pusher.remoteCheckpointDocID();
        lastSequenceWithCheckpointIdInitial = database.lastSequenceWithCheckpointId(remoteCheckpointDocId);

        runReplication(pusher);

        List<HttpRequest> capturedRequests = mockHttpClient.getCapturedRequests();
        for (HttpRequest capturedRequest : capturedRequests) {
            if (capturedRequest instanceof HttpPost) {
                HttpPost capturedPostRequest = (HttpPost) capturedRequest;

            }
        }

        // sleep to allow for any "post-finished" activities on the replicator related to checkpointing
        Thread.sleep(2000);

        // make sure local checkpoint is not updated
        lastSequenceWithCheckpointIdFinal = database.lastSequenceWithCheckpointId(remoteCheckpointDocId);

        String msg = "since the mock replicator rejected the PUT to _local/remoteCheckpointDocId, we " +
                "would expect lastSequenceWithCheckpointIdInitial == lastSequenceWithCheckpointIdFinal";
        assertEquals(msg, lastSequenceWithCheckpointIdFinal, lastSequenceWithCheckpointIdInitial);

        Log.d(TAG, "replication done");


    }

    public void testServerIsSyncGatewayVersion() {
        Replication pusher = database.createPushReplication(getReplicationURL());
        assertFalse(pusher.serverIsSyncGatewayVersion("0.01"));
        pusher.setServerType("Couchbase Sync Gateway/0.93");
        assertTrue(pusher.serverIsSyncGatewayVersion("0.92"));
        assertFalse(pusher.serverIsSyncGatewayVersion("0.94"));
    }

    private void putReplicationOffline(Replication replication) throws InterruptedException {

        final CountDownLatch wentOffline = new CountDownLatch(1);
        Replication.ChangeListener offlineChangeListener = new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (!event.getSource().online) {
                    wentOffline.countDown();
                }
            }
        };
        replication.addChangeListener(offlineChangeListener);

        replication.goOffline();
        boolean succeeded = wentOffline.await(30, TimeUnit.SECONDS);
        assertTrue(succeeded);

        replication.removeChangeListener(offlineChangeListener);

    }

    private void putReplicationOnline(Replication replication) throws InterruptedException {

        final CountDownLatch wentOnline = new CountDownLatch(1);
        Replication.ChangeListener onlineChangeListener = new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if (event.getSource().online) {
                    wentOnline.countDown();
                }
            }
        };
        replication.addChangeListener(onlineChangeListener);

        replication.goOnline();
        boolean succeeded = wentOnline.await(30, TimeUnit.SECONDS);
        assertTrue(succeeded);

        replication.removeChangeListener(onlineChangeListener);

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
        pullerWithFilter1.setDocIds(Arrays.asList("doc3", "doc1", "doc2"));
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
     * Whenever posting information directly to sync gateway via HTTP, the client
     * must pause briefly to give it a chance to achieve internal consistency.
     *
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

