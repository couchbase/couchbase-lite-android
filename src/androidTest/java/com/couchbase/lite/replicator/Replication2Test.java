package com.couchbase.lite.replicator;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.mockserver.MockBulkDocs;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.MockRevsDiff;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by hideki on 1/26/15.
 */
public class Replication2Test  extends LiteTestCase {

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/328
     *
     * Without bug fix, we observe extra PUT /{db}/_local/xxx for each _bulk_docs request
     *
     * 1. Create 200 docs
     * 2. Start push replicator
     * 3. GET  /{db}/_local/xxx
     * 4. PUSH /{db}/_revs_diff x 2
     * 5. PUSH /{db}/_bulk_docs x 2
     * 6. PUT  /{db}/_local/xxx
     */
    public void testExcessiveCheckpointingDuringPushReplication() throws Exception {

        List<Document> docs = new ArrayList<Document>();

        // 1. Add 200 local documents
        for(int i = 0; i < 200; i++) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("testExcessiveCheckpointingDuringPushReplication", String.valueOf(i));
            Document doc = createDocumentWithProperties(database, properties);
            docs.add(doc);
        }

        // create mock server
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
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
        String expectedLastSequence = "200";
        while (!foundCheckpointPut) {
            RecordedRequest request = dispatcher.takeRequestBlocking(MockHelper.PATH_REGEX_CHECKPOINT);
            if (request.getMethod().equals("PUT")) {
                foundCheckpointPut = true;
                String body = request.getUtf8Body();
                Log.e("testExcessiveCheckpointingDuringPushReplication", "body => " + body);
                // TODO: this is not valid if device can not handle all replication data at once
                if(System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {
                    assertTrue(body.indexOf(expectedLastSequence) != -1);
                }
                // wait until mock server responds to the checkpoint PUT request
                dispatcher.takeRecordedResponseBlocking(request);
            }
        }

        // make some assertions about the outgoing _bulk_docs requests
        RecordedRequest bulkDocsRequest1 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
        assertNotNull(bulkDocsRequest1);

        if(System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {

            // TODO: Need to fix
            RecordedRequest bulkDocsRequest2 = dispatcher.takeRequest(MockHelper.PATH_REGEX_BULK_DOCS);
            assertNotNull(bulkDocsRequest2);

            // TODO: Need to fix: https://github.com/couchbase/couchbase-lite-java-core/issues/446
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

        server.shutdown();
    }
}
