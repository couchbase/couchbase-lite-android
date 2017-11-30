package com.couchbase.lite;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorNotOpen;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConcurrentDatabaseTest extends BaseTest {
    private static final String TAG = ConcurrentDatabaseTest.class.getSimpleName();

    MutableDocument createDocumentWithTag(String tag) {
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

    List<String> createDocs(int nDocs, String tag) throws CouchbaseLiteException {
        List<String> docs = Collections.synchronizedList(new ArrayList<String>(nDocs));
        for (int i = 0; i < nDocs; i++) {
            MutableDocument doc = createDocumentWithTag(tag);
            db.save(doc);
            docs.add(doc.getId());
        }
        return docs;
    }

    boolean updateDocs(List<String> docIds, int rounds, String tag) {
        for (int i = 1; i <= rounds; i++) {
            for (String docId : docIds) {
                MutableDocument doc = db.getDocument(docId).toMutable();
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

                Log.i(TAG, "[%s] rounds: %d updating %s", tag, i, doc.getId());
                try {
                    db.save(doc);
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in Database.save()", e);
                    return false;
                }
            }
        }
        return true;
    }

    void readDocs(List<String> docIDs, int rounds) {
        for (int i = 1; i <= rounds; i++) {
            for (String docID : docIDs) {
                Document doc = db.getDocument(docID);
                assertNotNull(doc);
                assertEquals(docID, doc.getId());
            }
        }
    }

    interface VerifyBlock<T> {
        void verify(int n, T result);
    }

    void verifyByTagName(String tag, VerifyBlock block) throws CouchbaseLiteException {
        Expression TAG_EXPR = Expression.property("tag");
        SelectResult DOCID = SelectResult.expression(Expression.meta().getId());
        DataSource ds = DataSource.database(db);
        Query q = Query.select(DOCID).from(ds).where(TAG_EXPR.equalTo(tag));
        Log.e(TAG, "query - > %s", q.explain());
        ResultSet rs = q.run();
        Result result;
        int n = 0;
        while ((result = rs.next()) != null)
            block.verify(++n, result);
    }

    void verifyByTagName(String tag, int nRows) throws CouchbaseLiteException {
        final AtomicInteger count = new AtomicInteger(0);
        verifyByTagName(tag, new VerifyBlock<Result>() {
            @Override
            public void verify(int n, Result result) {
                count.incrementAndGet();
            }
        });
        assertEquals(nRows, count.intValue());
    }

    interface Callback {
        void callback(int threadIndex);
    }

    private void concurrentValidator(final int nThreads, final Callback callback, final int waitSec) {
        // setup
        final Thread[] threads = new Thread[nThreads];
        final CountDownLatch[] latchs = new CountDownLatch[nThreads];
        for (int i = 0; i < nThreads; i++) {
            final Integer counter = Integer.valueOf(i);
            latchs[i] = new CountDownLatch(1);
            String threadName = String.format(Locale.ENGLISH, "Thread-%d", i);
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    callback.callback(counter);
                    latchs[counter].countDown();
                }
            }, threadName);
        }

        // start
        for (int i = 0; i < nThreads; i++)
            threads[i].start();

        // wait
        for (int i = 0; i < nThreads; i++) {
            try {
                assertTrue(latchs[i].await(waitSec, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Log.e(TAG, "InterruptedException", e);
                fail();
            }
        }
    }

    @Test
    public void testConcurrentCreate() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 60;

        // concurrently creates documents
        concurrentValidator(kNThreads, new Callback() {
            @Override
            public void callback(int threadIndex) {
                String tag = "tag-" + threadIndex;
                try {
                    createDocs(kNDocs, tag);
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in createDocs()", e);
                    fail();
                }
            }
        }, kWaitInSec);

        // validate stored documents
        for (int i = 0; i < kNThreads; i++)
            verifyByTagName("tag-" + i, kNDocs);
    }

    @Test
    public void testConcurrentCreateInBatch() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 60;

        // concurrently creates documents
        concurrentValidator(kNThreads, new Callback() {
            @Override
            public void callback(int threadIndex) {
                final String tag = "tag-" + threadIndex;
                try {
                    db.inBatch(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                createDocs(kNDocs, tag);
                            } catch (CouchbaseLiteException e) {
                                Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", e, kNDocs, tag);
                                fail();
                            }
                        }
                    });
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in inBatch()", e);
                    fail();
                }
            }
        }, kWaitInSec);

        // validate stored documents
        for (int i = 0; i < kNThreads; i++)
            verifyByTagName("tag-" + i, kNDocs);
    }

    @Test
    public void testConcurrentUpdate() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        // NOTE: By increasing number of threads, update causes crash!
        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 60;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(kNThreads, new Callback() {
            @Override
            public void callback(int threadIndex) {
                String tag = "tag-" + threadIndex;
                assertTrue(updateDocs(docIDs, kNRounds, tag));
            }
        }, kWaitInSec);

        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < kNThreads; i++) {
            verifyByTagName("tag-" + i, new VerifyBlock<Result>() {
                @Override
                public void verify(int n, Result result) {
                    count.incrementAndGet();
                }
            });
        }

        assertEquals(kNDocs, count.intValue());
    }

    @Test
    public void testConcurrentRead() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 60;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(kNThreads, new Callback() {
            @Override
            public void callback(int threadIndex) {
                readDocs(docIDs, kNRounds);
            }
        }, kWaitInSec);
    }

    @Test
    public void testConcurrentReadInBatch() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 60;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(kNThreads, new Callback() {
            @Override
            public void callback(int threadIndex) {
                try {
                    db.inBatch(new Runnable() {
                        @Override
                        public void run() {
                            readDocs(docIDs, kNRounds);
                        }
                    });
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in inBatch()", e);
                    fail();
                }
            }
        }, kWaitInSec);
    }

    @Test
    public void testConcurrentReadNUpdate() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 5;
        final int kNRounds = 50;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // Read:
        final CountDownLatch latch1 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                readDocs(docIDs, kNRounds);
                latch1.countDown();
            }
        }).start();

        // Update:
        final CountDownLatch latch2 = new CountDownLatch(1);
        final String tag = "Update";
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(updateDocs(docIDs, kNRounds, tag));
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));

        verifyByTagName(tag, kNDocs);
    }

    @Test
    public void testConcurrentDelete() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 100;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        final CountDownLatch latch1 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String docID : docIDs) {
                    try {
                        Document doc = db.getDocument(docID);
                        db.delete(doc);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Database.delete(Document)", e);
                        fail();
                    }
                }
                latch1.countDown();
            }
        }).start();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String docID : docIDs) {
                    try {
                        db.delete(db.getDocument(docID));
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Database.delete(Document)", e);
                        fail();
                    }
                }
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));

        assertEquals(0, db.getCount());
    }

    @Test
    public void testConcurrentPurge() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 100;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        final CountDownLatch latch1 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String docID : docIDs) {
                    Document doc = db.getDocument(docID);
                    if (doc != null) {
                        try {

                            db.purge(doc);
                        } catch (CouchbaseLiteException e) {
                            assertEquals(404, e.getCode());
                        }
                    }
                }
                latch1.countDown();
            }
        }).start();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String docID : docIDs) {
                    Document doc = db.getDocument(docID);
                    if (doc != null) {
                        try {
                            db.purge(doc);
                        } catch (CouchbaseLiteException e) {
                            assertEquals(404, e.getCode());
                        }
                    }
                }
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));

        assertEquals(0, db.getCount());
    }

    @Test
    public void testConcurrentCreateNCloseDB() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createDocs(kNDocs, tag1);
                } catch (CouchbaseLiteException e) {
                    if (e.getDomain() != LiteCoreDomain || e.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", e, kNDocs, tag1);
                        fail();
                    }
                } catch (CouchbaseLiteRuntimeException re) {
                    if (re.getDomain() != LiteCoreDomain || re.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", re, kNDocs, tag1);
                        fail();
                    }
                }
                latch1.countDown();
            }
        }).start();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    db.close();
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in Database.close()", e);
                    fail();
                }
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));
    }

    @Test
    public void testConcurrentCreateNDeleteDB() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createDocs(kNDocs, tag1);
                } catch (CouchbaseLiteException e) {
                    if (e.getDomain() != LiteCoreDomain || e.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", e, kNDocs, tag1);
                        fail();
                    }
                } catch (CouchbaseLiteRuntimeException re) {
                    if (re.getDomain() != LiteCoreDomain || re.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", re, kNDocs, tag1);
                        fail();
                    }
                }
                latch1.countDown();
            }
        }).start();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    db.delete();
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in Database.delete()", e);
                    fail();
                }
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));
    }

    @Test
    public void testConcurrentCreateNCompactDB() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createDocs(kNDocs, tag1);
                } catch (CouchbaseLiteException e) {
                    if (e.getDomain() != LiteCoreDomain || e.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", e, kNDocs, tag1);
                        fail();
                    }
                } catch (CouchbaseLiteRuntimeException re) {
                    if (re.getDomain() != LiteCoreDomain || re.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", re, kNDocs, tag1);
                        fail();
                    }
                }
                latch1.countDown();
            }
        }).start();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    db.compact();
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in Database.compact()", e);
                    fail();
                }
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));
    }

    @Test
    public void testConcurrentCreateNCreateIndexDB() throws Exception {
        if (!config.concurrentTestsEnabled())
            return;

        loadJSONResource("sentences.json");

        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createDocs(kNDocs, tag1);
                } catch (CouchbaseLiteException e) {
                    if (e.getDomain() != LiteCoreDomain || e.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", e, kNDocs, tag1);
                        fail();
                    }
                } catch (CouchbaseLiteRuntimeException re) {
                    if (re.getDomain() != LiteCoreDomain || re.getCode() != kC4ErrorNotOpen) {
                        Log.e(TAG, "Error in createDocs() kNDocs -> %d, tag1 -> %s", re, kNDocs, tag1);
                        fail();
                    }
                }
                latch1.countDown();
            }
        }).start();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Index index = Index.ftsIndex().on(FTSIndexItem.expression(Expression.property("sentence")));
                    db.createIndex("sentence", index);
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in Database.createIndex()", e);
                    fail();
                }
                latch2.countDown();
            }
        }).start();

        assertTrue(latch1.await(60, TimeUnit.SECONDS));
        assertTrue(latch2.await(60, TimeUnit.SECONDS));
    }

    @Test
    public void testBlockDatabaseChange() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        db.addChangeListener(new DatabaseChangeListener() {
            @Override
            public void changed(DatabaseChange change) {
                Log.e(TAG, "changed() change -> %s", change);
                latch2.countDown();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    db.save(new MutableDocument("doc1"));
                } catch (CouchbaseLiteException e) {
                    fail();
                }
                latch1.countDown();
            }
        }).start();

        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        assertTrue(latch2.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testBlockDocumentChange() throws InterruptedException, CouchbaseLiteException {
        if (!config.concurrentTestsEnabled())
            return;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        db.addChangeListener("doc1", new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                latch2.countDown();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    db.save(new MutableDocument("doc1"));
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error in save()", e);
                    fail();
                }
                latch1.countDown();
            }
        }).start();

        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        assertTrue(latch2.await(10, TimeUnit.SECONDS));
    }
}
