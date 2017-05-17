package com.couchbase.lite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class NotificationTest extends BaseTest {

    @Before
    public void setUp() {
        super.setUp();
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void testDatabaseNotification() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        db.addChangeListener(new DatabaseChangeListener() {
            @Override
            public void changed(DatabaseChange change) {
                Log.e(TAG, "DatabaseChangeListener.changed() change -> " + change);
                assertNotNull(change);
                assertNotNull(change.getDocumentIDs());
                assertEquals(10, change.getDocumentIDs().size());
                assertEquals(db, change.getDatabase());
                latch.countDown();
            }
        });
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    Document doc = createDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                    doc.set("type", "demo");
                    save(doc);
                }
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testDocumentNotification() throws InterruptedException {
        Document docA = createDocument("A");
        Document docB = createDocument("B");


        final CountDownLatch latch1 = new CountDownLatch(1);
        DocumentChangeListener listener1 = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch1.getCount());
                latch1.countDown();
            }
        };
        db.addChangeListener("A", listener1);
        docB.set("thewronganswer", 18);
        save(docB);
        docA.set("theanswer", 18);
        save(docA);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        db.removeChangeListener("A", listener1);

        final CountDownLatch latch2 = new CountDownLatch(1);
        DocumentChangeListener listener2 = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch2.getCount());
                latch2.countDown();
            }
        };
        db.addChangeListener("A", listener2);
        docA.set("thewronganswer", 18);
        save(docA);
        assertTrue(latch2.await(5, TimeUnit.SECONDS));
        db.removeChangeListener("A", listener1);
    }

    @Test
    public void testExternalChanges() throws InterruptedException {
        final Database db2 = db.copy();
        assertNotNull(db2);
        try {

            final CountDownLatch latchDB = new CountDownLatch(1);
            db2.addChangeListener(new DatabaseChangeListener() {
                @Override
                public void changed(DatabaseChange change) {
                    assertNotNull(change);
                    assertEquals(10, change.getDocumentIDs().size());
                    assertEquals(1, latchDB.getCount());
                    latchDB.countDown();
                }
            });

            final CountDownLatch latchDoc = new CountDownLatch(1);
            db2.addChangeListener("doc-6", new DocumentChangeListener() {
                @Override
                public void changed(DocumentChange change) {
                    assertNotNull(change);
                    assertEquals("doc-6", change.getDocumentID());
                    Document doc = db2.getDocument(change.getDocumentID());
                    assertEquals("demo", doc.getString("type"));
                    assertEquals(1, latchDoc.getCount());
                    latchDoc.countDown();
                }
            });

            db.inBatch(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        Document doc = createDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                        doc.set("type", "demo");
                        save(doc);
                    }
                }
            });

            assertTrue(latchDB.await(5, TimeUnit.SECONDS));
            assertTrue(latchDoc.await(5, TimeUnit.SECONDS));
        } finally {
            db2.close();
        }
    }

}
