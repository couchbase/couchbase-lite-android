/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.replicator;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.WrappedSmartMockResponse;
import com.couchbase.lite.support.CouchbaseLiteHttpClientFactory;
import com.couchbase.lite.support.PersistentCookieJar;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class BulkDownloaderTest extends LiteTestCaseWithDB {
    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/331
     */
    public void testErrorHandling() throws Exception {
        PersistentCookieJar cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
            // _bulk_docs response -- 406 errors
            MockResponse mockResponse = new MockResponse().setResponseCode(406);
            WrappedSmartMockResponse mockBulkDocs =
                    new WrappedSmartMockResponse(mockResponse, false);
            mockBulkDocs.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            server.start();

            ScheduledExecutorService requestExecutorService = Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService = Executors
                    .newSingleThreadScheduledExecutor();

            String urlString = String.format(Locale.ENGLISH, "%s/%s", server.url("/db").url(), "_local");
            URL url = new URL(urlString);

            // BulkDownloader expects to be given a list of RevisionInternal
            List<RevisionInternal> revs = new ArrayList<RevisionInternal>();
            Document doc = createDocumentForPushReplication("doc1", null, null);
            RevisionInternal revisionInternal = database.getDocument(doc.getId(),
                    doc.getCurrentRevisionId(), true);
            revs.add(revisionInternal);

            // countdown latch to make sure we got an error
            final CountDownLatch gotError = new CountDownLatch(1);

            // create a bulkdownloader
            RemoteBulkDownloaderRequest bulkDownloader = new RemoteBulkDownloaderRequest(
                    factory,
                    url,
                    true,
                    revs,
                    database,
                    null,
                    new RemoteBulkDownloaderRequest.BulkDownloaderDocument() {
                        public void onDocument(Map<String, Object> props) {
                            // do nothing
                            Log.d(TAG, "onDocument called with %s", props);
                        }
                    },
                    new RemoteRequestCompletion() {
                        public void onCompletion(Response httpResponse, Object result, Throwable e) {
                            Log.d(TAG, "RemoteRequestCompletionBlock called, result: %s e: %s",
                                    result, e);
                            if (e != null) {
                                gotError.countDown();
                            }
                        }
                    }
            );

            // submit the request
            Future future = requestExecutorService.submit(bulkDownloader);

            // make sure our callback was called with an error, since
            // we are returning a 4xx error to all _bulk_get requests
            boolean success = gotError.await(60, TimeUnit.SECONDS);
            assertTrue(success);

            // wait for the future to return
            future.get(300, TimeUnit.SECONDS);

            // Note: ExecutorService should be called shutdown()
            Utils.shutdownAndAwaitTermination(requestExecutorService);
            Utils.shutdownAndAwaitTermination(workExecutorService);
        } finally {
            assertTrue(MockHelper.shutdown(server, dispatcher));
        }
    }
}
