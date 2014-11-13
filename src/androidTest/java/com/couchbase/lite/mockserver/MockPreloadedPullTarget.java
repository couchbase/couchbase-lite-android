package com.couchbase.lite.mockserver;

import com.couchbase.lite.Misc;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.util.ArrayList;
import java.util.List;

public class MockPreloadedPullTarget {

    protected MockDispatcher dispatcher;
    protected int numMockDocsToServe;
    protected int numDocsPerChangesResponse;


    public MockPreloadedPullTarget(MockDispatcher dispatcher, int numMockDocsToServe, int numDocsPerChangesResponse) {
        this.dispatcher = dispatcher;
        this.numMockDocsToServe = numMockDocsToServe;
        this.numDocsPerChangesResponse = numDocsPerChangesResponse;
    }

    public MockWebServer getMockWebServer() {

        MockWebServer server = MockHelper.getMockWebServer(dispatcher);

        List<MockDocumentGet.MockDocument> mockDocs = MockHelper.getMockDocuments(numMockDocsToServe);

        addCheckpointResponse();

        addChangesResponse(mockDocs);

        addMockDocuments(mockDocs);

        return server;
    }

    protected void addMockDocuments(List<MockDocumentGet.MockDocument> mockDocs) {
        // doc responses
        for (MockDocumentGet.MockDocument mockDoc : mockDocs) {
            MockDocumentGet mockDocumentGet = new MockDocumentGet(mockDoc);
            dispatcher.enqueueResponse(mockDoc.getDocPathRegex(), mockDocumentGet.generateMockResponse());
        }
    }

    protected void addChangesResponse(List<MockDocumentGet.MockDocument> mockDocs) {
        // _changes response
        int numChangeResponses = 0;
        MockHelper.Batcher<MockDocumentGet.MockDocument> batcher =
                new MockHelper.Batcher<MockDocumentGet.MockDocument>(mockDocs, numDocsPerChangesResponse);
        while (batcher.hasMoreBatches()) {
            List<MockDocumentGet.MockDocument> batch = batcher.nextBatch();
            MockChangesFeed mockChangesFeed = new MockChangesFeed();
            for (MockDocumentGet.MockDocument mockDoc : batch) {
                mockChangesFeed.add(new MockChangesFeed.MockChangedDoc(mockDoc));
            }
            MockResponse mockResponse = mockChangesFeed.generateMockResponse();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockResponse);
            numChangeResponses += 1;
        }
        if (numChangeResponses == 0) {
            // in the degenerate case, add empty changes response
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, new MockChangesFeed().generateMockResponse());
        }
    }

    protected void addCheckpointResponse() {
        // checkpoint GET response w/ 404.  also receives checkpoint PUT's
        MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
        mockCheckpointPut.setSticky(true);
        dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);
    }



}
