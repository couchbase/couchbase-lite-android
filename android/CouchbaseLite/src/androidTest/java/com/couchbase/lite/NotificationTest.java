//
// NotificationTest.java
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

import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

public class NotificationTest extends BaseTest {
    @Test
    public void testDatabaseChange()
            throws InterruptedException, CouchbaseLiteException {
        final CountDownLatch latch = new CountDownLatch(1);
        db.addChangeListener(executor, new DatabaseChangeListener() {
            @Override
            public void changed(DatabaseChange change) {
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
                    MutableDocument doc = createMutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                    doc.setValue("type", "demo");
                    try {
                        save(doc);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testDocumentChange()
            throws InterruptedException, CouchbaseLiteException {
        MutableDocument mDocA = createMutableDocument("A");
        MutableDocument mDocB = createMutableDocument("B");

        // save doc
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
        ListenerToken token = db.addDocumentChangeListener("A", listener1);
        mDocB.setValue("thewronganswer", 18);
        Document docB = save(mDocB);
        mDocA.setValue("theanswer", 18);
        Document docA = save(mDocA);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        db.removeChangeListener(token);

        // update doc
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
        token = db.addDocumentChangeListener("A", listener2);
        mDocA = docA.toMutable();
        mDocA.setValue("thewronganswer", 18);
        docA = save(mDocA);
        assertTrue(latch2.await(5, TimeUnit.SECONDS));
        db.removeChangeListener(token);

        // delete doc
        final CountDownLatch latch3 = new CountDownLatch(1);
        DocumentChangeListener listener3 = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch3.getCount());
                latch3.countDown();
            }
        };
        token = db.addDocumentChangeListener("A", listener3);
        db.delete(docA);
        assertTrue(latch3.await(5, TimeUnit.SECONDS));
        db.removeChangeListener(token);

    }

    @Test
    public void testExternalChanges()
            throws InterruptedException, CouchbaseLiteException {
        final Database db2 = db.copy();
        assertNotNull(db2);
        try {
            final CountDownLatch latchDB = new CountDownLatch(1);
            db2.addChangeListener(executor, new DatabaseChangeListener() {
                @Override
                public void changed(DatabaseChange change) {
                    assertNotNull(change);
                    assertEquals(10, change.getDocumentIDs().size());
                    assertEquals(1, latchDB.getCount());
                    latchDB.countDown();
                }
            });

            final CountDownLatch latchDoc = new CountDownLatch(1);
            db2.addDocumentChangeListener("doc-6", executor, new DocumentChangeListener() {
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
                        MutableDocument doc = createMutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                        doc.setValue("type", "demo");
                        try {
                            save(doc);
                        } catch (CouchbaseLiteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            assertTrue(latchDB.await(5, TimeUnit.SECONDS));
            assertTrue(latchDoc.await(5, TimeUnit.SECONDS));
        } finally {
            db2.close();
        }
    }

    @Test
    public void testAddSameChangeListeners()
            throws InterruptedException, CouchbaseLiteException {
        MutableDocument doc1 = createMutableDocument("doc1");
        doc1.setValue("name", "Scott");
        Document savedDoc1 = save(doc1);

        final CountDownLatch latch = new CountDownLatch(5);
        // Add change listeners:
        DocumentChangeListener listener = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                if (change.getDocumentID().equals("doc1"))
                    latch.countDown();
            }
        };
        ListenerToken token1 = db.addDocumentChangeListener("doc1", listener);
        ListenerToken token2 = db.addDocumentChangeListener("doc1", listener);
        ListenerToken token3 = db.addDocumentChangeListener("doc1", listener);
        ListenerToken token4 = db.addDocumentChangeListener("doc1", listener);
        ListenerToken token5 = db.addDocumentChangeListener("doc1", listener);

        try {
            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scott Tiger");
            save(doc1);

            // Let's wait for 0.5 seconds:
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        } finally {
            db.removeChangeListener(token1);
            db.removeChangeListener(token2);
            db.removeChangeListener(token3);
            db.removeChangeListener(token4);
            db.removeChangeListener(token5);
        }
    }

    @Test
    public void testRemoveDocumentChangeListener()
            throws InterruptedException, CouchbaseLiteException {
        MutableDocument doc1 = createMutableDocument("doc1");
        doc1.setValue("name", "Scott");
        Document savedDoc1 = save(doc1);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);
        // Add change listeners:
        DocumentChangeListener listener = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
                if (change.getDocumentID().equals("doc1")) {
                    latch1.countDown();
                    latch2.countDown();
                }
            }
        };
        ListenerToken token = db.addDocumentChangeListener("doc1", listener);

        // Update doc1:
        doc1 = savedDoc1.toMutable();
        doc1.setValue("name", "Scott Tiger");
        savedDoc1 = save(doc1);

        // Let's wait for 0.5 seconds:
        assertTrue(latch1.await(500, TimeUnit.MILLISECONDS));

        // Remove change listener:
        db.removeChangeListener(token);

        // Update doc1:
        doc1 = savedDoc1.toMutable();
        doc1.setValue("name", "Scotty");
        savedDoc1 = save(doc1);

        // Let's wait for 0.5 seconds:
        assertFalse(latch2.await(500, TimeUnit.MILLISECONDS));
        assertEquals(1, latch2.getCount());

        // Remove again:
        db.removeChangeListener(token);
    }
}
