package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ReplicatorTest extends BaseReplicatorTest {

    // TODO: It seems the replication finishes before constructor returns.
    //       This causes RefCounted throws exception.
    //       Also this might be related with disk I/O error
    // https://github.com/couchbase/couchbase-lite-core/issues/383
    // @Test
    public void testEmptyPush() throws InterruptedException {
        ReplicatorConfiguration config = makeConfig(true, false, false);
        run(config, 0, null);
    }

    @Test
    public void testPushDoc() throws Exception {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(true, false, false);
        run(config, 0, null);

        assertEquals(2, otherDB.getCount());
        Document doc2a = otherDB.getDocument("doc2");
        assertEquals("Cat", doc2a.getString("name"));
    }

    @Test
    public void testPushDocContinuous() throws Exception {
        Log.i(TAG, "testPushDocContinuous() - BEGIN");
        String strAnotherDB = "anotherDB";
        Database anotherDB = open(strAnotherDB);
        try {
            MutableDocument doc1 = new MutableDocument("doc1");
            doc1.setValue("name", "Tiger");
            anotherDB.save(doc1);
            assertEquals(1, anotherDB.getCount());

            MutableDocument doc2 = new MutableDocument("doc2");
            doc2.setValue("name", "Cat");
            otherDB.save(doc2);
            assertEquals(1, otherDB.getCount());

            ReplicatorConfiguration config = new ReplicatorConfiguration(anotherDB, new DatabaseEndpoint(otherDB));
            config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH);
            config.setContinuous(true);
            Replicator repl = run(config, 0, null);

            assertEquals(2, otherDB.getCount());
            Document doc2a = otherDB.getDocument("doc2");
            assertEquals("Cat", doc2a.getString("name"));

            stopContinuousReplicator(repl);
        } finally {
            anotherDB.close();
            this.deleteDatabase(strAnotherDB);
        }
        Log.i(TAG, "testPushDocContinuous() - END");
    }

    @Test
    public void testPullDoc() throws Exception {
        // For https://github.com/couchbase/couchbase-lite-core/issues/156
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(false, true, false);
        run(config, 0, null);

        assertEquals(2, db.getCount());
        Document doc2a = db.getDocument("doc2");
        assertEquals("Cat", doc2a.getString("name"));
    }

    // https://github.com/couchbase/couchbase-lite-core/issues/156
    @Test
    public void testPullDocContinuous() throws Exception {
        Log.i(TAG, "testPullDocContinuous() - BEGIN");
        String strAnotherDB = "anotherDB";
        Database anotherDB = open(strAnotherDB);
        try {
            MutableDocument doc1 = new MutableDocument("doc1");
            doc1.setValue("name", "Tiger");
            anotherDB.save(doc1);
            assertEquals(1, anotherDB.getCount());

            MutableDocument doc2 = new MutableDocument("doc2");
            doc2.setValue("name", "Cat");
            otherDB.save(doc2);
            assertEquals(1, otherDB.getCount());

            ReplicatorConfiguration config = new ReplicatorConfiguration(anotherDB, new DatabaseEndpoint(otherDB));
            config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
            config.setContinuous(true);
            run(config, 0, null);

            assertEquals(2, anotherDB.getCount());
            Document doc2a = anotherDB.getDocument("doc2");
            assertEquals("Cat", doc2a.getString("name"));

            stopContinuousReplicator(repl);
        } finally {
            anotherDB.close();
            this.deleteDatabase(strAnotherDB);
        }
        Log.i(TAG, "testPullDocContinuous() - END");
    }

    @Test
    public void testPullConflict() throws Exception {
        MutableDocument mDoc1 = new MutableDocument("doc");
        mDoc1.setValue("species", "Tiger");
        Document doc1 = save(mDoc1);
        assertEquals(1, db.getCount());
        mDoc1 = doc1.toMutable();
        mDoc1.setValue("name", "Hobbes");
        doc1 = save(mDoc1);
        assertEquals(1, db.getCount());

        MutableDocument mDoc2 = new MutableDocument("doc");
        mDoc2.setValue("species", "Tiger");
        Document doc2 = otherDB.save(mDoc2);
        assertEquals(1, otherDB.getCount());
        mDoc2 = doc2.toMutable();
        mDoc2.setValue("pattern", "striped");
        doc2 = otherDB.save(mDoc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(false, true, false);
        run(config, 0, null);
        assertEquals(1, db.getCount());

        Document doc1a = db.getDocument("doc");
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("species", "Tiger");
        expectedMap.put("name", "Hobbes");
        expectedMap.put("pattern", "striped");
        assertEquals(expectedMap, doc1a.toMap());
    }

    @Test
    public void testPullConflictNoBaseRevision() throws CouchbaseLiteException, InterruptedException {
        // Create the conflicting docs separately in each database. They have the same base revID
        // because the contents are identical, but because the db never pushed revision 1, it doesn't
        // think it needs to preserve its body; so when it pulls a conflict, there won't be a base
        // revision for the resolver.
        MutableDocument doc1 = new MutableDocument("doc");
        doc1.setValue("species", "Tiger");
        doc1 = db.save(doc1).toMutable();
        doc1.setValue("name", "Hobbes");
        db.save(doc1);

        MutableDocument doc2 = new MutableDocument("doc");
        doc2.setValue("species", "Tiger");
        doc2 = otherDB.save(doc2).toMutable();
        doc2.setValue("pattern", "striped");
        otherDB.save(doc2);

        ReplicatorConfiguration config = makeConfig(false, true, false, otherDB);
        config.setConflictResolver(new ConflictTest.MergeThenTheirsWins());
        run(config, 0, null);

        assertEquals(1, db.getCount());
        Document savedDoc = db.getDocument("doc");
        Map<String, Object> expected = new HashMap<>();
        expected.put("species", "Tiger");
        expected.put("name", "Hobbes");
        expected.put("pattern", "striped");
        assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testStopContinuousReplicator() throws InterruptedException {
        ReplicatorConfiguration config = makeConfig(true, true, true, otherDB);
        Replicator r = new Replicator(config);
        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = r.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                if (change.getStatus().getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    Log.i(Log.SYNC, "***** STOPPED Replicator ******");
                    latch.countDown();
                } else if (change.getStatus().getActivityLevel() == Replicator.ActivityLevel.CONNECTING
                        || change.getStatus().getActivityLevel() == Replicator.ActivityLevel.BUSY
                        || change.getStatus().getActivityLevel() == Replicator.ActivityLevel.IDLE) {
                    Log.i(Log.SYNC, "***** Stop Replicator ******");
                    change.getReplicator().stop();
                }
            }
        });

        Log.i(Log.SYNC, "***** Start Replicator ******");
        r.start();
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        r.removeChangeListener(token);
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }
    }

    @Test
    public void testDocIDFilter() throws CouchbaseLiteException, InterruptedException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setString("species", "Tiger");
        Document saved1 = db.save(doc1);
        doc1 = saved1.toMutable();
        doc1.setString("name", "Hobbes");
        saved1 = db.save(doc1);

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setString("species", "Tiger");
        Document saved2 = db.save(doc2);
        doc2 = saved2.toMutable();
        doc2.setString("pattern", "striped");
        saved2 = db.save(doc2);

        MutableDocument doc3 = new MutableDocument("doc3");
        doc3.setString("species", "Tiger");
        Document saved3 = otherDB.save(doc3);
        doc3 = saved3.toMutable();
        doc3.setString("name", "Hobbes");
        saved3 = otherDB.save(doc3);

        MutableDocument doc4 = new MutableDocument("doc4");
        doc4.setString("species", "Tiger");
        Document saved4 = otherDB.save(doc4);
        doc4 = saved4.toMutable();
        doc4.setString("pattern", "striped");
        saved4 = otherDB.save(doc4);

        ReplicatorConfiguration config = makeConfig(true, true, false);
        config.setDocumentIDs(Arrays.asList("doc1", "doc3"));
        run(config, 0, null);
        assertEquals(3, db.getCount());
        assertNotNull(db.getDocument("doc3"));
        assertEquals(3, otherDB.getCount());
        assertNotNull(otherDB.getDocument("doc1"));
    }

    @Test
    public void testReplicatorStopWhenClosed() throws CouchbaseLiteException {
        try {
            ReplicatorConfiguration config = makeConfig(true, true, true);
            Replicator repl = new Replicator(config);
            repl.start();
            while (repl.getStatus().getActivityLevel() != Replicator.ActivityLevel.IDLE) {
                Log.w(TAG, String.format(Locale.ENGLISH,
                        "Replicator status is still %s, waiting for idle...",
                        repl.getStatus().getActivityLevel()));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }

            closeDB();

            int attemptCount = 0;
            while (attemptCount++ < 20 && repl.getStatus().getActivityLevel() != Replicator.ActivityLevel.STOPPED) {
                Log.w(TAG, String.format(Locale.ENGLISH,
                        "Replicator status is still %s, waiting for stopped (remaining attempts %d)...",
                        repl.getStatus().getActivityLevel(), 10 - attemptCount));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
            assertTrue(attemptCount < 20);
        } finally {
            // NOTE: DB is closed middle of unit test. So tearDown might not be able to delete it.
            deleteDatabase(kDatabaseName);
        }
    }

    /**
     * Database to Database Push replication document has attachment
     * https://github.com/couchbase/couchbase-lite-core/issues/355
     */
    @Test
    public void testAttachmentPush() throws CouchbaseLiteException, InterruptedException, IOException {
        Log.i(TAG, "testAttachmentPush() - BEGIN");
        String strAnotherDB = "anotherDB";
        Database anotherDB = open(strAnotherDB);
        try {
            InputStream is = getAsset("image.jpg");
            try {
                Blob blob = new Blob("image/jpg", is);
                MutableDocument doc1 = new MutableDocument("doc1");
                doc1.setValue("name", "Tiger");
                doc1.setBlob("image.jpg", blob);
                //save(doc1);
                anotherDB.save(doc1);
            } finally {
                is.close();
            }
            assertEquals(1, anotherDB.getCount());

            //ReplicatorConfiguration config = makeConfig(true, false, false);
            ReplicatorConfiguration config = new ReplicatorConfiguration(anotherDB, new DatabaseEndpoint(otherDB));
            config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH);
            run(config, 0, null);

            assertEquals(1, otherDB.getCount());
            Document doc1a = otherDB.getDocument("doc1");
            Blob blob1a = doc1a.getBlob("image.jpg");
            assertNotNull(blob1a);
        } finally {
            anotherDB.close();
            this.deleteDatabase(strAnotherDB);
        }
        Log.i(TAG, "testAttachmentPush() - END");
    }

    /**
     * Database to Database Pull replication document has attachment
     * https://github.com/couchbase/couchbase-lite-core/issues/355
     */
    @Test
    public void testAttachmentPull() throws CouchbaseLiteException, InterruptedException, IOException {
        Log.i(TAG, "testAttachmentPull() - BEGIN");
        String strAnotherDB = "anotherDB";
        Database anotherDB = open(strAnotherDB);
        try {

            InputStream is = getAsset("image.jpg");
            //InputStream is = getAsset("attachment.png");
            try {
                Blob blob = new Blob("image/jpg", is);
                //Blob blob = new Blob("image/jpg", is);
                MutableDocument doc1 = new MutableDocument("doc1");
                doc1.setValue("name", "Tiger");
                doc1.setBlob("image.jpg", blob);
                otherDB.save(doc1);
            } finally {
                is.close();
            }
            assertEquals(1, otherDB.getCount());

            ReplicatorConfiguration config = new ReplicatorConfiguration(anotherDB, new DatabaseEndpoint(otherDB));
            config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
            run(config, 0, null);

            assertEquals(1, anotherDB.getCount());
            Document doc1a = anotherDB.getDocument("doc1");
            Blob blob1a = doc1a.getBlob("image.jpg");
            assertNotNull(blob1a);

        } finally {
            anotherDB.close();
            this.deleteDatabase(strAnotherDB);
        }
        Log.i(TAG, "testAttachmentPull() - END");
    }

    @Test
    public void testStopReplicatorAfterOffline() throws URISyntaxException, InterruptedException {
        timeout = 200;
        URLEndpoint target = new URLEndpoint(new URI("ws://foo.couchbase.com/db"));
        ReplicatorConfiguration config = makeConfig(false, true, true, db, target);
        Replicator repl = new Replicator(config);
        final CountDownLatch offline = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                    offline.countDown();
                    change.getReplicator().stop();
                }
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    stopped.countDown();
                }
            }
        });
        repl.start();
        assertTrue(offline.await(10, TimeUnit.SECONDS));
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        repl.removeChangeListener(token);
    }
}
