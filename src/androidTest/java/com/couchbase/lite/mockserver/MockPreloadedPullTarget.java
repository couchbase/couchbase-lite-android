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
package com.couchbase.lite.mockserver;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

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
