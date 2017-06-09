/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorBusy;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorTransactionNotClosed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class DatabaseTest extends BaseTest {
    private static final String TAG = DatabaseTest.class.getName();

    final static String kDatabaseTestBlob = "i'm blob";

    //---------------------------------------------
    //  Helper methods
    //---------------------------------------------

    static class DummyResolver implements ConflictResolver {
        @Override
        public ReadOnlyDocument resolve(Conflict conflict) {
            return null;
        }
    }

    // helper method to open database
    private Database openDatabase(String dbName) {
        DatabaseConfiguration options = new DatabaseConfiguration(this.context);
        options.setDirectory(dir);
        Database db = new Database(dbName, options);
        assertEquals(dbName, db.getName());
        assertTrue(db.getPath().getAbsolutePath().endsWith(".cblite2"));
        assertEquals(0, db.getCount());
        return db;
    }

    // helper method to delete database
    void deleteDatabase(Database db) {
        File path = db.getPath();
        // if path is null, db is already closed
        if (path != null)
            assertTrue(path.exists());
        db.delete();
        // if path is null, db is already closed before db.delete()
        if (path != null)
            assertFalse(path.exists());
    }

    // helper method to save document
    Document generateDocument(String docID) {
        Document doc = createDocument(docID);
        doc.set("key", 1);
        save(doc);
        assertEquals(1, db.getCount());
        assertEquals(1, doc.getSequence());
        return doc;
    }

    // helper methods to verify getDoc
    void verifyGetDocument(String docID) {
        verifyGetDocument(docID, 1);
    }

    // helper methods to verify getDoc
    void verifyGetDocument(String docID, int value) {
        verifyGetDocument(db, docID, value);
    }

    // helper methods to verify getDoc
    void verifyGetDocument(Database db, String docID) {
        verifyGetDocument(db, docID, 1);
    }

    // helper methods to verify getDoc
    void verifyGetDocument(Database db, String docID, int value) {
        Document doc = db.getDocument(docID);
        assertNotNull(doc);
        assertEquals(docID, doc.getId());
        assertFalse(doc.isDeleted());
        assertEquals(value, ((Number) doc.getObject("key")).intValue());
    }

    // helper method to purge doc and verify doc.
    void purgeDocAndVerify(Document doc) {
        String docID = doc.getId();
        db.purge(doc);
        assertEquals(docID, doc.getId());         // docID should be same
        assertEquals(0, doc.getSequence());       // sequence should be reset to 0
        assertFalse(doc.isDeleted());             // delete flag should be reset to true
        assertEquals(null, doc.getObject("key")); // content should be empty
    }

    // helper method to save n number of docs
    List<Document> createDocs(int n) {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Document doc = createDocument(String.format(Locale.US, "doc_%03d", i));
            doc.set("key", i);
            save(doc);
            docs.add(doc);
        }
        assertEquals(n, db.getCount());
        return docs;
    }

    // helper method to verify n number of docs
    void validateDocs(int n) {
        for (int i = 0; i < n; i++) {
            verifyGetDocument(String.format(Locale.US, "doc_%03d", i), i);
        }
    }

    //---------------------------------------------
    //  setUp/tearDown
    //---------------------------------------------

    @Before
    public void setUp() {
        super.setUp();
        Log.e("DatabaseTest", "setUp");
    }

    @After
    public void tearDown() {
        Log.e("DatabaseTest", "tearDown");
        super.tearDown();
    }

    //---------------------------------------------
    //  DatabaseConfiguration
    //---------------------------------------------
    @Test
    public void testCreateConfiguration() {
        // Default:
        DatabaseConfiguration config1 = new DatabaseConfiguration(this.context);
        config1.setDirectory(new File("/tmp"));
        assertNotNull(config1.getDirectory());
        assertTrue(config1.getDirectory().getAbsolutePath().length() > 0);
        assertNull(config1.getConflictResolver());
        assertNull(config1.getEncryptionKey());

        // Default + Copy
        DatabaseConfiguration config1a = config1.copy();
        assertNotNull(config1a.getDirectory());
        assertTrue(config1a.getDirectory().getAbsolutePath().length() > 0);
        assertNull(config1a.getConflictResolver());
        assertNull(config1a.getEncryptionKey());

        // Custom
        DummyResolver resolver = new DummyResolver();
        DatabaseConfiguration config2 = new DatabaseConfiguration(this.context);
        config2.setDirectory(new File("/tmp/mydb"));
        config2.setConflictResolver(resolver);
        config2.setEncryptionKey("key");
        assertEquals("/tmp/mydb", config2.getDirectory().getAbsolutePath());
        assertEquals(resolver, config2.getConflictResolver());
        assertEquals("key", config2.getEncryptionKey());

        // Custom + Copy
        DatabaseConfiguration config2a = config2.copy();
        assertEquals("/tmp/mydb", config2a.getDirectory().getAbsolutePath());
        assertEquals(resolver, config2a.getConflictResolver());
        assertEquals("key", config2a.getEncryptionKey());
    }

    @Test
    public void testGetSetConfiguration() {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(this.db.getConfig().getDirectory());

        Database db = new Database("db", config);
        try {
            assertNotNull(db.getConfig());
            assertTrue(db.getConfig() != config);
            assertEquals(db.getConfig().getDirectory(), config.getDirectory());
            assertEquals(db.getConfig().getConflictResolver(), config.getConflictResolver());
            assertEquals(db.getConfig().getEncryptionKey(), config.getEncryptionKey());
        } finally {
            db.close();
        }
    }

    @Test
    public void testConfigurationIsCopiedWhenGetSet() {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(this.db.getConfig().getDirectory());

        Database db = new Database("db", config);
        try {
            config.setConflictResolver(new DummyResolver());
            assertNotNull(db.getConfig());
            assertTrue(db.getConfig() != config);
            assertTrue(db.getConfig().getConflictResolver() != config.getConflictResolver());
        } finally {
            db.close();
        }
    }

    @Test
    public void testDatabaseConfigurationWithAndroidContect() {
        DatabaseConfiguration config = new DatabaseConfiguration(context);
        assertEquals(config.getDirectory(), context.getFilesDir());

        Database db = new Database("db", config);
        try {
            assertTrue(db.getPath().getAbsolutePath().contains(context.getFilesDir().getAbsolutePath()));
        } finally {
            db.close();
        }
    }

    //---------------------------------------------
    //  Create Database
    //---------------------------------------------

    @Test
    public void testCreate() {
        // create db with default options
        Database db = openDatabase("db");
        try {
            assertNotNull(db);
            assertEquals(0, db.getCount());
        } finally {
            // delete database
            deleteDatabase(db);
        }
    }

    @Test
    public void testCreateWithDefaultOption() {
        Database db = new Database("db", new DatabaseConfiguration(this.context));
        try {
        } finally {
            // delete database
            deleteDatabase(db);
        }
    }

    @Test
    public void testCreateWithSpecialCharacterDBNames() {
        Database db = openDatabase("`~@#$%^&*()_+{}|\\][=-/.,<>?\":;'");
        try {
            assertNotNull(db);
            assertEquals(0, db.getCount());
        } finally {
            // delete database
            deleteDatabase(db);
        }
    }

    @Test
    public void testCreateWithEmptyDBNames() {
        try {
            Database db = openDatabase("");
            fail();
        } catch (IllegalArgumentException e) {
            // NOTE: CBL Android's Database constructor does not work without specified directory.
        }
    }

    @Test
    public void testCreateWithCustomDirectory() {

        String dbName = "db";

        File dir = new File(context.getFilesDir(), "CouchbaseLite");
        try {
            Database.delete(dbName, dir);
        } catch (CouchbaseLiteException ex) {
        }

        assertFalse(Database.exists(dbName, dir));

        // create db with custom directory
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(dir);
        Database db = new Database(dbName, config);
        try {
            assertNotNull(db);
            assertEquals(dbName, db.getName());
            assertTrue(db.getPath().getAbsolutePath().endsWith(".cblite2"));
            assertTrue(db.getPath().getAbsolutePath().indexOf(dir.getPath()) != -1);
            assertTrue(Database.exists(dbName, dir));
            assertEquals(0, db.getCount());
        } finally {
            // delete database
            deleteDatabase(db);
        }
    }

    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    public void testGetNonExistingDocWithID() {
        assertTrue(db.getDocument("non-exist") == null);
    }

    @Test
    public void testGetExistingDocWithID() {
        // store doc
        String docID = "doc1";
        generateDocument(docID);

        // validate document by getDocument
        verifyGetDocument(docID);
    }

    // TODO: @Test
    public void testGetExistingDocWithIDFromDifferentDBInstance() {
        // store doc
        String docID = "doc1";
        generateDocument(docID);

        // open db with same db name and default option
        Database otherDB = openDatabase(db.getName());
        assertNotNull(otherDB);
        assertTrue(db != otherDB);

        // get doc from other DB.
        assertEquals(1, otherDB.getCount());
        assertTrue(otherDB.contains(docID));

        verifyGetDocument(otherDB, docID);

        otherDB.close();
    }

    @Test
    public void testGetExistingDocWithIDInBatch() {
        // Save 10 docs:
        createDocs(10);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                validateDocs(10);
            }
        });
    }

    @Test
    public void testGetDocFromClosedDB() {
        // Store doc:
        generateDocument("doc1");

        // Close db:
        db.close();

        try {
            Document doc = db.getDocument("doc1");
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testGetDocFromDeletedDB() {
        // Store doc:
        generateDocument("doc1");

        // Close db:
        deleteDatabase(db);
        try {
            Document doc = db.getDocument("doc1");
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------
    private void testSaveNewDocWithID(String docID) {
        // store doc
        generateDocument(docID);

        assertEquals(1, db.getCount());
        assertTrue(db.contains(docID));

        // validate document by getDocument
        verifyGetDocument(docID);
    }

    @Test
    public void testSaveNewDocWithID() {
        testSaveNewDocWithID("doc1");
    }

    @Test
    public void testSaveNewDocWithSpecialCharactersDocID() {
        testSaveNewDocWithID("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'");
    }

    @Test
    public void testSaveDoc() {
        // store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // update doc
        doc.set("key", 2);
        save(doc);

        assertEquals(1, db.getCount());
        assertTrue(db.contains(docID));

        // validate document by getDocument
        verifyGetDocument(docID, 2);
    }

    // TODO : @Test
    // LiteCore throws exception in tearDown
    public void testSaveDocInDifferentDBInstance() {
        // Store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default
        Database otherDB = openDatabase(db.getName());
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(1, otherDB.getCount());

        // Update doc & store it into different instance
        doc.set("key", 2);
        CouchbaseLiteException exception = null;
        try {
            otherDB.save(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.Forbidden, e.getCode());
        } finally {
            // close otherDb
            otherDB.close();
        }
    }

    @Test
    public void testSaveDocInDifferentDB() {
        // Store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default
        Database otherDB = openDatabase("otherDB");
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(0, otherDB.getCount());

        // Update doc & store it into different instance
        doc.set("key", 2);
        CouchbaseLiteException exception = null;
        try {
            otherDB.save(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.Forbidden, e.getCode());
        } finally {
            // delete otherDb
            deleteDatabase(otherDB);
        }
    }

    @Test
    public void testSaveSameDocTwice() {
        String docID = "doc1";
        Document doc = generateDocument(docID);
        save(doc);
        assertEquals(docID, doc.getId());
        assertEquals(1, db.getCount());
    }

    @Test
    public void testSaveInBatch() {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                createDocs(10);
            }
        });
        assertEquals(10, db.getCount());
        validateDocs(10);
    }

    @Test
    public void testSaveDocToClosedDB() {
        db.close();

        Document doc = createDocument("doc1");
        doc.set("key", 1);

        try {
            save(doc);
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testSaveDocToDeletedDB() {
        // Delete db:
        deleteDatabase(db);

        Document doc = createDocument("doc1");
        doc.set("key", 1);

        try {
            save(doc);
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    public void testDeletePreSaveDoc() {
        Document doc = createDocument("doc1");
        doc.set("key", 1);
        try {
            db.delete(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.NotFound, e.getCode());
        }
    }

    @Test
    public void testDeleteDoc() {
        String docID = "doc1";
        Document doc = generateDocument(docID);

        db.delete(doc);
        assertEquals(0, db.getCount());

        assertEquals(docID, doc.getId());
        assertTrue(doc.isDeleted());
        assertEquals(2, doc.getSequence());
        assertTrue(doc.getObject("key") == null);
    }

    // TODO : @Test
    // Error from LiteCore in tearDown
    public void testDeleteDocInDifferentDBInstance() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with same name:
        // Create db with default
        Database otherDB = openDatabase(db.getName());
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertTrue(otherDB.contains(docID));
        assertEquals(1, otherDB.getCount());

        // Delete from the different db instance:
        try {
            otherDB.delete(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.Forbidden, e.getCode());
        } finally {
            // close otherDb
            otherDB.close();
        }
    }

    @Test
    public void testDeleteDocInDifferentDB() {
        // Store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default
        Database otherDB = openDatabase("otherDB");
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertFalse(otherDB.contains(docID));
        assertEquals(0, otherDB.getCount());

        // Delete from the different db:
        try {
            otherDB.delete(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.Forbidden, e.getCode());
        } finally {
            // close otherDb
            deleteDatabase(otherDB);
        }
    }

    @Test
    public void testDeleteSameDocTwice() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // First time deletion:
        db.delete(doc);
        assertEquals(0, db.getCount());
        assertNull(doc.getObject("key"));
        assertEquals(2, doc.getSequence());
        assertTrue(doc.isDeleted());

        // Second time deletion:
        db.delete(doc);
        assertEquals(0, db.getCount());
        assertNull(doc.getObject("key"));
        assertEquals(3, doc.getSequence());
        assertTrue(doc.isDeleted());
    }

    @Test
    public void testDeleteDocInBatch() {
        // Save 10 docs:
        createDocs(10);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    String docID = String.format(Locale.US, "doc_%03d", i);
                    Document doc = db.getDocument(docID);
                    db.delete(doc);
                    assertNull(doc.getObject("key"));
                    assertTrue(doc.isDeleted());
                    assertEquals((9 - i), db.getCount());
                }
            }
        });

        assertEquals(0, db.getCount());
    }

    @Test
    public void testDeleteDocOnClosedDB() {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Close db:
        db.close();

        // Delete doc from db:
        try {
            db.delete(doc);
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testDeleteDocOnDeletedDB() {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Delete db:
        deleteDatabase(db);

        // Delete doc from db:
        try {
            db.delete(doc);
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    public void testPurgePreSaveDoc() {
        Document doc = createDocument("doc1");
        try {
            db.purge(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.NotFound, e.getCode());
        }
        assertEquals(0, db.getCount());
    }

    @Test
    public void testPurgeDoc() {
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Purge Doc
        purgeDocAndVerify(doc);
        assertEquals(0, db.getCount());

        save(doc);
        assertEquals(2, doc.getSequence());
    }

    // TODO : @Test
    // Error from LiteCore in tearDown
    public void testPurgeDocInDifferentDBInstance() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default:
        Database otherDB = openDatabase(db.getName());
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(1, otherDB.getCount());
        assertTrue(otherDB.contains(docID));

        // purge document against other db instance:
        try {
            otherDB.purge(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.Forbidden, e.getCode());
        } finally {
            // close otherDb
            otherDB.close();
        }
    }

    @Test
    public void testPurgeDocInDifferentDB() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default:
        Database otherDB = openDatabase("otherDB");
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(0, otherDB.getCount());
        assertFalse(otherDB.contains(docID));

        // Purge document against other db:
        try {
            otherDB.purge(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.Forbidden, e.getCode());
        } finally {
            // close otherDb
            deleteDatabase(otherDB);
        }
    }

    @Test
    public void testPurgeSameDocTwice() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Get the document for the second purge:
        Document doc1 = db.getDocument(docID);

        // Purge the document first time:
        purgeDocAndVerify(doc);
        assertEquals(0, db.getCount());

        // Purge the document second time:
        purgeDocAndVerify(doc1);
        assertEquals(0, db.getCount());
    }

    @Test
    public void testPurgeDocInBatch() {
        // Save 10 docs:
        createDocs(10);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    String docID = String.format(Locale.US, "doc_%03d", i);
                    Document doc = db.getDocument(docID);
                    purgeDocAndVerify(doc);
                    assertEquals((9 - i), db.getCount());
                }
            }
        });

        assertEquals(0, db.getCount());
    }

    @Test
    public void testPurgeDocOnClosedDB() {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Close db:
        db.close();

        // Purge doc:
        try {
            db.purge(doc);
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testPurgeDocOnDeletedDB() {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Close db:
        deleteDatabase(db);

        // Purge doc:
        try {
            db.purge(doc);
            fail();
        } catch (IllegalStateException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Close Database
    //---------------------------------------------
    @Test
    public void testClose() {
        db.close();
    }

    @Test
    public void testCloseTwice() {
        db.close();
        db.close();
    }

    @Test
    public void testCloseThenAccessDoc() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Close db:
        db.close();

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getObject("key")).intValue());
        doc.set("key", 2);
        doc.set("key1", "value");
    }

    @Test
    public void testCloseThenAccessBlob() {
        // Store doc with blob:
        Document doc = generateDocument("doc1");
        doc.set("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
        save(doc);

        // Close db:
        db.close();

        // content should be accessible & modifiable without error
        assertTrue(doc.getObject("blob") instanceof Blob);
        Blob blob = doc.getBlob("blob");
        assertEquals(8, blob.length());
        assertNull(blob.getContent());
    }

    @Test
    public void testCloseThenGetDatabaseName() {
        db.close();
        assertEquals("testdb", db.getName());
    }

    @Test
    public void testCloseThenGetDatabasePath() {
        db.close();
        assertTrue(db.getPath() == null);
    }

    @Test
    public void testCloseThenCallInBatch() {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                // delete db
                try {
                    db.close();
                    fail();
                } catch (CouchbaseLiteException e) {
                    assertEquals(LiteCoreDomain, e.getDomain());
                    // 26 -> kC4ErrorTransactionNotClosed: Function cannot be called while in a transaction
                    assertEquals(kC4ErrorTransactionNotClosed, e.getCode()); // 26
                }
            }
        });
    }

    @Test
    public void testCloseThenDeleteDatabase() {
        db.close();
        try {
            deleteDatabase(db);
            fail();
        } catch (IllegalStateException e) {
            // should come here!
        }
    }

    //---------------------------------------------
    //  Delete Database
    //---------------------------------------------
    @Test
    public void testDelete() {
        deleteDatabase(db);
    }

    @Test
    public void testDeleteTwice() {
        // delete db twice
        File path = db.getPath();
        assertTrue(path.exists());
        db.delete();
        try {
            db.delete();
            fail();
        } catch (IllegalStateException e) {
            // should come here!
        }
        assertFalse(path.exists());
    }

    @Test
    public void testDeleteThenAccessDoc() {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Delete db:
        deleteDatabase(db);

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getObject("key")).intValue());
        doc.set("key", 2);
        doc.set("key1", "value");
    }

    @Test
    public void testDeleteThenAccessBlob() {
        // Store doc with blob:
        Document doc = generateDocument("doc1");
        doc.set("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
        save(doc);

        // Delete db:
        deleteDatabase(db);

        // content should be accessible & modifiable without error
        assertTrue(doc.getObject("blob") instanceof Blob);
        Blob blob = doc.getBlob("blob");
        assertEquals(8, blob.length());
        assertNull(blob.getContent());
    }

    @Test
    public void testDeleteThenGetDatabaseName() {
        // delete db
        deleteDatabase(db);

        assertEquals("testdb", db.getName());
    }

    @Test
    public void testDeleteThenGetDatabasePath() {
        // delete db
        deleteDatabase(db);

        assertTrue(db.getPath() == null);
    }

    @Test
    public void testDeleteThenCallInBatch() {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                // delete db
                try {
                    db.delete();
                    fail();
                } catch (CouchbaseLiteException e) {
                    assertEquals(LiteCoreDomain, e.getDomain());
                    // 26 -> kC4ErrorTransactionNotClosed: Function cannot be called while in a transaction
                    assertEquals(kC4ErrorTransactionNotClosed, e.getCode()); // 26
                }
            }
        });
    }

    @Test
    public void testDeleteDBOpenedByOtherInstance() {
        Database otherDB = openDatabase(db.getName());
        try {
            assertTrue(db != otherDB);

            // delete db
            try {
                db.delete();
                fail();
            } catch (CouchbaseLiteException e) {
                assertEquals(LiteCoreDomain, e.getDomain());
                assertEquals(kC4ErrorBusy, e.getCode()); // 24
            }
        } finally {
            otherDB.close();
        }
    }

    //---------------------------------------------
    //  Delete Database (static)
    //---------------------------------------------

    @Test
    public void testDeleteByStaticMethod() {
        String dbName = "db";

        // create db with custom directory
        Database db = openDatabase(dbName);
        File path = db.getPath();

        // close db before delete
        db.close();

        Database.delete(dbName, dir);
        assertFalse(path.exists());
    }

    @Test
    public void testDeleteOpeningDBByStaticMethod() {
        Database db = openDatabase("db");
        try {
            try {
                Database.delete("db", dir);
                fail();
            } catch (CouchbaseLiteException e) {
                assertEquals(LiteCoreDomain, e.getDomain());
                assertEquals(kC4ErrorBusy, e.getCode()); // 24
            } finally {
                ;
            }
        } finally {
            deleteDatabase(db);
        }
    }

    @Test
    public void testDeleteNonExistingDBWithDefaultDir() {
        try {
            Database.delete("notexistdb", null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDeleteNonExistingDB() {
        try {
            Database.delete("notexistdb", dir);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.NotFound, e.getCode());
        }
    }

    //---------------------------------------------
    //  Database Existing
    //---------------------------------------------

    @Test
    public void testDatabaseExistsWithDir() {
        assertFalse(Database.exists("db", dir));

        // create db with custom directory
        Database db = openDatabase("db");
        File path = db.getPath();

        assertTrue(Database.exists("db", dir));

        db.close();

        assertTrue(Database.exists("db", dir));

        Database.delete("db", dir);
        assertFalse(path.exists());

        assertFalse(Database.exists("db", dir));
    }

    @Test
    public void testDatabaseExistsAgainstNonExistDBWithDefaultDir() {
        try {
            Database.exists("nonexist", null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDatabaseExistsAgainstNonExistDB() {
        assertFalse(Database.exists("nonexist", dir));
    }

    //TODO: @Test - test fails because database.compact() does not delete blob files.
    public void testCompact() {
        final List<Document> docs = createDocs(20);

        // Update each doc 25 times:
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (Document doc : docs) {
                    for (int i = 0; i < 25; i++) {
                        doc.set("number", i);
                        save(doc);
                    }
                }
            }
        });

        // Add each doc with a blob object:
        for (Document doc : docs) {
            doc.set("blob", new Blob("text/plain", doc.getId().getBytes()));
            save(doc);
        }

        assertEquals(20, db.getCount());

        File attsDir = new File(db.getPath(), "Attachments");
        assertTrue(attsDir.exists());
        assertTrue(attsDir.isDirectory());
        File[] atts = attsDir.listFiles();
        assertEquals(20, atts.length);

        // Compact:
        db.compact();

        // Delete all docs:
        for (Document doc : docs) {
            db.delete(doc);
            assertTrue(doc.isDeleted());
        }

        // Compact:
        db.compact();

        atts = attsDir.listFiles();
        assertEquals(0, atts.length);
    }
}
