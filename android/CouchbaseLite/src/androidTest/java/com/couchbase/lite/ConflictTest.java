//
// ConflictTest.java
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ConflictTest extends BaseTest {

    static class TheirsWins implements ConflictResolver {
        @Override
        public Document resolve(Conflict conflict) {
            return conflict.getTheirs();
        }
    }

    static class MergeThenTheirsWins implements ConflictResolver {
        @Override
        public Document resolve(Conflict conflict) {
            MutableDocument resolved = new MutableDocument();
            Set<String> changed = new HashSet<>();

            if (conflict.getBase() != null) {
                for (String key : conflict.getBase()) {
                    resolved.setValue(key, conflict.getBase().getValue(key));
                }
            }

            if (conflict.getTheirs() != null) {
                for (String key : conflict.getTheirs()) {
                    resolved.setValue(key, conflict.getTheirs().getValue(key));
                    changed.add(key);
                }
            }

            if (conflict.getMine() != null) {
                for (String key : conflict.getMine()) {
                    if (!changed.contains(key))
                        resolved.setValue(key, conflict.getMine().getValue(key));
                }
            }

            return resolved;
        }
    }

    static class GiveUp implements ConflictResolver {
        @Override
        public Document resolve(Conflict conflict) {
            return null;
        }
    }

    static class DoNotResolve implements ConflictResolver {
        @Override
        public Document resolve(Conflict conflict) {
            fail("Resolver should not have been called!");
            return null;
        }
    }

    private MutableDocument setupConflict() throws CouchbaseLiteException {
        // Setup a default database conflict resolver
        MutableDocument mDoc = createMutableDocument("doc1");
        mDoc.setValue("type", "profile");
        mDoc.setValue("name", "Scott");
        Document doc = save(mDoc);

        // Force a conflict
        Map<String, Object> properties = doc.toMap();
        properties.put("name", "Scotty");
        save(properties, doc.getId());

        // Change document in memory, so save will trigger a conflict
        mDoc = doc.toMutable();
        mDoc.setValue("name", "Scott Pilgrim");

        return mDoc;
    }

    private void save(final Map<String, Object> props, final String docID)
            throws CouchbaseLiteException {
        // Save to database:
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes;
                    C4Document trickey = db.getC4Database().get(docID, true);
                    FLEncoder enc = db.getC4Database().createFleeceEncoder();
                    try {
                        enc.writeValue(props);
                        bytes = enc.finish();
                    } finally {
                        enc.free();
                    }
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
                    newDoc.free();
                    trickey.free();
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

        DatabaseConfiguration config = new DatabaseConfiguration(this.context)
                .setDirectory(getDir().getAbsolutePath())
                .setConflictResolver(resolver);
        db = new Database(kDatabaseName, config);
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

        MutableDocument mDoc1 = setupConflict();
        Document doc1 = save(mDoc1);
        assertNotNull(doc1);
        assertEquals("Scotty", doc1.getValue("name"));

        // Get a new document with its own conflict resolver
        closeDB();
        openDB(new MergeThenTheirsWins());

        MutableDocument doc2 = createMutableDocument("doc2");
        doc2.setValue("type", "profile");
        doc2.setValue("name", "Scott");
        Document savedDoc2 = save(doc2);
        assertNotNull(savedDoc2);

        // Force a conflict again
        Map<String, Object> properties = savedDoc2.toMap();
        properties.put("type", "bio");
        properties.put("gender", "male");
        save(properties, doc2.getId());

        // Save and make sure that the correct conflict resolver won
        doc2 = savedDoc2.toMutable();
        doc2.setValue("type", "biography");
        doc2.setValue("age", 31);

        savedDoc2 = save(doc2);
        assertNotNull(savedDoc2);

        assertEquals(31L, savedDoc2.getLong("age"));
        assertEquals("bio", savedDoc2.getString("type"));
        assertEquals("male", savedDoc2.getString("gender"));
        assertEquals("Scott", savedDoc2.getString("name"));
    }

    @Test
    public void testConflictResolverGivesUp() throws CouchbaseLiteException {
        closeDB();
        openDB(new GiveUp());

        MutableDocument doc = setupConflict();
        try {
            save(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(CBLError.Domain.CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorConflict, e.getCode());
        }
    }

    @Test
    public void testConflictDeletion() throws CouchbaseLiteException {
        closeDB();
        openDB(new DefaultConflictResolver()); // set null to conflict resolver, should use default one

        MutableDocument mDoc = setupConflict();
        db.delete(mDoc);
        assertNull(db.getDocument(mDoc.getId()));
    }

    @Test
    public void testConflictMineIsDeeper() throws CouchbaseLiteException {
        closeDB();
        openDB(new DefaultConflictResolver());

        MutableDocument doc = setupConflict();
        save(doc);
        assertEquals("Scott Pilgrim", doc.getString("name"));
    }

    @Test
    public void testConflictTheirsIsDeeper() throws CouchbaseLiteException {
        closeDB();
        openDB(new DefaultConflictResolver());

        MutableDocument mDoc = setupConflict();

        // Add another revision to the conflict, so it'll have a higher generation:
        Map<String, Object> properties = mDoc.toMap();
        properties.put("name", "Scott of the Sahara");
        save(properties, mDoc.getId());

        Document doc = save(mDoc);
        assertEquals("Scott of the Sahara", doc.getString("name"));
    }

    @Test
    public void testNoBase() throws CouchbaseLiteException {
        closeDB();

        openDB(new ConflictResolver() {
            @Override
            public Document resolve(Conflict conflict) {
                assertEquals("Tiger", conflict.getMine().getString("name"));
                assertEquals("Daniel", conflict.getTheirs().getString("name"));
                assertNull(conflict.getBase());
                return conflict.getMine();
            }
        });

        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setString("name", "Daniel");
        save(doc1a);

        MutableDocument doc1b = new MutableDocument("doc1");
        doc1b.setString("name", "Tiger");
        save(doc1b);

        assertEquals("Tiger", doc1b.getString("name"));
    }

    // REF: https://github.com/couchbase/couchbase-lite-android/issues/1293
    @Test
    public void testConflictWithoutCommonAncestor() throws CouchbaseLiteException {
        closeDB();

        // open db with special conflict resolver
        openDB(new ConflictResolver() {
            @Override
            public Document resolve(Conflict conflict) {
                assertNotNull(conflict);
                assertNull(conflict.getBase()); // make sure base is null
                return conflict.getBase();      // null -> cause 409
            }
        });

        String docID = "doc1";
        Map<String, Object> props = new HashMap<>();
        props.put("hello", "world");

        //STEP 1: Created a new document with id = "doc1"
        MutableDocument doc = new MutableDocument(docID, props);
        doc = db.save(doc).toMutable();

        //STEP 2: Added a revision to the document
        doc.setValue("university", 1);
        db.save(doc);

        // STEP3: Create Conflict as follows
        doc = new MutableDocument(docID, props);
        doc.setValue("university", 2);
        try {
            db.save(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            // Currently returned error code is LiteCore error code.
            // This could change to HTTP error code.
            assertEquals(CBLError.Domain.CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorConflict, e.getCode());
        }
    }
}
