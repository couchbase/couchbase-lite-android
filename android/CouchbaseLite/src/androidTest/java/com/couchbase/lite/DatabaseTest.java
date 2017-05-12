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

import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorBusy;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorTransactionNotClosed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseTest extends BaseTest {
    private static final String TAG = DatabaseTest.class.getName();

    //---------------------------------------------
    //  Helper methods
    //---------------------------------------------

    // helper method to open database
    private Database openDatabase(String dbName) {
        DatabaseConfiguration options = new DatabaseConfiguration();
        options.setDirectory(dir);
        Database db = new Database(dbName, options);
        assertEquals(dbName, db.getName());
        assertTrue(db.getPath().getAbsolutePath().endsWith(".cblite2"));
        assertEquals(0, db.documentCount());
        return db;
    }

    // helper method to delete database
    void deleteDatabase(Database db) {
        File path = db.getPath();
        assertTrue(path.exists());
        db.delete();
        assertFalse(path.exists());
    }

    // helper method to save document
    Document generateDocument(String docID) {
        Document doc = new Document(docID);
        doc.set("key", 1);
        db.save(doc);
        assertEquals(1, db.documentCount());
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
        assertEquals(value, doc.getObject("key"));
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
    //  Create Database
    //---------------------------------------------

    @Test
    public void testCreate() {
        // create db with default options

        Database db = openDatabase("db");
        assertNotNull(db);
        assertEquals(0, db.documentCount());

        // delete database
        deleteDatabase(db);
    }

    @Test
    public void testCreateWithDefaultOption() {
        try {
            Database db = new Database("db", new DatabaseConfiguration());
            fail();
        } catch (UnsupportedOperationException e) {
            // NOTE: CBL Android's Database constructor does not work without specified directory.
        }
    }

    @Test
    public void testCreateWithSpecialCharacterDBNames() {
        Database db = openDatabase("`~@#$%^&*()_+{}|\\][=-/.,<>?\":;'");
        assertNotNull(db);
        assertEquals(0, db.documentCount());

        // delete database
        deleteDatabase(db);
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
        File dir = new File(context.getFilesDir(), "CouchbaseLite");
        try {
            Database.delete("db", dir);
        } catch (Exception ex) {
        }

        assertFalse(Database.exists("db", dir));

        // create db with custom directory
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(dir);
        Database db = new Database("db", config);
        assertNotNull(db);
        assertEquals("db", db.getName());
        assertTrue(db.getPath().getAbsolutePath().endsWith(".cblite2"));
        assertTrue(db.getPath().getAbsolutePath().indexOf(dir.getPath()) != -1);
        assertTrue(Database.exists("db", dir));
        assertEquals(0, db.documentCount());

        // delete database
        deleteDatabase(db);
    }

    @Test
    public void testCreateWithCustomConflictResolver() {
        // TODO: DatabaseConfiguration.conflictResolver is not implemented yet.
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

    // TODO
    // @Test
    public void testGetExistingDocWithIDFromDifferentDBInstance() {
        // store doc
        String docID = "doc1";
        generateDocument(docID);

        // open db with same db name and default option
        Database otherDB = openDatabase(db.getName());
        assertNotNull(otherDB);
        assertTrue(db != otherDB);

        // get doc from other DB.
        assertEquals(1, otherDB.documentCount());

        verifyGetDocument(otherDB, docID);

        otherDB.close();
    }

    @Test
    public void testGetExistingDocWithIDInBatch() {
        // TODO
    }

    @Test
    public void testGetDocFromClosedDB() {
        // TODO
    }

    @Test
    public void testGetDocFromDeletedDB() {
        // TODO
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------
    private void testSaveNewDocWithID(String docID) {
        // store doc
        generateDocument(docID);

        assertEquals(1, db.documentCount());

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
        db.save(doc);

        assertEquals(1, db.documentCount());

        // validate document by getDocument
        verifyGetDocument(docID, 2);
    }

    @Test
    public void testSaveDocInDifferentDBInstance() {
        // TODO
    }

    @Test
    public void testSaveDocInDifferentDB() {
        // TODO
    }

    @Test
    public void testSaveSameDocTwice() {
        // TODO
    }

    @Test
    public void testSaveInBatch() {
        // TODO
    }

    @Test
    public void testSaveDocToClosedDB() {
        // TODO
    }

    @Test
    public void testSaveDocToDeletedDB() {
        // TODO
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    public void testDeletePreSaveDoc() {
        Document doc = new Document("doc1");
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
        assertEquals(0, db.documentCount());

        assertEquals(docID, doc.getId());
        assertTrue(doc.isDeleted());
        assertEquals(2, doc.getSequence());
        assertTrue(doc.getObject("key") == null);
    }

    @Test
    public void testDeleteDocInDifferentDBInstance() {
        // TODO
    }

    @Test
    public void testDeleteDocInDifferentDB() {
        // TODO
    }

    @Test
    public void testDeleteSameDocTwice() {
        // TODO
    }

    @Test
    public void testDeleteDocInBatch() {
        // TODO
    }

    @Test
    public void testDeleteDocOnClosedDB() {
        // TODO
    }

    @Test
    public void testDeleteDocOnDeletedDB() {
        // TODO
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    public void testPurgePreSaveDoc() {
        Document doc = new Document("doc1");
        try {
            db.purge(doc);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CBLErrorDomain, e.getDomain());
            assertEquals(Status.NotFound, e.getCode());
        }
        assertEquals(0, db.documentCount());
    }

    @Test
    public void testPurgeDoc() {
        String docID = "doc1";
        Document doc = generateDocument(docID);

        // Purge Doc
        // Note: After purge: sequence -> 2
        purgeDocAndVerify(doc);
        assertEquals(0, db.documentCount());

        // Save to check sequence number -> 3 (should it be 2 or 3?)
        db.save(doc);
        // TODO
        // assertEquals(3, doc.getSequence());
    }

    @Test
    public void testPurgeDocInDifferentDBInstance() {
        // TODO
    }

    @Test
    public void testPurgeDocInDifferentDB() {
        // TODO
    }

    @Test
    public void testPurgeSameDocTwice() {
        // TODO
    }

    @Test
    public void testPurgeDocInBatch() {
        // TODO
    }

    @Test
    public void testPurgeDocOnClosedDB() {
        // TODO
    }

    @Test
    public void testPurgeDocOnDeletedDB() {
        // TODO
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
        //TODO
    }

    @Test
    public void testCloseThenAccessBlob() {
        //TODO
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
        deleteDatabase(db);
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
        db.delete();
        assertFalse(path.exists());
    }

    @Test
    public void testDeleteThenAccessDoc() {
        //TODO
    }

    @Test
    public void testDeleteThenAccessBlob() {
        //TODO
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

    // TODO:
    // @Test
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
    public void testDeleteWithDefaultDirDB() {
        //TODO
    }

    @Test
    public void testDeleteOpeningDBWithDefaultDir() {
        //TODO
    }

    @Test
    public void testDeleteByStaticMethod() {
        // create db with custom directory
        Database db = openDatabase("db");
        File path = db.getPath();

        // close db before delete
        db.close();

        Database.delete("db", dir);
        assertFalse(path.exists());
    }

    @Test
    public void testDeleteOpeningDBByStaticMethod() {
        // create db with custom directory
        Database db = openDatabase("db");

        try {
            Database.delete("db", dir);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(LiteCoreDomain, e.getDomain());
            assertEquals(kC4ErrorBusy, e.getCode()); // 24
        }
    }

    @Test
    public void testDeleteNonExistingDBWithDefaultDir() {
        // Expectation: No operation
        Database.delete("notexistdb", null);
    }

    @Test
    public void testDeleteNonExistingDB() {
        // Expectation: No operation
        Database.delete("notexistdb", dir);
    }

    //---------------------------------------------
    //  Database Existing
    //---------------------------------------------

    @Test
    public void testDatabaseExistsWithDefaultDir() {
        //TODO
    }

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
        assertFalse(Database.exists("nonexist", null));
    }

    @Test
    public void testDatabaseExistsAgainstNonExistDB() {
        assertFalse(Database.exists("nonexist", dir));
    }
}
