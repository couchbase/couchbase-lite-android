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

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBusy;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorTransactionNotClosed;
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
    private Database openDatabase(String dbName) throws CouchbaseLiteException {
        return openDatabase(dbName, true);
    }
    private Database openDatabase(String dbName, boolean countCheck) throws CouchbaseLiteException {
        DatabaseConfiguration options = new DatabaseConfiguration(this.context);
        options.setDirectory(dir);
        Database db = new Database(dbName, options);
        assertEquals(dbName, db.getName());
        assertTrue(db.getPath().getAbsolutePath().endsWith(".cblite2"));
        if(countCheck)
            assertEquals(0, db.getCount());
        return db;
    }

    // helper method to delete database
    void deleteDatabase(Database db) throws CouchbaseLiteException {
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
    Document generateDocument(String docID) throws CouchbaseLiteException {
        Document doc = createDocument(docID);
        doc.setObject("key", 1);
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
    void purgeDocAndVerify(Document doc) throws CouchbaseLiteException {
        String docID = doc.getId();
        db.purge(doc);
        assertEquals(docID, doc.getId());         // docID should be same
        assertEquals(0, doc.getSequence());       // sequence should be reset to 0
        assertFalse(doc.isDeleted());             // delete flag should be reset to true
        assertEquals(null, doc.getObject("key")); // content should be empty
    }

    // helper method to save n number of docs
    List<Document> createDocs(int n) throws CouchbaseLiteException {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Document doc = createDocument(String.format(Locale.US, "doc_%03d", i));
            doc.setObject("key", i);
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
    public void setUp() throws Exception {
        super.setUp();
        Log.i("DatabaseTest", "setUp");
    }

    @After
    public void tearDown() throws Exception {
        Log.i("DatabaseTest", "tearDown");
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
    public void testGetSetConfiguration() throws CouchbaseLiteException {
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
    public void testConfigurationIsCopiedWhenGetSet() throws CouchbaseLiteException {
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
    public void testDatabaseConfigurationWithAndroidContect() throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration(context);
        assertEquals(config.getDirectory(), context.getFilesDir());

        Database db = new Database("db", config);
        try {
            String expectedPath = context.getFilesDir().getAbsolutePath();
            assertTrue(db.getPath().getAbsolutePath().contains(expectedPath));
        } finally {
            db.close();
        }
    }

    //---------------------------------------------
    //  Create Database
    //---------------------------------------------

    @Test
    public void testCreate() throws CouchbaseLiteException {
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
    public void testCreateWithDefaultOption() throws CouchbaseLiteException {
        Database db = new Database("db", new DatabaseConfiguration(this.context));
        try {
        } finally {
            // delete database
            deleteDatabase(db);
        }
    }

    @Test
    public void testCreateWithSpecialCharacterDBNames() throws CouchbaseLiteException {
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
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateWithCustomDirectory() throws CouchbaseLiteException {

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
    public void testGetExistingDocWithID() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        generateDocument(docID);

        // validate document by getDocument
        verifyGetDocument(docID);
    }

    @Test
    public void testGetExistingDocWithIDFromDifferentDBInstance() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        generateDocument(docID);

        // open db with same db name and default option
        Database otherDB = openDatabase(db.getName(), false);
        assertNotNull(otherDB);
        assertTrue(db != otherDB);

        // get doc from other DB.
        assertEquals(1, otherDB.getCount());
        assertTrue(otherDB.contains(docID));

        verifyGetDocument(otherDB, docID);

        otherDB.close();
    }

    @Test
    public void testGetExistingDocWithIDInBatch() throws CouchbaseLiteException {
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
    public void testGetDocFromClosedDB() throws CouchbaseLiteException {
        // Store doc:
        generateDocument("doc1");

        // Close db:
        db.close();

        try {
            Document doc = db.getDocument("doc1");
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testGetDocFromDeletedDB() throws CouchbaseLiteException {
        // Store doc:
        generateDocument("doc1");

        // Close db:
        deleteDatabase(db);
        try {
            Document doc = db.getDocument("doc1");
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------
    private void testSaveNewDocWithID(String docID) throws CouchbaseLiteException {
        // store doc
        generateDocument(docID);

        assertEquals(1, db.getCount());
        assertTrue(db.contains(docID));

        // validate document by getDocument
        verifyGetDocument(docID);
    }

    @Test
    public void testSaveNewDocWithID() throws CouchbaseLiteException {
        testSaveNewDocWithID("doc1");
    }

    @Test
    public void testSaveNewDocWithSpecialCharactersDocID() throws CouchbaseLiteException {
        testSaveNewDocWithID("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'");
    }

    @Test
    public void testSaveDoc() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // update doc
        doc.setObject("key", 2);
        save(doc);

        assertEquals(1, db.getCount());
        assertTrue(db.contains(docID));

        // validate document by getDocument
        verifyGetDocument(docID, 2);
    }

    @Test
    public void testSaveDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default
        Database otherDB = openDatabase(db.getName(), false);
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(1, otherDB.getCount());

        // Update doc & store it into different instance
        doc.setObject("key", 2);
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
    public void testSaveDocInDifferentDB() throws CouchbaseLiteException {
        // Store doc
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default
        Database otherDB = openDatabase("otherDB");
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(0, otherDB.getCount());

        // Update doc & store it into different instance
        doc.setObject("key", 2);
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
    public void testSaveSameDocTwice() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = generateDocument(docID);
        save(doc);
        assertEquals(docID, doc.getId());
        assertEquals(1, db.getCount());
    }

    @Test
    public void testSaveInBatch() throws CouchbaseLiteException {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                try {
                    createDocs(10);
                } catch (CouchbaseLiteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        assertEquals(10, db.getCount());
        validateDocs(10);
    }

    @Test
    public void testSaveDocToClosedDB() throws CouchbaseLiteException {
        db.close();

        Document doc = createDocument("doc1");
        doc.setObject("key", 1);

        try {
            save(doc);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testSaveDocToDeletedDB() throws CouchbaseLiteException {
        // Delete db:
        deleteDatabase(db);

        Document doc = createDocument("doc1");
        doc.setObject("key", 1);

        try {
            save(doc);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    public void testDeletePreSaveDoc() {
        Document doc = createDocument("doc1");
        doc.setObject("key", 1);
        try {
            db.delete(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.NotFound, e.getCode());
        }
    }

    @Test
    public void testDeleteDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = generateDocument(docID);

        db.delete(doc);
        assertEquals(0, db.getCount());

        assertEquals(docID, doc.getId());
        assertTrue(doc.isDeleted());
        assertEquals(2, doc.getSequence());
        assertTrue(doc.getObject("key") == null);
    }

    @Test
    public void testDeleteDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with same name:
        // Create db with default
        Database otherDB = openDatabase(db.getName(), false);
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
    public void testDeleteDocInDifferentDB() throws CouchbaseLiteException {
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
    public void testDeleteSameDocTwice() throws CouchbaseLiteException {
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
    public void testDeleteDocInBatch() throws CouchbaseLiteException {
        // Save 10 docs:
        createDocs(10);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    String docID = String.format(Locale.US, "doc_%03d", i);
                    Document doc = db.getDocument(docID);
                    try {
                        db.delete(doc);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                    assertNull(doc.getObject("key"));
                    assertTrue(doc.isDeleted());
                    assertEquals((9 - i), db.getCount());
                }
            }
        });

        assertEquals(0, db.getCount());
    }

    @Test
    public void testDeleteDocOnClosedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Close db:
        db.close();

        // Delete doc from db:
        try {
            db.delete(doc);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testDeleteDocOnDeletedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Delete db:
        deleteDatabase(db);

        // Delete doc from db:
        try {
            db.delete(doc);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
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
    public void testPurgeDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Purge Doc
        purgeDocAndVerify(doc);
        assertEquals(0, db.getCount());

        save(doc);
        assertEquals(2, doc.getSequence());
    }

    @Test
    public void testPurgeDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Create db with default:
        Database otherDB = openDatabase(db.getName(), false);
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
    public void testPurgeDocInDifferentDB() throws CouchbaseLiteException {
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
    public void testPurgeSameDocTwice() throws CouchbaseLiteException {
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
    public void testPurgeDocInBatch() throws CouchbaseLiteException {
        // Save 10 docs:
        createDocs(10);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    String docID = String.format(Locale.US, "doc_%03d", i);
                    Document doc = db.getDocument(docID);
                    try {
                        purgeDocAndVerify(doc);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                    assertEquals((9 - i), db.getCount());
                }
            }
        });

        assertEquals(0, db.getCount());
    }

    @Test
    public void testPurgeDocOnClosedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Close db:
        db.close();

        // Purge doc:
        try {
            db.purge(doc);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    @Test
    public void testPurgeDocOnDeletedDB() throws CouchbaseLiteException {
        // Store doc:
        Document doc = generateDocument("doc1");

        // Close db:
        deleteDatabase(db);

        // Purge doc:
        try {
            db.purge(doc);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should be thrown IllegalStateException!!
        }
    }

    //---------------------------------------------
    //  Close Database
    //---------------------------------------------
    @Test
    public void testClose() throws CouchbaseLiteException {
        db.close();
    }

    @Test
    public void testCloseTwice() throws CouchbaseLiteException {
        db.close();
        db.close();
    }

    @Test
    public void testCloseThenAccessDoc() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Close db:
        db.close();

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getObject("key")).intValue());
        doc.setObject("key", 2);
        doc.setObject("key1", "value");
    }

    @Test
    public void testCloseThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        Document doc = generateDocument("doc1");
        doc.setObject("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
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
    public void testCloseThenGetDatabaseName() throws CouchbaseLiteException {
        db.close();
        assertEquals("testdb", db.getName());
    }

    @Test
    public void testCloseThenGetDatabasePath() throws CouchbaseLiteException {
        db.close();
        assertTrue(db.getPath() == null);
    }

    @Test
    public void testCloseThenCallInBatch() throws CouchbaseLiteException {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                // delete db
                try {
                    db.close();
                    fail();
                } catch (CouchbaseLiteException e) {
                    assertEquals(LiteCoreDomain, e.getDomain());
                    // 26 -> kC4ErrorTransactionNotClosed:
                    //          Function cannot be called while in a transaction
                    assertEquals(kC4ErrorTransactionNotClosed, e.getCode()); // 26
                }
            }
        });
    }

    @Test
    public void testCloseThenDeleteDatabase() throws CouchbaseLiteException {
        db.close();
        try {
            deleteDatabase(db);
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should come here!
        }
    }

    //---------------------------------------------
    //  Delete Database
    //---------------------------------------------
    @Test
    public void testDelete() throws CouchbaseLiteException {
        deleteDatabase(db);
    }

    @Test
    public void testDeleteTwice() throws CouchbaseLiteException {
        // delete db twice
        File path = db.getPath();
        assertTrue(path.exists());
        db.delete();
        try {
            db.delete();
            fail();
        } catch (CouchbaseLiteRuntimeException e) {
            // should come here!
        }
        assertFalse(path.exists());
    }

    @Test
    public void testDeleteThenAccessDoc() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Delete db:
        deleteDatabase(db);

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getObject("key")).intValue());
        doc.setObject("key", 2);
        doc.setObject("key1", "value");
    }

    @Test
    public void testDeleteThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        Document doc = generateDocument("doc1");
        doc.setObject("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
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
    public void testDeleteThenGetDatabaseName() throws CouchbaseLiteException {
        // delete db
        deleteDatabase(db);

        assertEquals("testdb", db.getName());
    }

    @Test
    public void testDeleteThenGetDatabasePath() throws CouchbaseLiteException {
        // delete db
        deleteDatabase(db);

        assertTrue(db.getPath() == null);
    }

    @Test
    public void testDeleteThenCallInBatch() throws CouchbaseLiteException {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                // delete db
                try {
                    db.delete();
                    fail();
                } catch (CouchbaseLiteException e) {
                    assertEquals(LiteCoreDomain, e.getDomain());
                    // 26 -> kC4ErrorTransactionNotClosed:
                    //          Function cannot be called while in a transaction
                    assertEquals(kC4ErrorTransactionNotClosed, e.getCode()); // 26
                }
            }
        });
    }

    @Test
    public void testDeleteDBOpenedByOtherInstance() throws CouchbaseLiteException {
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
    public void testDeleteByStaticMethod() throws CouchbaseLiteException {
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
    public void testDeleteOpeningDBByStaticMethod() throws CouchbaseLiteException {
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
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
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
    public void testDatabaseExistsWithDir() throws CouchbaseLiteException {
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

    // TODO: This test does not pass with Android API16 arm-v7a
    //@Test
    public void testCompact() throws CouchbaseLiteException {
        final List<Document> docs = createDocs(20);

        // Update each doc 25 times:
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (Document doc : docs) {
                    for (int i = 0; i < 25; i++) {
                        doc.setObject("number", i);
                        try {
                            save(doc);
                        } catch (CouchbaseLiteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        });

        // Add each doc with a blob object:
        for (Document doc : docs) {
            doc.setObject("blob", new Blob("text/plain", doc.getId().getBytes()));
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

    @Test
    public void testOverwriteDocWithNewDocInstgance() throws CouchbaseLiteException {
        // REF: https://github.com/couchbase/couchbase-lite-android/issues/1231

        Document doc1 = new Document("abc");
        doc1.setObject("someKey", "someVar");
        db.save(doc1);

        // This cause conflict, DefaultConflictResolver should be applied.
        Document doc2 = new Document("abc");
        doc2.setObject("someKey", "newVar");
        db.save(doc2);

        // NOTE: Both doc1 and doc2 are generation 1. So mine (doc2) should win.
        assertEquals(1, db.getCount());
        assertEquals("newVar", db.getDocument("abc").getString("someKey"));
    }

    @Test
    public void testCopy() throws CouchbaseLiteException {
        for (int i = 0; i < 10; i++) {
            String docID = String.format(Locale.US, "doc_%03d", i);
            Document doc = createDocument(docID);
            doc.setObject("name", docID);
            byte[] data = docID.getBytes();
            Blob blob = new Blob("text/plain", data);
            doc.setObject("data", blob);
            save(doc);
        }

        String dbName = "nudb";
        DatabaseConfiguration config = db.getConfig();
        File dir = config.getDirectory();

        // Make sure no an existing database at the new location:
        if (Database.exists(dbName, dir))
            Database.delete(dbName, dir);

        // Copy:
        Database.copy(db.getPath(), dbName, config);

        // Verify:
        assertTrue(Database.exists(dbName, dir));

        Database nudb = new Database(dbName, config);
        assertNotNull(nudb);
        assertEquals(10, nudb.getCount());

        Expression DOCID = Expression.meta().getId();
        SelectResult S_DOCID = SelectResult.expression(DOCID);
        Query query = Query.select(S_DOCID).from(DataSource.database(nudb));
        ResultSet rs = query.run();
        for (Result r : rs) {
            String docID = r.getString(0);
            assertNotNull(docID);

            Document doc = nudb.getDocument(docID);
            assertNotNull(doc);
            assertEquals(docID, doc.getString("name"));

            Blob blob = doc.getBlob("data");
            assertNotNull(blob);

            String data = new String(blob.getContent());
            assertEquals(docID, data);
        }

        // Clean up:
        nudb.close();
        Database.delete(dbName, dir);
    }
}
