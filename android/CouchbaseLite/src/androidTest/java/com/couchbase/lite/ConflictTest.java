package com.couchbase.lite;

import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.fleece.FLEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
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
                resolved.setObject(key, conflict.getBase().getObject(key));
            }

            for (String key : conflict.getTheirs()) {
                resolved.setObject(key, conflict.getTheirs().getObject(key));
                changed.add(key);
            }

            for (String key : conflict.getMine()) {
                if (!changed.contains(key))
                    resolved.setObject(key, conflict.getMine().getObject(key));
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
        doc.setObject("type", "profile");
        doc.setObject("name", "Scott");
        save(doc);

        // Force a conflict
        Map<String, Object> properties = doc.toMap();
        properties.put("name", "Scotty");
        save(properties, doc.getId());

        // Change document in memory, so save will trigger a conflict
        doc.setObject("name", "Scott Pilgrim");

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
        doc2.setObject("type", "profile");
        doc2.setObject("name", "Scott");
        save(doc2);

        // Force a conflict again
        Map<String, Object> properties = doc2.toMap();
        properties.put("type", "bio");
        properties.put("gender", "male");
        save(properties, doc2.getId());

        // Save and make sure that the correct conflict resolver won
        doc2.setObject("type", "biography");
        doc2.setObject("age", 31);
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

    // REF: https://github.com/couchbase/couchbase-lite-android/issues/1293
    @Test
    public void testConflictWithoutCommonAncestor() throws CouchbaseLiteException {
        closeDB();

        // open db with special conflict resolver
        openDB(new ConflictResolver() {
            @Override
            public ReadOnlyDocument resolve(Conflict conflict) {
                assertNotNull(conflict);
                assertNull(conflict.getBase()); // make sure base is null
                return conflict.getBase();      // null -> cause 409
            }
        });

        String docID = "doc1";
        Map<String, Object> props = new HashMap<>();
        props.put("hello", "world");

        //STEP 1: Created a new document with id = "doc1"
        Document doc = new Document(docID, props);
        db.save(doc);

        //STEP 2: Added a revision to the document
        doc = db.getDocument(docID);
        doc.setObject("university", 1);
        db.save(doc);

        // STEP3: Create Conflict as follows
        doc = new Document(docID, props);
        doc.setObject("university", 2);
        try {
            db.save(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            // Currently returned error code is LiteCore error code.
            // This could change to HTTP error code.
            assertEquals(LiteCoreDomain, e.getDomain());
            assertEquals(kC4ErrorConflict, e.getCode()); // int kC4ErrorConflict = 14;
        }
    }
}
