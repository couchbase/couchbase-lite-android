package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DatabaseTest extends BaseTest {
    private static final String TAG = "DatabaseTest";

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

    @Test
    public void testCreate() {
        DatabaseOptions options = DatabaseOptions.getDefaultOptions();
        options.setDirectory(dir);
        Database db = new Database("db", options);
        assertNotNull(db);
        Log.e(TAG, "db.getPath()=%s", db.getPath());
        assertNotNull(db.getPath());
        assertTrue(db.getPath().endsWith(".cblite2"));
        assertEquals("db", db.getName());

        db.close();
        assertNull(db.getPath());
        Database.delete("db", dir);
    }

    @Test
    public void testDelete() throws Exception {
        assertNotNull(db.getPath());

        String path = db.getPath();
        db.delete();
        assertNull(db.getPath());
        assertFalse(new File(path).exists());
    }

    @Test
    public void testCreateDocument() throws Exception {
        Document doc = db.getDocument();
        assertNotNull(doc);
        assertNotNull(doc.getID());
        assertTrue(doc.getID().length() > 0);
        assertEquals(db, doc.getDatabase());
        assertFalse(doc.exists());
        assertFalse(doc.isDeleted());
        assertNull(doc.getProperties());

        Document doc1 = db.getDocument("doc1");
        assertNotNull(doc1);
        assertEquals("doc1", doc1.getID());
        assertEquals(db, doc1.getDatabase());
        assertFalse(doc1.exists());
        assertFalse(doc1.isDeleted());
        // TODO: Cache is not implemented yet. So, following line will fail.
        // assertEquals(doc1, db.getDocument("doc1"));
        // TODO: Properties is not implemented yet. So, following line will fail.
        // assertNull(doc1.getProperties());
    }

    @Test
    public void testDocumentExists() throws Exception {
        assertFalse(db.documentExists("doc1"));

        Document doc1 = db.getDocument("doc1");
        doc1.save();
        assertTrue(db.documentExists("doc1"));
        // TODO: Properties is not implemented yet. So, following line will fail.
        // assertNull(doc1.getProperties());
    }

    @Test
    public void testInBatchSuccess() throws Exception {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    String docID = String.format(Locale.ENGLISH, "doc%d", i);
                    Document doc = db.getDocument(docID);
                    doc.save();
                }
            }
        });
        for (int i = 0; i < 10; i++) {
            String docID = String.format(Locale.ENGLISH, "doc%d", i);
            assertTrue(db.documentExists(docID));
        }
    }
}
