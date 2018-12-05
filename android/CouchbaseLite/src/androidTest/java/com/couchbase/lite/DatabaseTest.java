//
// DatabaseTest.java
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

import android.os.Build;

import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseTest extends BaseTest {
    final static String kDatabaseTestBlob = "i'm blob";

    //---------------------------------------------
    //  Helper methods
    //---------------------------------------------

    // helper method to open database
    private Database openDatabase(String dbName) throws CouchbaseLiteException {
        return openDatabase(dbName, true);
    }

    private Database openDatabase(String dbName, boolean countCheck) throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(getDir().getAbsolutePath());
        Database db = new Database(dbName, config);
        assertEquals(dbName, db.getName());
        assertTrue(new File(db.getPath()).getAbsolutePath().endsWith(".cblite2"));
        if (countCheck)
            assertEquals(0, db.getCount());
        return db;
    }

    // helper method to delete database
    void deleteDatabase(Database db) throws CouchbaseLiteException {
        File path = db.getPath() != null ? new File(db.getPath()) : null;
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
        DatabaseConfiguration config1 = new DatabaseConfiguration(this.context);
        config1.setDirectory("/tmp");
        assertNotNull(config1.getDirectory());
        assertTrue(config1.getDirectory().length() > 0);

        // Custom
        DatabaseConfiguration config2 = new DatabaseConfiguration(this.context);
        config2.setDirectory("/tmp/mydb");
        assertEquals("/tmp/mydb", config2.getDirectory());
    }

    @Test
    public void testGetSetConfiguration() throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(this.db.getConfig().getDirectory());
        Database db = new Database("db", config);
        try {
            assertNotNull(db.getConfig());
            assertFalse(db.getConfig() == config);
            assertEquals(db.getConfig().getDirectory(), config.getDirectory());
        } finally {
            db.delete();
        }
    }

    @Test
    public void testConfigurationIsCopiedWhenGetSet() throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(this.db.getConfig().getDirectory());
        Database db = new Database("db", config);
        try {
            assertNotNull(db.getConfig());
            assertTrue(db.getConfig() != config);
        } finally {
            db.delete();
        }
    }

    @Test
    public void testDatabaseConfigurationWithAndroidContect() throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        assertEquals(config.getDirectory(), context.getFilesDir().getAbsolutePath());
        Database db = new Database("db", config);
        try {
            String expectedPath = context.getFilesDir().getAbsolutePath();
            assertTrue(new File(db.getPath()).getAbsolutePath().contains(expectedPath));
        } finally {
            db.delete();
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

        Database db = new Database("db", new DatabaseConfiguration(this.context));
        try {
        } finally {
            // delete database
            deleteDatabase(db);
        }
    }

    @Test
    public void testCreateWithSpecialCharacterDBNames() throws CouchbaseLiteException {
        Database db = openDatabase("`~@#$%^&*()_+{}|\\][=-/.,<>?\":;'ABCDEabcde");
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
        DatabaseConfiguration config = new DatabaseConfiguration(this.context)
                .setDirectory(dir.getAbsolutePath());
        Database db = new Database(dbName, config);
        try {
            assertNotNull(db);
            assertEquals(dbName, db.getName());
            assertTrue(new File(db.getPath()).getAbsolutePath().endsWith(".cblite2"));
            assertTrue(new File(db.getPath()).getAbsolutePath().indexOf(dir.getPath()) != -1);
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
        Database.getLog().getConsole().setLevel(LogLevel.VERBOSE);

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
        } catch (IllegalStateException e) {
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
        } catch (IllegalStateException e) {
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
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorInvalidParameter, e.getCode());
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
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorInvalidParameter, e.getCode());
        } finally {
            // delete otherDb
            deleteDatabase(otherDB);
            deleteDatabase("otherDB");
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
        } catch (IllegalStateException e) {
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
        } catch (IllegalStateException e) {
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
        } catch (CouchbaseLiteException e) {
            if (e.getCode() == CBLError.Code.CBLErrorNotFound)
                ;// expected
            else
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
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorInvalidParameter, e.getCode());
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
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorInvalidParameter, e.getCode());
        } finally {
            // close otherDb
            deleteDatabase(otherDB);
            deleteDatabase("otherDB");
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
        } catch (IllegalStateException e) {
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
        } catch (IllegalStateException e) {
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
        } catch (CouchbaseLiteException e) {
            if (e.getCode() == CBLError.Code.CBLErrorNotFound)
                ;// expected
            else
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
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorInvalidParameter, e.getCode());
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
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorInvalidParameter, e.getCode());
        } finally {
            // close otherDb
            deleteDatabase(otherDB);
            deleteDatabase("otherDB");
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
        } catch (IllegalStateException e) {
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
        } catch (IllegalStateException e) {
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
        MutableDocument mDoc = new MutableDocument(docID);
        mDoc.setInt("key", 1);
        MutableDictionary mDict = new MutableDictionary(); // nested dictionary
        mDict.setString("hello", "world");
        mDoc.setDictionary("dict", mDict);
        Document doc = save(mDoc);

        // Close db:
        db.close();

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.getId());
        assertEquals(1, ((Number) doc.getValue("key")).intValue());
        Dictionary dict = doc.getDictionary("dict");
        assertNotNull(dict);
        assertEquals("world", dict.getString("hello"));
        MutableDocument updateDoc = doc.toMutable();
        updateDoc.setValue("key", 2);
        updateDoc.setValue("key1", "value");
    }

    @Test
    public void testCloseThenAccessBlob() throws CouchbaseLiteException {
        // Store doc with blob:
        MutableDocument mDoc = generateDocument("doc1").toMutable();
        mDoc.setValue("blob", new Blob("text/plain", kDatabaseTestBlob.getBytes()));
        Document doc = save(mDoc);

        // Close db:
        db.close();

        // content should be accessible & modifiable without error
        assertTrue(doc.getValue("blob") instanceof Blob);
        Blob blob = doc.getBlob("blob");
        assertEquals(8, blob.length());
        try {
            blob.getContent();
            fail();
        } catch (IllegalStateException e) {
            ; // expected
        }
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
                    assertEquals(CBLErrorDomain, e.getDomain());
                    assertEquals(CBLErrorTransactionNotClosed, e.getCode()); // 26
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
        } catch (IllegalStateException e) {
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
        File path = new File(db.getPath());
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
                    assertEquals(CBLErrorDomain, e.getDomain());
                    assertEquals(CBLErrorTransactionNotClosed, e.getCode()); // 26
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
                assertEquals(CBLErrorDomain, e.getDomain());
                assertEquals(CBLErrorBusy, e.getCode()); // 24
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
        try {
            Database database = open(dbName);
            File path = new File(database.getPath());
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
        } finally {
            Database.delete(dbName, getDir());
        }
    }

    @Test
    public void testDeleteOpeningDBWithDefaultDir() throws CouchbaseLiteException {
        String dbName = "db";

        // create db with custom directory
        Database db = openDatabase(dbName);
        try {
            File path = new File(db.getPath());
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
            db.delete();
        }
    }

    @Test
    public void testDeleteByStaticMethod() throws CouchbaseLiteException {
        String dbName = "db";

        // create db with custom directory
        Database db = openDatabase(dbName);
        File path = new File(db.getPath());

        // close db before delete
        db.close();

        Database.delete(dbName, getDir());
        assertFalse(path.exists());
    }

    @Test
    public void testDeleteOpeningDBByStaticMethod() throws CouchbaseLiteException {
        Database db = openDatabase("db");
        try {
            try {
                Database.delete("db", getDir());
                fail();
            } catch (CouchbaseLiteException e) {
                assertEquals(CBLErrorDomain, e.getDomain());
                assertEquals(CBLErrorBusy, e.getCode()); // 24
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
            Database.delete("notexistdb", getDir());
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(CBLErrorDomain, e.getDomain());
            assertEquals(CBLErrorNotFound, e.getCode());
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
        assertFalse(Database.exists("db", getDir()));

        // create db with custom directory
        Database db = openDatabase("db");
        File path = new File(db.getPath());

        assertTrue(Database.exists("db", getDir()));

        db.close();

        assertTrue(Database.exists("db", getDir()));

        Database.delete("db", getDir());
        assertFalse(path.exists());

        assertFalse(Database.exists("db", getDir()));
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
        assertFalse(Database.exists("nonexist", getDir()));
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
        Document doc1 = save(mDoc1);

        // This cause conflict, DefaultConflictResolver should be applied.
        MutableDocument mDoc2 = new MutableDocument("abc");
        mDoc2.setValue("someKey", "newVar");
        Document doc2 = save(mDoc2);

        // NOTE: Both doc1 and doc2 are generation 1. Higher revision one should win
        assertEquals(1, db.getCount());
        Document doc = db.getDocument("abc");
        assertNotNull(doc);
        // NOTE doc1 -> theirs, doc2 -> mine
        if (doc2.getRevID().compareTo(doc1.getRevID()) > 0)
            // mine -> doc 2 win
            assertEquals("newVar", doc.getString("someKey"));
        else
            // their -> doc 1 win
            assertEquals("someVar", doc.getString("someKey"));
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
        Database.copy(new File(db.getPath()), dbName, config);

        // Verify:
        assertTrue(Database.exists(dbName, dir));

        Database nudb = new Database(dbName, config);
        assertNotNull(nudb);
        assertEquals(NUM_DOCS, nudb.getCount());

        SelectResult S_DOCID = SelectResult.expression(Meta.id);
        Query query = QueryBuilder.select(S_DOCID).from(DataSource.database(nudb));
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

        Index index1 = IndexBuilder.valueIndex(fNameItem, lNameItem);
        db.createIndex("index1", index1);
        assertEquals(1, db.getIndexes().size());

        // Create FTS index:
        FullTextIndexItem detailItem = FullTextIndexItem.property("detail");
        Index index2 = IndexBuilder.fullTextIndex(detailItem);
        db.createIndex("index2", index2);
        assertEquals(2, db.getIndexes().size());

        FullTextIndexItem detailItem2 = FullTextIndexItem.property("es-detail");
        FullTextIndex index3 = IndexBuilder.fullTextIndex(detailItem2).ignoreAccents(true).setLanguage("es");
        db.createIndex("index3", index3);
        assertEquals(3, db.getIndexes().size());
        assertEquals(Arrays.asList("index1", "index2", "index3"), db.getIndexes());

        // Create value index with expression() instead of property()
        Expression fNameExpr = Expression.property("firstName");
        Expression lNameExpr = Expression.property("lastName");
        ValueIndexItem fNameItem2 = ValueIndexItem.expression(fNameExpr);
        ValueIndexItem lNameItem2 = ValueIndexItem.expression(lNameExpr);
        Index index4 = IndexBuilder.valueIndex(fNameItem2, lNameItem2);
        db.createIndex("index4", index4);
        assertEquals(4, db.getIndexes().size());

        Log.i(TAG, "db.getIndexes() -> " + db.getIndexes());
    }

    @Test
    public void testCreateSameIndexTwice() throws CouchbaseLiteException {
        // Create index with first name:
        ValueIndexItem indexItem = ValueIndexItem.property("firstName");
        Index index = IndexBuilder.valueIndex(indexItem);
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
        Index fNameindex = IndexBuilder.valueIndex(fNameItem);
        db.createIndex("myindex", fNameindex);

        // Create value index with last name:
        ValueIndex lNameindex = IndexBuilder.valueIndex(lNameItem);
        db.createIndex("myindex", lNameindex);

        // Check:
        assertEquals(1, db.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), db.getIndexes());

        // Create FTS index:
        Index detailIndex = IndexBuilder.fullTextIndex(detailItem);
        db.createIndex("myindex", detailIndex);

        // Check:
        assertEquals(1, db.getIndexes().size());
        assertEquals(Arrays.asList("myindex"), db.getIndexes());
    }

    @Test
    public void testDeleteIndex() throws CouchbaseLiteException {
        testCreateIndex();

        // Delete indexes:

        db.deleteIndex("index4");
        assertEquals(3, db.getIndexes().size());
        assertEquals(Arrays.asList("index1", "index2", "index3"), db.getIndexes());

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
        db.deleteIndex("index4");
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1416
    @Test
    public void testDeleteAndOpenDB() throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(getDir().toString());

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

    @Test
    public void testSaveAndUpdateMutableDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        db.save(doc);

        // Update:
        doc.setString("lastName", "Tiger");
        db.save(doc);

        // Update:
        doc.setLong("age", 20L); // Int vs Long assertEquals can not ignore diff.
        db.save(doc);


        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Daniel");
        expected.put("lastName", "Tiger");
        expected.put("age", 20L);
        assertEquals(expected, doc.toMap());
        assertEquals(3, doc.getSequence());

        Document savedDoc = db.getDocument(doc.getId());
        assertEquals(expected, savedDoc.toMap());
        assertEquals(3, savedDoc.getSequence());
    }

    @Test
    public void testSaveDocWithConflict() throws CouchbaseLiteException {
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    void testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl cc) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        db.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        MutableDocument doc1a = db.getDocument("doc1").toMutable();
        MutableDocument doc1b = db.getDocument("doc1").toMutable();

        // Modify doc1a:
        doc1a.setString("firstName", "Scott");
        db.save(doc1a);
        doc1a.setString("nickName", "Scotty");
        db.save(doc1a);

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        expected.put("nickName", "Scotty");
        assertEquals(expected, doc1a.toMap());
        assertEquals(3, doc1a.getSequence());

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(db.save(doc1b, cc));
            Document savedDoc = db.getDocument(doc.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(4, savedDoc.getSequence());
        } else {
            assertFalse(db.save(doc1b, cc));
            Document savedDoc = db.getDocument(doc.getId());
            assertEquals(expected, savedDoc.toMap());
            assertEquals(3, savedDoc.getSequence());
        }

        cleanDB();
    }

    @Test
    public void testSaveDocWithNoParentConflict() throws CouchbaseLiteException {
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    void testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl cc) throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setString("firstName", "Daniel");
        doc1a.setString("lastName", "Tiger");
        db.save(doc1a);

        Document savedDoc = db.getDocument(doc1a.getId());
        assertEquals(doc1a.toMap(), savedDoc.toMap());
        assertEquals(1, savedDoc.getSequence());

        MutableDocument doc1b = new MutableDocument("doc1");
        doc1b.setString("firstName", "Scott");
        doc1b.setString("lastName", "Tiger");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(db.save(doc1b, cc));
            savedDoc = db.getDocument(doc1b.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(2, savedDoc.getSequence());
        } else {
            assertFalse(db.save(doc1b, cc));
            savedDoc = db.getDocument(doc1b.getId());
            assertEquals(doc1a.toMap(), savedDoc.toMap());
            assertEquals(1, savedDoc.getSequence());
        }

        cleanDB();
    }

    @Test
    public void testSaveDocWithDeletedConflict() throws CouchbaseLiteException {
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    void testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl cc) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        db.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        Document doc1a = db.getDocument("doc1");
        MutableDocument doc1b = db.getDocument("doc1").toMutable();

        // Delete doc1a:
        db.delete(doc1a);
        assertEquals(2, doc1a.getSequence());
        assertNull(db.getDocument(doc.getId()));

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(db.save(doc1b, cc));
            Document savedDoc = db.getDocument(doc.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(3, savedDoc.getSequence());
        } else {
            assertFalse(db.save(doc1b, cc));
            assertNull(db.getDocument(doc.getId()));
        }

        cleanDB();
    }

    @Test
    public void testDeleteAndUpdateDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        db.save(doc);

        db.delete(doc);
        assertEquals(2, doc.getSequence());
        assertNull(db.getDocument(doc.getId()));

        doc.setString("firstName", "Scott");
        db.save(doc);
        assertEquals(3, doc.getSequence());
        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        assertEquals(expected, doc.toMap());

        Document savedDoc = db.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testDeleteAlreadyDeletedDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        db.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        Document doc1a = db.getDocument("doc1");
        MutableDocument doc1b = db.getDocument("doc1").toMutable();

        // Delete doc1a:
        db.delete(doc1a);
        assertEquals(2, doc1a.getSequence());
        assertNull(db.getDocument(doc.getId()));

        // Delete doc1b:
        db.delete(doc1b);
        assertEquals(2, doc1b.getSequence());
        assertNull(db.getDocument(doc.getId()));
    }

    @Test
    public void testDeleteDocWithConflict() throws CouchbaseLiteException {
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    void testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl cc) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        db.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        MutableDocument doc1a = db.getDocument("doc1").toMutable();
        MutableDocument doc1b = db.getDocument("doc1").toMutable();


        // Modify doc1a:
        doc1a.setString("firstName", "Scott");
        db.save(doc1a);

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        assertEquals(expected, doc1a.toMap());
        assertEquals(2, doc1a.getSequence());

        // Modify doc1b and delete, result to conflict when delete:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(db.delete(doc1b, cc));
            assertEquals(3, doc1b.getSequence());
            assertNull(db.getDocument(doc1b.getId()));
        } else {
            assertFalse(db.delete(doc1b, cc));
            Document savedDoc = db.getDocument(doc.getId());
            assertEquals(expected, savedDoc.toMap());
            assertEquals(2, savedDoc.getSequence());
        }

        cleanDB();
    }

    @Test
    public void testDeleteNonExistingDoc() throws CouchbaseLiteException {
        Document doc1a = generateDocument("doc1");
        Document doc1b = db.getDocument("doc1");

        // purge doc
        db.purge(doc1a);
        assertEquals(0, db.getCount());
        assertNull(db.getDocument(doc1a.getId()));

        try {
            db.delete(doc1a);
            fail();
        } catch (CouchbaseLiteException e) {
            if (e.getCode() == CBLError.Code.CBLErrorNotFound)
                ;// expected
            else
                fail();
        }

        db.delete(doc1b);
        assertEquals(0, db.getCount());
        assertNull(db.getDocument(doc1b.getId()));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1652
    @Test
    public void testDeleteWithOldDocInstance() throws CouchbaseLiteException {
        // 1. save
        MutableDocument mdoc = new MutableDocument("doc");
        mdoc.setBoolean("updated", false);
        db.save(mdoc);

        // 2. update
        Document doc = db.getDocument("doc");
        mdoc = doc.toMutable();
        mdoc.setBoolean("updated", true);
        db.save(mdoc);

        // 3. delete by previously retrived document
        db.delete(doc);
        assertNull(db.getDocument("doc"));
    }
}
