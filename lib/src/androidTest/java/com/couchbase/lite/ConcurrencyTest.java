//
// ConcurrencyUnitTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.couchbase.lite.utils.ConcurrencyUnitTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class ConcurrencyTest extends BaseTest {

    interface Callback {
        void callback(int threadIndex);
    }

    interface VerifyBlock<T> {
        void verify(int n, T result);
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentCreate() throws CouchbaseLiteException {
        final int kNDocs = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            threadIndex -> {
                String tag = "tag-" + threadIndex;
                try { createDocs(kNDocs, tag); }
                catch (CouchbaseLiteException e) { fail(); }
            },
            kWaitInSec);

        // validate stored documents
        for (int i = 0; i < kNThreads; i++) { verifyByTagName("tag-" + i, kNDocs); }
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentCreateInBatch() throws CouchbaseLiteException {
        final int kNDocs = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            threadIndex -> {
                final String tag = "tag-" + threadIndex;
                try {
                    db.inBatch(() -> {
                        try { createDocs(kNDocs, tag); }
                        catch (CouchbaseLiteException e) { fail(); }
                    });
                }
                catch (CouchbaseLiteException e) { fail(); }
            },
            kWaitInSec);

        checkForFailure();

        // validate stored documents
        for (int i = 0; i < kNThreads; i++) { verifyByTagName("tag-" + i, kNDocs); }
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentUpdate() throws CouchbaseLiteException {
        // NOTE: By increasing number of threads, update causes crash!
        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 600;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            threadIndex -> {
                String tag = "tag-" + threadIndex;
                assertTrue(updateDocs(docIDs, kNRounds, tag));
            },
            kWaitInSec);

        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < kNThreads; i++) { verifyByTagName("tag-" + i, (n, result) -> count.incrementAndGet()); }

        assertEquals(kNDocs, count.intValue());
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentRead() throws CouchbaseLiteException {
        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(kNThreads, threadIndex -> readDocs(docIDs, kNRounds), kWaitInSec);
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentReadInBatch() throws CouchbaseLiteException {
        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            threadIndex -> {
                try { db.inBatch(() -> readDocs(docIDs, kNRounds)); }
                catch (CouchbaseLiteException e) { fail(); }
            },
            kWaitInSec);
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentReadNUpdate() throws InterruptedException, CouchbaseLiteException {
        final int kNDocs = 5;
        final int kNRounds = 50;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // Read:
        final CountDownLatch latch1 = new CountDownLatch(1);
        runSafelyInThread(latch1, () -> readDocs(docIDs, kNRounds));

        // Update:
        final CountDownLatch latch2 = new CountDownLatch(1);
        final String tag = "Update";
        runSafelyInThread(latch2, () -> assertTrue(updateDocs(docIDs, kNRounds, tag)));

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();

        verifyByTagName(tag, kNDocs);
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentDelete() throws InterruptedException, CouchbaseLiteException {
        final int kNDocs = 100;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        final CountDownLatch latch1 = new CountDownLatch(1);
        runSafelyInThread(
            latch1,
            () -> {
                for (String docID : docIDs) {
                    try {
                        Document doc = db.getDocument(docID);
                        if (doc != null) { db.delete(doc); }
                    }
                    catch (CouchbaseLiteException e) { fail(); }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        runSafelyInThread(
            latch2,
            () -> {
                for (String docID : docIDs) {
                    try {
                        Document doc = db.getDocument(docID);
                        if (doc != null) { db.delete(doc); }
                    }
                    catch (CouchbaseLiteException e) { fail(); }
                }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();

        assertEquals(0, db.getCount());
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentPurge() throws InterruptedException, CouchbaseLiteException {
        final int nDocs = 100;

        // createDocs returns synchronized List.
        final List<String> docIDs = createDocs(nDocs, "Create");
        assertEquals(nDocs, docIDs.size());

        final CountDownLatch latch1 = new CountDownLatch(1);
        runSafelyInThread(
            latch1,
            () -> {
                for (String docID : docIDs) {
                    Document doc = db.getDocument(docID);
                    if (doc != null) {
                        try { db.purge(doc); }
                        catch (CouchbaseLiteException e) { assertEquals(404, e.getCode()); }
                    }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        runSafelyInThread(
            latch2,
            () -> {
                for (String docID : docIDs) {
                    Document doc = db.getDocument(docID);
                    if (doc != null) {
                        try { db.purge(doc); }
                        catch (CouchbaseLiteException e) { assertEquals(404, e.getCode()); }
                    }
                }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();

        assertEquals(0, db.getCount());
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentCreateNCloseDB() throws InterruptedException {
        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        runSafelyInThread(
            latch1,
            () -> {
                try { createDocs(kNDocs, tag1); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
                // db not open
                catch (IllegalStateException ignore) { }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        runSafelyInThread(
            latch2,
            () -> {
                try { db.close(); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentCreateNDeleteDB() throws InterruptedException {
        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        runSafelyInThread(
            latch1,
            () -> {
                try { createDocs(kNDocs, tag1); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
                // db not open
                catch (IllegalStateException ignore) { }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        runSafelyInThread(
            latch2,
            () -> {
                try { db.delete(); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentCreateNCompactDB() throws InterruptedException {
        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        runSafelyInThread(
            latch1,
            () -> {
                try { createDocs(kNDocs, "Create1"); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        runSafelyInThread(
            latch2,
            () -> {
                try { db.compact(); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    @ConcurrencyUnitTest
    public void testConcurrentCreateNCreateIndexDB() throws Exception {
        loadJSONResource("sentences.json");

        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        runSafelyInThread(
            latch1,
            () -> {
                try { createDocs(kNDocs, "Create1"); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        runSafelyInThread(
            latch2,
            () -> {
                try {
                    Index index = IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence"));
                    db.createIndex("sentence", index);
                }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    @ConcurrencyUnitTest
    public void testBlockDatabaseChange() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        db.addChangeListener(executor, change -> latch2.countDown());

        runSafelyInThread(
            latch1,
            () -> {
                try { db.save(new MutableDocument("doc1")); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    @ConcurrencyUnitTest
    public void testBlockDocumentChange() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        db.addDocumentChangeListener("doc1", change -> latch2.countDown());

        runSafelyInThread(
            latch1,
            () -> {
                try { db.save(new MutableDocument("doc1")); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(180, TimeUnit.SECONDS));
        assertTrue(latch2.await(180, TimeUnit.SECONDS));
        checkForFailure();
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1407
    @Test
    @ConcurrencyUnitTest
    public void testQueryExecute() throws Exception {
        loadJSONResource("names_100.json");

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.sequence))
            .from(DataSource.database(db));

        concurrentValidator(
            10,
            threadIndex -> {
                ResultSet rs = null;

                try { rs = query.execute(); }
                catch (CouchbaseLiteException e) { fail(); }

                List<Result> results = rs.allResults();

                assertEquals(100, results.size());
                assertEquals(db.getCount(), results.size());
            },
            180);
    }

    private MutableDocument createDocumentWithTag(String tag) {
        MutableDocument doc = new MutableDocument();

        // Tag
        doc.setValue("tag", tag);

        // String
        doc.setValue("firstName", "Daniel");
        doc.setValue("lastName", "Tiger");

        // Dictionary:
        MutableDictionary address = new MutableDictionary();
        address.setValue("street", "1 Main street");
        address.setValue("city", "Mountain View");
        address.setValue("state", "CA");
        doc.setValue("address", address);

        // Array:
        MutableArray phones = new MutableArray();
        phones.addValue("650-123-0001");
        phones.addValue("650-123-0002");
        doc.setValue("phones", phones);

        // Date:
        doc.setValue("updated", new Date());

        return doc;
    }

    private List<String> createDocs(int nDocs, String tag) throws CouchbaseLiteException {
        List<String> docs = Collections.synchronizedList(new ArrayList<>(nDocs));
        for (int i = 0; i < nDocs; i++) {
            MutableDocument doc = createDocumentWithTag(tag);
            Document saved = save(doc);
            docs.add(saved.getId());
        }
        return docs;
    }

    private boolean updateDocs(List<String> docIds, int rounds, String tag) {
        for (int i = 1; i <= rounds; i++) {
            for (String docId : docIds) {
                Document d = db.getDocument(docId);
                MutableDocument doc = d.toMutable();
                doc.setValue("tag", tag);

                MutableDictionary address = doc.getDictionary("address");
                assertNotNull(address);
                String street = String.format(Locale.ENGLISH, "%d street.", i);
                address.setValue("street", street);

                MutableArray phones = doc.getArray("phones");
                assertNotNull(phones);
                assertEquals(2, phones.count());
                String phone = String.format(Locale.ENGLISH, "650-000-%04d", i);
                phones.setValue(0, phone);

                doc.setValue("updated", new Date());
                try { db.save(doc); }
                catch (CouchbaseLiteException e) { return false; }
            }
        }
        return true;
    }

    private void readDocs(List<String> docIDs, int rounds) {
        for (int i = 1; i <= rounds; i++) {
            for (String docID : docIDs) {
                Document doc = db.getDocument(docID);
                assertNotNull(doc);
                assertEquals(docID, doc.getId());
            }
        }
    }

    private void verifyByTagName(String tag, VerifyBlock<Result> block) throws CouchbaseLiteException {
        Expression TAG_EXPR = Expression.property("tag");
        SelectResult DOCID = SelectResult.expression(Meta.id);
        DataSource ds = DataSource.database(db);
        Query q = QueryBuilder.select(DOCID).from(ds).where(TAG_EXPR.equalTo(Expression.string(tag)));
        ResultSet rs = q.execute();
        Result result;
        int n = 0;
        while ((result = rs.next()) != null) { block.verify(++n, result); }
    }

    private void verifyByTagName(String tag, int nRows) throws CouchbaseLiteException {
        final AtomicInteger count = new AtomicInteger(0);
        verifyByTagName(tag, (n, result) -> count.incrementAndGet());
        assertEquals(nRows, count.intValue());
    }

    private void concurrentValidator(final int nThreads, final Callback callback, final int waitSec) {
        // setup
        final Thread[] threads = new Thread[nThreads];
        final CountDownLatch[] latches = new CountDownLatch[nThreads];

        for (int i = 0; i < nThreads; i++) {
            final int counter = i;
            latches[i] = new CountDownLatch(1);
            threads[i] = new Thread(
                () -> runSafely(
                    () -> {
                        callback.callback(counter);
                        latches[counter].countDown();
                    }),
                "Thread-" + i);
        }

        // start
        for (int i = 0; i < nThreads; i++) { threads[i].start(); }

        // wait
        for (int i = 0; i < nThreads; i++) {
            try { assertTrue(latches[i].await(waitSec, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { fail(); }
        }

        checkForFailure();
    }
}
