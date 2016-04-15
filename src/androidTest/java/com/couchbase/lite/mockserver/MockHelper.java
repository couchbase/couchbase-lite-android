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

import com.couchbase.lite.Manager;
import com.couchbase.lite.Misc;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Misc helper methods for MockWebserver-based Mock objects
 */
public class MockHelper {
    public static final String PATH_REGEX_CHECKPOINT = "/db/_local.*";
    public static final String PATH_REGEX_CHANGES = "/db/_changes.*";
    public static final String PATH_REGEX_CHANGES_NORMAL = "/db/_changes\\?feed=normal.*";
    public static final String PATH_REGEX_CHANGES_LONGPOLL = "/db/_changes\\?feed=longpoll.*";
    public static final String PATH_REGEX_REVS_DIFF = "/db/_revs_diff.*";
    public static final String PATH_REGEX_BULK_DOCS = "/db/_bulk_docs.*";
    public static final String PATH_REGEX_SESSION = "/db/_session.*";
    public static final String PATH_REGEX_SESSION_COUCHDB = "/_session.*";
    public static final String PATH_REGEX_FACEBOOK_AUTH = "/db/_facebook.*";
    public static final String PATH_REGEX_BULK_GET = "/db/_bulk_get.*";
    public static final String PATH_REGEX_ALL_DOCS = "/db/_all_docs.*";

    public static MockWebServer getMockWebServer(MockDispatcher dispatcher) {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        return server;
    }

    /**
     * Get a "preloaded" mock CouchDB suitable to be used as a pull replication target.
     * It's preloaded in the sense that it is ready serve up mock documents.
     *
     * This can't be used to simulate a Sync Gateway, because it does not support _bulk_get
     *
     * @param dispatcher the MockDispatcher
     * @param numMockDocsToServe how many docs should be served to pull replicator?
     * @param numDocsPerChangesResponse how many docs to add to each _changes response?  MAXINT for all.
     *
     */
    public static MockWebServer getPreloadedPullTargetMockCouchDB(MockDispatcher dispatcher,
                                                                  int numMockDocsToServe,
                                                                  int numDocsPerChangesResponse) {
        return new MockPreloadedPullTarget(dispatcher, numMockDocsToServe,
                numDocsPerChangesResponse).getMockWebServer();
    }

    public static void set200OKJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 200 OK")
                .setHeader("Content-Type", "application/json");
    }

    public static void set201OKJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 201 OK")
                .setHeader("Content-Type", "application/json");
    }

    public static void set404NotFoundJson(MockResponse mockResponse) {
        mockResponse.setStatus("HTTP/1.1 404 NOT FOUND")
                .setHeader("Content-Type", "application/json");
    }

    public static void addFake404CheckpointResponse(MockWebServer mockWebServer) {
        MockResponse fakeCheckpointResponse = new MockResponse();
        MockHelper.set404NotFoundJson(fakeCheckpointResponse);
        mockWebServer.enqueue(fakeCheckpointResponse);
    }


    public static Map<String, Object> generateRandomJsonMap() {

        Map<String, Object> randomJsonMap = new HashMap<String, Object>();
        randomJsonMap.put(Misc.CreateUUID(), false);
        randomJsonMap.put("uuid", Misc.CreateUUID());
        return randomJsonMap;
    }

    public static SmartMockResponse wrap(MockResponse mockResponse) {
        return new WrappedSmartMockResponse(mockResponse);
    }

    public static Map<String, Object> getJsonMapFromRequest(byte[] requestBody)
            throws IOException {
        return Manager.getObjectMapper().readValue(requestBody, Map.class);
    }

    /**
     * returns decompressed byte[] body
     */
    public static byte[] getUncompressedBody(RecordedRequest request){
        byte[] body = request.getBody();
        if(isGzip(request)){
            body = com.couchbase.lite.util.Utils.decompressByGzip(body);
        }
        return body;
    }

    /**
     * returns decompressed String body
     */
    public static String getUtf8Body(RecordedRequest request) throws Exception{
        return new String(getUncompressedBody(request), "UTF-8");
    }

    /*
     * check if gzip is used for request body
     */
    public static boolean isGzip(RecordedRequest request){
        return request.getHeader("Content-Encoding") != null &&
               request.getHeader("Content-Encoding").contains("gzip");
    }

    public static class Batcher<T> {

        private BlockingQueue<T> items;
        private int batchSize;

        public Batcher(List<T> items, int batchSize) {
            this.items = new LinkedBlockingQueue<T>(items);
            this.batchSize = batchSize;
        }

        public boolean hasMoreBatches() {
            return !this.items.isEmpty();
        }

        public List<T> nextBatch() {
            List<T> batch = new ArrayList<T>();
            for (int i=0; i<batchSize; i++) {
                if (!this.items.isEmpty()) {
                    try {
                        T item = this.items.take();
                        batch.add(item);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return batch;
        }
    }

    public static List<MockDocumentGet.MockDocument> getMockDocuments(int numDocs) {
        List<MockDocumentGet.MockDocument> mockDocs = new ArrayList<MockDocumentGet.MockDocument>();
        for (int i=0; i<numDocs; i++) {
            String docId = String.format("doc%s", i);
            String revIdHash = Misc.CreateUUID().substring(0, 4);
            String revId = String.format("1-%s", revIdHash);
            int seq = i;
            // mock documents to be pulled
            MockDocumentGet.MockDocument mockDoc =
                    new MockDocumentGet.MockDocument(docId, revId, seq);
            mockDoc.setJsonMap(MockHelper.generateRandomJsonMap());
            mockDocs.add(mockDoc);
        }
        return mockDocs;
    }
}