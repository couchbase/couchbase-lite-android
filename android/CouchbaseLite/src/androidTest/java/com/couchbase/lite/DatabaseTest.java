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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseTest extends BaseTest {
    private static final String TAG = DatabaseTest.class.getName();

    // helper method to open database
    private Database openDatabase(String dbName){
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

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------

    //---------------------------------------------
    //  Close Database
    //---------------------------------------------

    //---------------------------------------------
    //  Delete Database
    //---------------------------------------------

    //---------------------------------------------
    //  Delete Database (static)
    //---------------------------------------------

    //---------------------------------------------
    //  Database Existing
    //---------------------------------------------





//    @Test
//    public void testDelete() throws Exception {
//        assertNotNull(db.getPath());
//
//        File path = db.getPath();
//        db.delete();
//        assertNull(db.getPath());
//        assertFalse(path.exists());
//    }



//    @Test
//    public void testCreateDocument() throws Exception {
//        Document doc = db.getDocument();
//        assertNotNull(doc);
//        assertNotNull(doc.getID());
////        assertTrue(doc.getID().length() > 0);
//        assertEquals(db, doc.getDatabase());
//        assertFalse(doc.exists());
//        assertFalse(doc.isDeleted());
//        assertNull(doc.getProperties());
//
//        Document doc1 = db.getDocument("doc1");
//        assertNotNull(doc1);
//        assertEquals("doc1", doc1.getID());
//        assertEquals(db, doc1.getDatabase());
//        assertFalse(doc1.exists());
//        assertFalse(doc1.isDeleted());
//        assertEquals(doc1, db.getDocument("doc1"));
//        assertNull(doc1.getProperties());
//    }

//    @Test
//    public void testDocumentExists() throws Exception {
//        assertFalse(db.documentExists("doc1"));
//
//        Document doc1 = db.getDocument("doc1");
//        doc1.save();
//        assertTrue(db.documentExists("doc1"));
//        assertNull(doc1.getProperties());
//    }
//
//    @Test
//    public void testInBatchSuccess() throws Exception {
//        db.inBatch(new Runnable() {
//            @Override
//            public void run() {
//                for (int i = 0; i < 10; i++) {
//                    String docID = String.format(Locale.ENGLISH, "doc%d", i);
//                    Document doc = db.getDocument(docID);
//                    doc.save();
//                }
//            }
//        });
//        for (int i = 0; i < 10; i++) {
//            String docID = String.format(Locale.ENGLISH, "doc%d", i);
//            assertTrue(db.documentExists(docID));
//        }
//    }
}
