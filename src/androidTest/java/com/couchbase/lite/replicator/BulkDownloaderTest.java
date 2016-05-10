package com.couchbase.lite.replicator;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.WrappedSmartMockResponse;
import com.couchbase.lite.support.CouchbaseLiteHttpClientFactory;
import com.couchbase.lite.support.PersistentCookieStore;
import com.couchbase.lite.support.RemoteRequestCompletionBlock;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import org.apache.http.HttpResponse;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BulkDownloaderTest extends LiteTestCaseWithDB {

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/331
     */
    public void testErrorHandling() throws Exception {
        PersistentCookieStore cookieStore = database.getPersistentCookieStore();
        CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // _bulk_docs response -- 406 errors
            MockResponse mockResponse = new MockResponse().setResponseCode(406);
            WrappedSmartMockResponse mockBulkDocs = new WrappedSmartMockResponse(mockResponse, false);
            mockBulkDocs.setSticky(true);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            server.play();

            ScheduledExecutorService requestExecutorService = Executors.newScheduledThreadPool(5);
            ScheduledExecutorService workExecutorService = Executors.newSingleThreadScheduledExecutor();

            String urlString = String.format("%s/%s", server.getUrl("/db"), "_local");
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
            BulkDownloader bulkDownloader = new BulkDownloader(
                    factory,
                    url,
                    true,
                    revs,
                    database,
                    null,
                    new BulkDownloader.BulkDownloaderDocumentBlock() {
                        public void onDocument(Map<String, Object> props) {
                            // do nothing
                            Log.d(TAG, "onDocument called with %s", props);
                        }
                    },
                    new RemoteRequestCompletionBlock() {
                        public void onCompletion(HttpResponse httpResponse, Object result, Throwable e) {
                            Log.d(TAG, "RemoteRequestCompletionBlock called, result: %s e: %s", result, e);
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
        }finally {
            server.shutdown();
        }
    }
}
