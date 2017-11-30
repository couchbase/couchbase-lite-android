package com.couchbase.lite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrUnknownHost;
import static org.junit.Assert.assertEquals;

public class ReplicatorTest extends BaseReplicatorTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testBadURL() throws InterruptedException {
        ReplicatorConfiguration config = makeConfig(false, true, false, "blxp://localhost/db");
        run(config, 15, "LiteCore");
    }

    @Test
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
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(true, false, true);
        run(config, 0, null);

        assertEquals(2, otherDB.getCount());
        Document doc2a = otherDB.getDocument("doc2");
        assertEquals("Cat", doc2a.getString("name"));
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
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Cat");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());

        ReplicatorConfiguration config = makeConfig(false, true, true);
        run(config, 0, null);

        assertEquals(2, db.getCount());
        Document doc2a = db.getDocument("doc2");
        assertEquals("Cat", doc2a.getString("name"));
    }

    @Test
    public void testPullConflict() throws Exception {
        MutableDocument doc1 = new MutableDocument("doc");
        doc1.setValue("species", "Tiger");
        save(doc1);
        assertEquals(1, db.getCount());
        doc1.setValue("name", "Hobbes");
        save(doc1);
        assertEquals(1, db.getCount());

        MutableDocument doc2 = new MutableDocument("doc");
        doc2.setValue("species", "Tiger");
        otherDB.save(doc2);
        assertEquals(1, otherDB.getCount());
        doc2.setValue("pattern", "striped");
        otherDB.save(doc2);
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
    public void testPullConflictNoBaseRevision() {
        // TODO
    }

    @Test
    public void testStopContinuousReplicator() {
        // TODO
    }

    // https://github.com/couchbase/couchbase-lite-core/issues/149
    // @Test
    public void testMissingHost() throws InterruptedException {
        // should timeout after 10sec
        // builder.connectTimeout(10, TimeUnit.SECONDS)
        timeout = 20;

        // NOTE: Following URL causes UnknownHostException which is transient error,
        //       and replicator status becomes OFFLINE

        String uri = String.format(Locale.ENGLISH, "blip://foo.couchbase.com/db");
        ReplicatorConfiguration config = makeConfig(false, true, true, uri);
        run(config, kC4NetErrUnknownHost, "Network");
    }
}
