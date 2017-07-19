package com.couchbase.lite;

import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.fleece.FLEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorConflict;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ConflictTest extends BaseTest {

    static class TheirsWins implements ConflictResolver {
        @Override
        public ReadOnlyDocument resolve(Conflict conflict) {
            return conflict.getTheirs();
        }
    }

    static class MergeThenTheirsWins implements ConflictResolver {
        @Override
        public ReadOnlyDocument resolve(Conflict conflict) {
            Document resolved = new Document();
            Set<String> changed = new HashSet<>();

            for (String key : conflict.getBase()) {
                resolved.set(key, conflict.getBase().getObject(key));
            }

            for (String key : conflict.getTheirs()) {
                resolved.set(key, conflict.getTheirs().getObject(key));
                changed.add(key);
            }

            for (String key : conflict.getMine()) {
                if (!changed.contains(key))
                    resolved.set(key, conflict.getMine().getObject(key));
            }

            return resolved;
        }
    }

    static class GiveUp implements ConflictResolver {
        @Override
        public ReadOnlyDocument resolve(Conflict conflict) {
            return null;
        }
    }

    static class DoNotResolve implements ConflictResolver {
        @Override
        public ReadOnlyDocument resolve(Conflict conflict) {
            fail("Resolver should not have been called!");
            return null;
        }
    }

    private Document setupConflict() throws CouchbaseLiteException {
        // Setup a default database conflict resolver
        Document doc = createDocument("doc1");
        doc.set("type", "profile");
        doc.set("name", "Scott");
        save(doc);

        // Force a conflict
        Map<String, Object> properties = doc.toMap();
        properties.put("name", "Scotty");
        save(properties, doc.getId());

        // Change document in memory, so save will trigger a conflict
        doc.set("name", "Scott Pilgrim");

        return doc;
    }

    private void save(final Map<String, Object> props, final String docID)
            throws CouchbaseLiteException {
        // Save to database:
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                try {
                    C4Document trickey = db.getC4Database().get(docID, true);
                    FLEncoder enc = db.getC4Database().createFleeceEncoder();
                    enc.writeValue(props);
                    byte[] bytes = enc.finish();
                    C4Document newDoc = db.getC4Database().put(
                            bytes,
                            docID,
                            0,
                            false,
                            false,
                            (String[]) Arrays.asList(trickey.getRevID()).toArray(),
                            true,
                            0);
                    assertNotNull(newDoc);
                } catch (Exception e) {
                    Log.e(TAG, "Error in Runnable.run()", e);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    protected void openDB() throws CouchbaseLiteException {
        openDB(new DoNotResolve());
    }

    protected void openDB(ConflictResolver resolver) throws CouchbaseLiteException {
        assertNull(db);

        DatabaseConfiguration options = new DatabaseConfiguration(this.context);
        options.setDirectory(dir);
        options.setConflictResolver(resolver);
        db = new Database(kDatabaseName, options);
        assertNotNull(db);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testConflict() throws CouchbaseLiteException {
        closeDB();
        openDB(new TheirsWins());

        Document doc1 = setupConflict();
        save(doc1);
        assertEquals("Scotty", doc1.getObject("name"));

        // Get a new document with its own conflict resolver

        closeDB();
        openDB(new MergeThenTheirsWins());

        Document doc2 = createDocument("doc2");
        doc2.set("type", "profile");
        doc2.set("name", "Scott");
        save(doc2);

        // Force a conflict again
        Map<String, Object> properties = doc2.toMap();
        properties.put("type", "bio");
        properties.put("gender", "male");
        save(properties, doc2.getId());

        // Save and make sure that the correct conflict resolver won
        doc2.set("type", "biography");
        doc2.set("age", 31);
        save(doc2);

        assertEquals(31L, doc2.getObject("age"));
        assertEquals("bio", doc2.getObject("type"));
        assertEquals("male", doc2.getObject("gender"));
        assertEquals("Scott", doc2.getObject("name"));
    }

    @Test
    public void testConflictResolverGivesUp() throws CouchbaseLiteException {
        closeDB();
        openDB(new GiveUp());

        Document doc = setupConflict();
        try {
            save(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(LiteCoreDomain, e.getDomain());
            assertEquals(kC4ErrorConflict, e.getCode());
        }
    }

    @Test
    public void testDeletionConflict() throws CouchbaseLiteException {
        closeDB();
        openDB(new DoNotResolve());

        Document doc = setupConflict();
        db.delete(doc);
        assertFalse(doc.isDeleted());
        assertEquals("Scotty", doc.getString("name"));
    }

    @Test
    public void testConflictMineIsDeeper() throws CouchbaseLiteException {
        closeDB();
        openDB(null);

        Document doc = setupConflict();
        save(doc);
        assertEquals("Scott Pilgrim", doc.getString("name"));
    }

    @Test
    public void testConflictTheirsIsDeeper() throws CouchbaseLiteException {
        closeDB();
        openDB(null);

        Document doc = setupConflict();

        // Add another revision to the conflict, so it'll have a higher generation:
        Map<String, Object> properties = doc.toMap();
        properties.put("name", "Scott of the Sahara");
        save(properties, doc.getId());

        save(doc);
        assertEquals("Scott of the Sahara", doc.getString("name"));
    }
}
