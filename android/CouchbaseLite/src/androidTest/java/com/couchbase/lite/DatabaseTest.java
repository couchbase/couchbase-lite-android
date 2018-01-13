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

import android.os.Build;

import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
        public Document resolve(Conflict conflict) {
            return null;
        }
    }

    // helper method to open database
    private Database openDatabase(String dbName) throws CouchbaseLiteException {
        return openDatabase(dbName, true);
    }

    private Database openDatabase(String dbName, boolean countCheck) throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        builder.setDirectory(dir.getAbsolutePath());
        Database db = new Database(dbName, builder.build());
        assertEquals(dbName, db.getName());
        assertTrue(db.getPath().getAbsolutePath().endsWith(".cblite2"));
        if (countCheck)
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
        assertEquals(value, ((Number) doc.getValue("key")).intValue());
        doc = null;
    }

    // helper method to purge doc and verify doc.
    void purgeDocAndVerify(Document doc) throws CouchbaseLiteException {
        String docID = doc.getId();
        db.purge(doc);
        assertNull(db.getDocument(docID));
    }

    // helper method to save n number of docs
    List<String> createDocs(int n) throws CouchbaseLiteException {
        List<String> docs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            MutableDocument doc = createMutableDocument(String.format(Locale.US, "doc_%03d", i));
            doc.setValue("key", i);
            Document savedDoc = save(doc);
            docs.add(savedDoc.getId());
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
        Log.i(TAG, "setUp");
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown");
        super.tearDown();
    }

    //---------------------------------------------
    //  DatabaseConfiguration
    //---------------------------------------------
    @Test
    public void testCreateConfiguration() {
        // Default:
        DatabaseConfiguration.Builder builder1 = new DatabaseConfiguration.Builder(this.context);
        builder1.setDirectory("/tmp");
        DatabaseConfiguration config1 = builder1.build();
        assertNotNull(config1.getDirectory());
        assertTrue(config1.getDirectory().length() > 0);
        assertNotNull(config1.getConflictResolver());
        assertNull(config1.getEncryptionKey());

        // Default + Copy
        DatabaseConfiguration config1a = config1.copy();
        assertNotNull(config1a.getDirectory());
        assertTrue(config1a.getDirectory().length() > 0);
        assertNotNull(config1a.getConflictResolver());
        assertNull(config1a.getEncryptionKey());

        // Custom
        EncryptionKey key = new EncryptionKey("key");
        DummyResolver resolver = new DummyResolver();
        DatabaseConfiguration.Builder builder2 = new DatabaseConfiguration.Builder(this.context);
        builder2.setDirectory("/tmp/mydb");
        builder2.setConflictResolver(resolver);
        builder2.setEncryptionKey(key);
        DatabaseConfiguration config2 = builder2.build();
        assertEquals("/tmp/mydb", config2.getDirectory());
        assertEquals(resolver, config2.getConflictResolver());
        assertEquals(key, config2.getEncryptionKey());

        // Custom + Copy
        DatabaseConfiguration config2a = config2.copy();
        assertEquals("/tmp/mydb", config2a.getDirectory());
        assertEquals(resolver, config2a.getConflictResolver());
        assertEquals(key, config2a.getEncryptionKey());
    }

    @Test
    public void testGetSetConfiguration() throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        builder.setDirectory(this.db.getConfig().getDirectory());
        DatabaseConfiguration config = builder.build();
        Database db = new Database("db", config);
        try {
            assertNotNull(db.getConfig());
            assertEquals(db.getConfig(), config);
            assertEquals(db.getConfig().getDirectory(), config.getDirectory());
            assertEquals(db.getConfig().getConflictResolver(), config.getConflictResolver());
            assertEquals(db.getConfig().getEncryptionKey(), config.getEncryptionKey());
        } finally {
            db.close();
        }
    }

    @Test
    public void testConfigurationIsCopiedWhenGetSet() throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        builder.setDirectory(this.db.getConfig().getDirectory());
        DatabaseConfiguration config = builder.build();
        Database db = new Database("db", config);
        try {
            builder.setConflictResolver(new DummyResolver());
            config = builder.build();
            assertNotNull(db.getConfig());
            assertTrue(db.getConfig() != config);
            assertTrue(db.getConfig().getConflictResolver() != config.getConflictResolver());
        } finally {
            db.close();
        }
    }

    @Test
    public void testDatabaseConfigurationWithAndroidContect() throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        DatabaseConfiguration config = builder.build();
        assertEquals(config.getDirectory(), context.getFilesDir().getAbsolutePath());
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
    public void testCreateWithDefaultConfiguration() throws CouchbaseLiteException {

        Database db = new Database("db", new DatabaseConfiguration.Builder(this.context).build());
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
        DatabaseConfiguration config = new DatabaseConfiguration.Builder(this.context)
                .setDirectory(dir.getAbsolutePath())
                .build();
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

        verifyGetDocument(otherDB, docID);

        otherDB.close();
    }

    @Test
    public void testGetExistingDocWithIDInBatch() throws CouchbaseLiteException {
        final int NUM_DOCS = 10;

        // Save 10 docs:
        createDocs(NUM_DOCS);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                validateDocs(NUM_DOCS);
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

    // base test method
    private void testSaveNewDocWithID(String docID) throws CouchbaseLiteException {
        // store doc
        generateDocument(docID);

        assertEquals(1, db.getCount());

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
    public void testSaveAndGetMultipleDocs() throws CouchbaseLiteException {
        {
            final int NUM_DOCS = 10;//1000;
            for (int i = 0; i < NUM_DOCS; i++) {
                MutableDocument doc = createMutableDocument(String.format(Locale.US, "doc_%03d", i));
                doc.setValue("key", i);
                save(doc);
            }
            assertEquals(NUM_DOCS, db.getCount());
            validateDocs(NUM_DOCS);
        }
        System.gc();
    }

    @Test
    public void testSaveDoc() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        MutableDocument doc = generateDocument(docID).toMutable();

        // update doc
        doc.setValue("key", 2);
        save(doc);

        assertEquals(1, db.getCount());

        // validate document by getDocument
        verifyGetDocument(docID, 2);
    }

    @Test
    public void testSaveDocInDifferentDBInstance() throws CouchbaseLiteException {
        // Store doc
        String docID = "doc1";
        MutableDocument doc = generateDocument(docID).toMutable();

        // Create db with default
        Database otherDB = openDatabase(db.getName(), false);
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(1, otherDB.getCount());

        // Update doc & store it into different instance
        doc.setValue("key", 2);
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
        MutableDocument doc = generateDocument(docID).toMutable();

        // Create db with default
        Database otherDB = openDatabase("otherDB");
        assertNotNull(otherDB);
        assertTrue(otherDB != db);
        assertEquals(0, otherDB.getCount());

        // Update doc & store it into different instance
        doc.setValue("key", 2);
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
        MutableDocument doc = generateDocument(docID).toMutable();
        save(doc);
        assertEquals(docID, doc.getId());
        assertEquals(1, db.getCount());
    }

    @Test
    public void testSaveInBatch() throws CouchbaseLiteException {
        final int NUM_DOCS = 10;

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                try {
                    createDocs(NUM_DOCS);
                } catch (CouchbaseLiteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        assertEquals(NUM_DOCS, db.getCount());
        validateDocs(NUM_DOCS);
    }

    @Test
    public void testSaveDocToClosedDB() throws CouchbaseLiteException {
        db.close();

        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("key", 1);

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

        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("key", 1);

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
        MutableDocument doc = createMutableDocument("doc1");
        doc.setValue("key", 1);
        try {
            db.delete(doc);
            fail();
        } catch (IllegalArgumentException uoe) {
            // expected
        } catch (CouchbaseLiteException e) {
            fail();
        }
    }

    @Test
    public void testDeleteDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = generateDocument(docID);
        db.delete(doc);
        assertEquals(0, db.getCount());
        assertNull(db.getDocument(docID));
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
    public void testDeleteDocInBatch() throws CouchbaseLiteException {
        final int NUM_DOCS = 10;

        // Save 10 docs:
        createDocs(NUM_DOCS);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < NUM_DOCS; i++) {
                    String docID = String.format(Locale.US, "doc_%03d", i);
                    Document doc = db.getDocument(docID);
                    try {
                        db.delete(doc);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                    assertNull(db.getDocument(docID));
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
        MutableDocument doc = createMutableDocument("doc1");
        try {
            db.purge(doc);
            fail();
        } catch (IllegalArgumentException uoe) {
            // expected
        } catch (CouchbaseLiteException e) {
            fail();
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
        final int NUM_DOCS = 10;
        // Save 10 docs:
        createDocs(NUM_DOCS);

        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < NUM_DOCS; i++) {
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
        assertEquals(1, ((Number) doc.getValue("key")).intValue());
        MutableDocument updateDoc = doc.toMutable();
        updateDoc.setValue("key", 2);
        updateDoc.setValue("key1", "value");
    }

    @Test
    public void testCloseThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        MutableDocument doc = generateDocument("doc1").toMutable();
        doc.setValue("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
        save(doc);

        // Close db:
        db.close();

        // content should be accessible & modifiable without error
        assertTrue(doc.getValue("blob") instanceof Blob);
        Blob blob = doc.getBlob("blob");
        assertEquals(8, blob.length());
        // NOTE: content is still in memory for this
        assertNotNull(blob.getContent());
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
        MutableDocument doc = generateDocument(docID).toMutable();

        // Delete db:
        deleteDatabase(db);

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getValue("key")).intValue());
        doc.setValue("key", 2);
        doc.setValue("key1", "value");
    }

    @Test
    public void testDeleteThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        String docID = "doc1";
        MutableDocument doc = generateDocument(docID).toMutable();
        doc.setValue("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
        save(doc);

        // Delete db:
        deleteDatabase(db);

        // content should be accessible & modifiable without error
        Object obj = doc.getValue("blob");
        assertNotNull(obj);
        assertTrue(obj instanceof Blob);
        Blob blob = (Blob) obj;
        assertEquals(8, blob.length());
        // NOTE content still exists in memory for this case.
        assertNotNull(blob.getContent());
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
    public void testDeleteWithDefaultDirDB() throws CouchbaseLiteException {
        String dbName = "db";
        Database database = open(dbName);
        File path = database.getPath();
        assertNotNull(path);
        assertTrue(path.exists());
        // close db before delete
        database.close();

        // Java/Android does not allow null as directory parameter
        try {
            Database.delete(dbName, null);
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }
        assertTrue(path.exists());
    }

    @Test
    public void testDeleteOpeningDBWithDefaultDir() throws CouchbaseLiteException {
        String dbName = "db";

        // create db with custom directory
        Database db = openDatabase(dbName);
        try {
            File path = db.getPath();
            assertNotNull(path);
            assertTrue(path.exists());

            // Java/Android does not allow null as directory parameter
            try {
                Database.delete(dbName, null);
                fail();
            } catch (IllegalArgumentException ex) {
                // ok
            } finally {
                ;
            }
        } finally {
            db.close();
        }
    }

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

    @Test
    public void testDatabaseExistsWithDefaultDir() {
        // NOTE: Android/Java does not allow to use null as directory parameters
        //       This test is not valid for Android Java. Will keep this test
        //       for unit test consistency with other platforms

        try {
            Database.exists("db", null);
            fail();
        } catch (IllegalArgumentException ex) {
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

    @Test
    public void testCompact() throws CouchbaseLiteException {
        final int NUM_DOCS = 20;
        final int NUM_UPDATES = 25;
        final List<String> docIDs = createDocs(NUM_DOCS);

        // Update each doc 25 times:
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (String docID : docIDs) {
                    Document savedDoc = db.getDocument(docID);
                    for (int i = 0; i < NUM_UPDATES; i++) {
                        MutableDocument doc = savedDoc.toMutable();
                        doc.setValue("number", i);
                        try {
                            savedDoc = save(doc);
                        } catch (CouchbaseLiteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        });

        // Add each doc with a blob object:
        for (String docID : docIDs) {
            Document savedDoc = db.getDocument(docID);
            MutableDocument doc = savedDoc.toMutable();
            doc.setValue("blob", new Blob("text/plain", doc.getId().getBytes()));
            savedDoc = save(doc);
        }

        assertEquals(NUM_DOCS, db.getCount());

        File attsDir = new File(db.getPath(), "Attachments");
        assertTrue(attsDir.exists());
        assertTrue(attsDir.isDirectory());
        File[] atts = attsDir.listFiles();
        assertEquals(NUM_DOCS, atts.length);

        // Compact:
        db.compact();

        // Delete all docs:
        for (String docID : docIDs) {
            Document savedDoc = db.getDocument(docID);
            db.delete(savedDoc);
            assertNull(db.getDocument(docID));
        }

        // Compact:
        db.compact();

        atts = attsDir.listFiles();
        assertEquals(0, atts.length);
    }

    @Test
    public void testOverwriteDocWithNewDocInstgance() throws CouchbaseLiteException {
        // REF: https://github.com/couchbase/couchbase-lite-android/issues/1231

        MutableDocument mDoc1 = new MutableDocument("abc");
        mDoc1.setValue("someKey", "someVar");
        Document doc1 = db.save(mDoc1);

        // This cause conflict, DefaultConflictResolver should be applied.
        MutableDocument mDoc2 = new MutableDocument("abc");
        mDoc2.setValue("someKey", "newVar");
        Document doc2 = db.save(mDoc2);

        // NOTE: Both doc1 and doc2 are generation 1. Higher revision one should win
        assertEquals(1, db.getCount());
        Document doc = db.getDocument("abc");
        assertNotNull(doc);
        if (doc1.getRevID().compareTo(doc2.getRevID()) > 0)
            assertEquals("someVar", doc.getString("someKey"));
        else
            assertEquals("newVar", doc.getString("someKey"));
    }

    @Test
    public void testCopy() throws CouchbaseLiteException {
        // NOTE: On Stack emulator ARM v7a Android API 16, testCopy() test fails with a directory
        //       operation in the native library. This test can pass with real ARM device with
        //       API 17. Also it can pass with x86 stack emulator with API 16.
        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.JELLY_BEAN
                && Build.FINGERPRINT.startsWith("generic")
                && "armv7l".equals(System.getProperty("os.arch"))) {
            Log.w(TAG, "testCopy() is skipped.");
            return;
        }

        // TODO: crash with 2048
        final int NUM_DOCS = 10;

        for (int i = 0; i < NUM_DOCS; i++) {
            String docID = String.format(Locale.US, "doc_%03d", i);
            MutableDocument doc = createMutableDocument(docID);
            doc.setValue("name", docID);
            byte[] data = docID.getBytes();
            Blob blob = new Blob("text/plain", data);
            doc.setValue("data", blob);
            save(doc);
        }

        String dbName = "nudb";
        DatabaseConfiguration config = db.getConfig();
        File dir = new File(config.getDirectory());

        // Make sure no an existing database at the new location:
        if (Database.exists(dbName, dir))
            Database.delete(dbName, dir);

        // Copy:
        Database.copy(db.getPath(), dbName, config);

        // Verify:
        assertTrue(Database.exists(dbName, dir));

        Database nudb = new Database(dbName, config);
        assertNotNull(nudb);
        assertEquals(NUM_DOCS, nudb.getCount());

        SelectResult S_DOCID = SelectResult.expression(Meta.id);
        Query query = Query.select(S_DOCID).from(DataSource.database(nudb));
        ResultSet rs = query.execute();
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

    @Test
    public void testCreateIndex() throws CouchbaseLiteException {
        assertEquals(0, db.getIndexes().size());

        // Create value index:
        ValueIndexItem fNameItem = ValueIndexItem.property("firstName");
        ValueIndexItem lNameItem = ValueIndexItem.property("lastName");

        Index index1 = Index.valueIndex(fNameItem, lNameItem);
        db.createIndex("index1", index1);
        assertEquals(1, db.getIndexes().size());

        // Create FTS index:
        FullTextIndexItem detailItem = FullTextIndexItem.property("detail");
        Index index2 = Index.fullTextIndex(detailItem);
        db.createIndex("index2", index2);
        assertEquals(2, db.getIndexes().size());

        FullTextIndexItem detailItem2 = FullTextIndexItem.property("es-detail");
        Index index3 = Index.fullTextIndex(detailItem2).ignoreAccents(true).setLocale("es");
        db.createIndex("index3", index3);
        assertEquals(3, db.getIndexes().size());
        assertEquals(Arrays.asList("index1", "index2", "index3"), db.getIndexes());

        Log.e(TAG, "db.getIndexes() -> " + db.getIndexes());
    }

    @Test
    public void testCreateSameIndexTwice() throws CouchbaseLiteException {
        // Create index with first name:
        ValueIndexItem indexItem = ValueIndexItem.property("firstName");
        Index index = Index.valueIndex(indexItem);
        db.createIndex("myindex", index);

        // Call create index again:
        db.createIndex("myindex", index);

        assertEquals(1, db.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), db.getIndexes());
    }

    @Test
    public void testCreateSameNameIndexes() throws CouchbaseLiteException {
        ValueIndexItem fNameItem = ValueIndexItem.property("firstName");
        ValueIndexItem lNameItem = ValueIndexItem.property("lastName");
        FullTextIndexItem detailItem = FullTextIndexItem.property("detail");

        // Create value index with first name:
        Index fNameindex = Index.valueIndex(fNameItem);
        db.createIndex("myindex", fNameindex);

        // Create value index with last name:
        Index lNameindex = Index.valueIndex(lNameItem);
        db.createIndex("myindex", lNameindex);

        // Check:
        assertEquals(1, db.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), db.getIndexes());

        // Create FTS index:
        Index detailIndex = Index.fullTextIndex(detailItem);
        db.createIndex("myindex", lNameindex);

        // Check:
        assertEquals(1, db.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), db.getIndexes());
    }

    @Test
    public void testDeleteIndex() throws CouchbaseLiteException {
        testCreateIndex();

        // Delete indexes:
        db.deleteIndex("index1");
        assertEquals(2, db.getIndexes().size());
        assertEquals(Arrays.asList("index2", "index3"), db.getIndexes());

        db.deleteIndex("index2");
        assertEquals(1, db.getIndexes().size());
        assertEquals(Arrays.asList("index3"), db.getIndexes());

        db.deleteIndex("index3");
        assertEquals(0, db.getIndexes().size());
        assertEquals(Arrays.asList(), db.getIndexes());

        // Delete non existing index:
        db.deleteIndex("dummy");

        // Delete deleted indexes:
        db.deleteIndex("index1");
        db.deleteIndex("index2");
        db.deleteIndex("index3");

    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1416
    @Test
    public void testDeleteAndOpenDB() throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        builder.setDirectory(dir.toString());
        DatabaseConfiguration config = builder.build();

        // open "application" database
        final Database database1 = new Database("application", config);

        // delete "application" database
        database1.delete();

        // open "application" database again
        final Database database2 = new Database("application", config);

        // inserting documents
        database2.inBatch(new Runnable() {
            @Override
            public void run() {
                // just create 100 documents
                for (int i = 0; i < 100; i++) {
                    MutableDocument doc = new MutableDocument();

                    // each doc has 10 items
                    doc.setInt("index", i);
                    for (int j = 0; j < 10; j++)
                        doc.setInt("item_" + j, j);

                    try {
                        database2.save(doc);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Failed in storing document " + e.getMessage(), e);
                        fail();
                    }
                }
            }
        });

        // close db again
        database2.close();
    }
}
