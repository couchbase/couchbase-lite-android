/**
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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

package com.couchbase.lite.replicator;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.mockserver.MockChangesFeed;
import com.couchbase.lite.mockserver.MockChangesFeedNoResponse;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockDocumentBulkGet;
import com.couchbase.lite.mockserver.MockDocumentGet;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by hideki on 12/15/15.
 */
public class ReplicationInternalTest extends LiteTestCaseWithDB {

    // one shot pull replication with _bulk_get - successful scenario
    public void testOneShotPullReplBulkGet() throws Exception {
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);


            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(false);

            //add change listener to notify when the replication is finished
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationIdleSignal));

            //start replication
            pull.start();

            boolean success = replicationIdleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            if (pull.getLastError() != null)
                Log.d(TAG, "Replication had error: ", pull.getLastError());
            assertNull(pull.getLastError());

            //assert document 1 was correctly pulled
            Document doc1 = database.getDocument(doc1Id);
            assertNotNull(doc1);
            assertNotNull(doc1.getCurrentRevision());
            assertEquals("1-0001", doc1.getCurrentRevision().getId());

            //assert it was impossible to pull doc2
            Document doc2 = database.getDocument(doc2Id);
            assertNotNull(doc2);
            assertEquals("1-0002", doc2.getCurrentRevision().getId());

            //assert it was possible to pull doc3
            Document doc3 = database.getDocument(doc3Id);
            assertNotNull(doc3);
            assertEquals("1-0003", doc3.getCurrentRevision().getId());

            // wait until the replicator PUT's checkpoint with mockDocument3's sequence
            waitForPutCheckpointRequestWithSeq(dispatcher, mockDocument3.getDocSeq());

            //last saved seq must be equal to last pulled document seq
            String doc3Seq = Integer.toString(mockDocument3.getDocSeq());
            String lastSequence = database.lastSequenceWithCheckpointId(pull.remoteCheckpointDocID());
            assertEquals(doc3Seq, lastSequence);
        } finally {
            server.shutdown();
        }
    }

    // continuous pull replication with _bulk_get - successful scenario
    public void testContPullReplBulkGet() throws Exception {
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // add sticky _changes response to feed=longpoll that just blocks for 60 seconds to emulate
            // server that doesn't have any new changes
            MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse.setDelayMs(60 * 1000);
            mockChangesFeedNoResponse.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);

            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationIdleObserver(replicationIdleSignal));
            CountDownLatch replicationDoneSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));

            pull.start();
            assertTrue(replicationIdleSignal.await(30, TimeUnit.SECONDS));
            pull.stop();
            assertTrue(replicationDoneSignal.await(30, TimeUnit.SECONDS));

            if (pull.getLastError() != null)
                Log.d(TAG, "Replication had error: ", pull.getLastError());
            assertNull(pull.getLastError());

            Document doc1 = database.getDocument(doc1Id);
            assertNotNull(doc1);
            assertNotNull(doc1.getCurrentRevision());
            assertEquals("1-0001", doc1.getCurrentRevision().getId());
            Document doc2 = database.getDocument(doc2Id);
            assertNotNull(doc2);
            assertNotNull(doc2.getCurrentRevision());
            assertEquals("1-0002", doc2.getCurrentRevision().getId());
            Document doc3 = database.getDocument(doc3Id);
            assertNotNull(doc3);
            assertNotNull(doc3.getCurrentRevision());
            assertEquals("1-0003", doc3.getCurrentRevision().getId());

            waitForPutCheckpointRequestWithSeq(dispatcher, mockDocument3.getDocSeq());

            String doc3Seq = Integer.toString(mockDocument3.getDocSeq());
            String lastSequence = database.lastSequenceWithCheckpointId(pull.remoteCheckpointDocID());
            assertEquals(doc3Seq, lastSequence);
        } finally {
            server.shutdown();
        }
    }

    /*
        // Sample response for _bulk_get with missing revision
        curl -i -H "Content-Type: application/json" -X POST -d '{"docs":[{"atts_since":null,"id":"5b344b64-0aaa-4a24-806e-d7e6abc771a7","rev":"2-ceaee13c1bae76a11ebfa9371009ae8d"}]}' http://localhost:4985/db/_bulk_get?revs=true&attachments=true

        HTTP/1.1 200 OK
        Content-Type: multipart/mixed; boundary="be4a1c7b5e3b180aade8a7753fd285a54e55415314ddc1134ceb36021f7e"
        Server: Couchbase Sync Gateway/1.2 branch/master commit/9bc164c+CHANGES
        Date: Wed, 16 Dec 2015 02:35:08 GMT
        Content-Length: 320

        --be4a1c7b5e3b180aade8a7753fd285a54e55415314ddc1134ceb36021f7e
        Content-Type: application/json; error="true"

        {"error":"not_found","id":"5b344b64-0aaa-4a24-806e-d7e6abc771a7","reason":"missing","rev":"2-ceaee13c1bae76a11ebfa9371009ae8d","status":404}
        --be4a1c7b5e3b180aade8a7753fd285a54e55415314ddc1134ceb36021f7e--
    */
    // continuous pull replication with _bulk_get with missing revision - erro scenario
    public void testOneShotPullReplBulkGetWithMissingRev() throws Exception {
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockDocument2.setMissing(true);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(false);

            //add change listener to notify when the replication is finished
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationIdleSignal));

            //start replication
            pull.start();

            boolean success = replicationIdleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            if (pull.getLastError() != null)
                Log.d(TAG, "Replication had error: ", pull.getLastError());
            assertNull(pull.getLastError());

            assertEquals(2, database.getDocumentCount());

            //assert document 1 was correctly pulled
            Document doc1 = database.getDocument(doc1Id);
            assertNotNull(doc1);
            assertNotNull(doc1.getCurrentRevision());
            assertEquals("1-0001", doc1.getCurrentRevision().getId());

            //assert it was possible to pull doc3
            Document doc3 = database.getDocument(doc3Id);
            assertNotNull(doc3);
            assertEquals("1-0003", doc3.getCurrentRevision().getId());
        } finally {
            server.shutdown();
        }
    }

    // continuous pull replication with _bulk_get with missing revision - erro scenario
    public void testContPullReplBulkGetWithMissingRev() throws Exception {
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // add sticky _changes response to feed=longpoll that just blocks for 60 seconds to emulate
            // server that doesn't have any new changes
            MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse.setDelayMs(60 * 1000);
            mockChangesFeedNoResponse.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockDocument2.setMissing(true);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);

            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationIdleObserver(replicationIdleSignal));
            CountDownLatch replicationDoneSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));

            pull.start();
            assertTrue(replicationIdleSignal.await(30, TimeUnit.SECONDS));
            pull.stop();
            assertTrue(replicationDoneSignal.await(30, TimeUnit.SECONDS));

            if (pull.getLastError() != null)
                Log.d(TAG, "Replication had error: ", pull.getLastError());
            assertNull(pull.getLastError());

            assertEquals(2, database.getDocumentCount());

            Document doc1 = database.getDocument(doc1Id);
            assertNotNull(doc1);
            assertNotNull(doc1.getCurrentRevision());
            assertEquals("1-0001", doc1.getCurrentRevision().getId());
            Document doc3 = database.getDocument(doc3Id);
            assertNotNull(doc3);
            assertNotNull(doc3.getCurrentRevision());
            assertEquals("1-0003", doc3.getCurrentRevision().getId());
        } finally {
            server.shutdown();
        }
    }

    // one shot pull replication with _bulk_get - 404 should stop immediately - error scenario
    public void testOneShotPullReplBulkGetWith404() throws Exception {
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            mockBulkGet.set404(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(false);

            //add change listener to notify when the replication is finished
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationIdleSignal));

            //start replication
            pull.start();

            boolean success = replicationIdleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            assertNotNull(pull.getLastError());
            Log.d(TAG, "Replication had error: ", pull.getLastError());

            assertEquals(0, database.getDocumentCount());
        } finally {
            server.shutdown();
        }
    }


    // continuous pull replication with _bulk_get - 404 should stop immediately - error scenario
    public void testContPullReplBulkGetWith404() throws Exception {
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // add sticky _changes response to feed=longpoll that just blocks for 60 seconds to emulate
            // server that doesn't have any new changes
            MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse.setDelayMs(60 * 1000);
            mockChangesFeedNoResponse.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            mockBulkGet.set404(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);

            CountDownLatch replicationDoneSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));

            pull.start();
            assertTrue(replicationDoneSignal.await(30, TimeUnit.SECONDS));

            assertNotNull(pull.getLastError());
            Log.d(TAG, "Replication had error: ", pull.getLastError());

            assertEquals(0, database.getDocumentCount());

        } finally {
            server.shutdown();
        }
    }

    public void testServerIsSyncGatewayVersion() {

        // sync gateway 1.2
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.2 branch/fix/server_header commit/5bfcf79+CHANGES", "1.3"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.2 branch/fix/server_header commit/5bfcf79+CHANGES", "1.2"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.2 branch/fix/server_header commit/5bfcf79+CHANGES", "1.1"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.2 branch/fix/server_header commit/5bfcf79+CHANGES", "0.93"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.2 branch/fix/server_header commit/5bfcf79+CHANGES", "0.92"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.2 branch/fix/server_header commit/5bfcf79+CHANGES", "0.81"));

        // sync gateway 1.0 or 1.1
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.0", "1.3"));
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.0", "1.2"));
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.0", "1.1"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.0", "0.93"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.0", "0.92"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/1.0", "0.81"));

        // before 1.0 release (beta)
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/0.92", "1.3"));
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/0.92", "1.2"));
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/0.92", "1.1"));
        assertFalse(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/0.92", "0.93"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/0.92", "0.92"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/0.92", "0.81"));

        // developer build 1.1 or earlier ('a' > '0')
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/unofficial", "1.3"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/unofficial", "1.2"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/unofficial", "1.1"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/unofficial", "0.93"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/unofficial", "0.92"));
        assertTrue(ReplicationInternal.serverIsSyncGatewayVersion("Couchbase Sync Gateway/unofficial", "0.81"));
    }

    public void testUserAgent() throws Exception{
        String doc1Id = "doc1";
        String doc2Id = "doc2";
        String doc3Id = "doc3";

        final MockDocumentGet.MockDocument mockDocument1 = new MockDocumentGet.MockDocument(doc1Id, "1-0001", 1);
        mockDocument1.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument2 = new MockDocumentGet.MockDocument(doc2Id, "1-0002", 2);
        mockDocument2.setJsonMap(MockHelper.generateRandomJsonMap());
        final MockDocumentGet.MockDocument mockDocument3 = new MockDocumentGet.MockDocument(doc3Id, "1-0003", 3);
        mockDocument3.setJsonMap(MockHelper.generateRandomJsonMap());

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

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

            // _bulk_get response for odd indexed documents
            MockDocumentBulkGet mockBulkGet = new MockDocumentBulkGet();
            mockBulkGet.addDocument(mockDocument1);
            mockBulkGet.addDocument(mockDocument2);
            mockBulkGet.addDocument(mockDocument3);
            mockBulkGet.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // checkpoint PUT response
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);


            // start mock server
            server.play();

            //create replication
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(false);

            //add change listener to notify when the replication is finished
            CountDownLatch replicationIdleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(replicationIdleSignal));

            //start replication
            pull.start();

            boolean success = replicationIdleSignal.await(30, TimeUnit.SECONDS);
            assertTrue(success);

            if (pull.getLastError() != null)
                Log.d(TAG, "Replication had error: ", pull.getLastError());
            assertNull(pull.getLastError());

            // wait until the replicator PUT's checkpoint with mockDocument3's sequence
            waitForPutCheckpointRequestWithSeq(dispatcher, mockDocument3.getDocSeq());

            // Check User-Agent Header value for /_changes and /_bulk_get
            // CheckPoint is validated by waitForPutCheckpointRequestWithSeq() method
            checkUserAgent(dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHANGES));
            checkUserAgent(dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_BULK_GET));
        } finally {
            server.shutdown();
        }
    }

    public void testStopReplication() throws Exception {
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

            // add sticky _changes response to feed=longpoll that just blocks for 60 seconds to emulate
            // server that doesn't have any new changes
            MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse.setDelayMs(120 * 1000);
            mockChangesFeedNoResponse.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES_LONGPOLL, mockChangesFeedNoResponse);

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
            mockBulkGet.setDelayMs(120*1000);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_GET, mockBulkGet);

            // Respond to all PUT Checkpoint requests
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(500);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // Run pull replication:
            Replication pull = database.createPullReplication(server.getUrl("/db"));
            pull.setContinuous(true);

            final CountDownLatch doneSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationFinishedObserver(doneSignal));

            final CountDownLatch idleSignal = new CountDownLatch(1);
            pull.addChangeListener(new ReplicationIdleObserver(idleSignal));

            final CountDownLatch changeSignal = new CountDownLatch(1);
            pull.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    if (event.getChangeCount() > 0)
                        changeSignal.countDown();
                }
            });

            pull.start();

            // wait until idle
            assertTrue(changeSignal.await(30, TimeUnit.SECONDS));

            pull.stop();

            assertTrue(doneSignal.await(30, TimeUnit.SECONDS));
        } finally {
            if (server != null)
                server.shutdown();
        }
    }

}
