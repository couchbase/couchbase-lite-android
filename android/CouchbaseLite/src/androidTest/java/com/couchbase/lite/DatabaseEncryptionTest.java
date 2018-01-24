package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.IOUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBusy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseEncryptionTest extends BaseTest {
    private Database seekrit;

    //---------------------------------------------
    //  setUp/tearDown
    //---------------------------------------------

    @Before
    public void setUp() throws Exception {
        Log.i(TAG, "setUp");
        super.setUp();

    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown");

        if (seekrit != null) seekrit.close();

        // database exist, delete it
        if (Database.exists("seekrit", getDir())) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 10; i++) {
                try {
                    Database.delete("seekrit", getDir());
                    break;
                } catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == kC4ErrorBusy) {
                        try {
                            Thread.sleep(300);
                        } catch (Exception e) {
                        }
                    } else {
                        throw ex;
                    }
                }
            }
        }

        super.tearDown();
    }

    Database openSeekrit(String password) throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        if (password != null)
            builder.setEncryptionKey(new EncryptionKey(password));
        builder.setDirectory(getDir().getAbsolutePath());
        return new Database("seekrit", builder.build());
    }

    @Test
    public void testUnEncryptedDatabase() throws CouchbaseLiteException {
        // Create unencrypted database:
        seekrit = openSeekrit(null);
        assertNotNull(seekrit);

        Map<String, Object> map = new HashMap<>();
        map.put("answer", 42);
        MutableDocument doc = createMutableDocument(null, map);
        seekrit.save(doc);
        seekrit.close();
        seekrit = null;

        // Try to reopen with password (fails):
        try {
            openSeekrit("wrong");
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals("LiteCore", e.getDomainString());
            assertEquals(29, e.getCode());
        }

        // Reopen with no password:
        seekrit = openSeekrit(null);
        assertNotNull(seekrit);
        assertEquals(1, seekrit.getCount());
    }

    @Test
    public void testEncryptedDatabase() throws CouchbaseLiteException {
        // Create encrypted database:
        seekrit = openSeekrit("letmein");
        assertNotNull(seekrit);

        Map<String, Object> map = new HashMap<>();
        map.put("answer", 42);
        MutableDocument doc = createMutableDocument(null, map);
        seekrit.save(doc);
        seekrit.close();
        seekrit = null;

        // Reopen without password (fails):
        try {
            openSeekrit(null);
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals("LiteCore", e.getDomainString());
            assertEquals(29, e.getCode());
        }

        // Reopen with wrong password (fails):
        try {
            openSeekrit("wrong");
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals("LiteCore", e.getDomainString());
            assertEquals(29, e.getCode());
        }

        // Reopen with correct password:
        seekrit = openSeekrit("letmein");
        assertNotNull(seekrit);
        assertEquals(1, seekrit.getCount());
    }

    @Test
    public void testDeleteEncryptedDatabase() throws CouchbaseLiteException {
        // Create encrypted database:
        seekrit = openSeekrit("letmein");
        assertNotNull(seekrit);

        // Delete database:
        seekrit.delete();

        // Re-create database:
        seekrit = openSeekrit(null);
        assertNotNull(seekrit);
        assertEquals(0, seekrit.getCount());
        seekrit.close();
        seekrit = null;

        // Make sure it doesn't need a password now:
        seekrit = openSeekrit(null);
        assertNotNull(seekrit);
        assertEquals(0, seekrit.getCount());
        seekrit.close();
        seekrit = null;

        // Make sure old password doesn't work:
        try {
            seekrit = openSeekrit("letmein");
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals("LiteCore", e.getDomainString());
            assertEquals(29, e.getCode());
        }
    }

    @Test
    public void testCompactEncryptedDatabase() throws CouchbaseLiteException {
        // Create encrypted database:
        seekrit = openSeekrit("letmein");
        assertNotNull(seekrit);

        // Create a doc and then update it:
        Map<String, Object> map = new HashMap<>();
        map.put("answer", 42);
        MutableDocument doc = createMutableDocument(null, map);
        Document savedDoc = seekrit.save(doc);
        doc = savedDoc.toMutable();
        doc.setValue("answer", 84);
        savedDoc = seekrit.save(doc);

        // Compact:
        seekrit.compact();

        // Update the document again:
        doc = savedDoc.toMutable();
        doc.setValue("answer", 85);
        savedDoc = seekrit.save(doc);

        // Close and re-open:
        seekrit.close();
        seekrit = openSeekrit("letmein");
        assertNotNull(seekrit);
        assertEquals(1, seekrit.getCount());
    }

    @Test
    public void testEncryptedBlobs() throws CouchbaseLiteException, IOException {
        testEncryptedBlobs("letmein");
    }

    void testEncryptedBlobs(String password) throws CouchbaseLiteException, IOException {
        // Create database with the password:
        seekrit = openSeekrit(password);
        assertNotNull(seekrit);

        // Save a doc with a blob:
        byte[] body = "This is a blob!".getBytes();
        MutableDocument mDoc = createMutableDocument("att");
        Blob blob = new Blob("text/plain", body);
        mDoc.setBlob("blob", blob);
        Document doc = seekrit.save(mDoc);

        // Read content from the raw blob file:
        blob = doc.getBlob("blob");
        assertNotNull(blob.digest());
        assertTrue(Arrays.equals(body, blob.getContent()));

        String filename = blob.digest().substring(5);
        filename = filename.replaceAll("/", "_");
        String path = String.format(Locale.ENGLISH, "%s/Attachments/%s.blob", seekrit.getPath(), filename);
        File file = new File(path);
        assertTrue(file.exists());
        byte[] raw = IOUtils.toByteArray(file);
        assertNotNull(raw);
        if (password != null)
            assertFalse(Arrays.equals(raw, body));
        else
            assertTrue(Arrays.equals(raw, body));
    }

    @Test
    public void testMultipleDatabases() throws CouchbaseLiteException {
        // Create encryped database:
        seekrit = openSeekrit("seekrit");

        // Get another instance of the database:
        Database seekrit2 = openSeekrit("seekrit");
        assertNotNull(seekrit2);
        seekrit2.close();

        // Try rekey:
        EncryptionKey newKey = new EncryptionKey("foobar");
        seekrit.setEncryptionKey(newKey);
    }

    @Test
    public void testAddKey() throws CouchbaseLiteException, IOException {
        rekeyUsingOldPassword(null, "letmein");
    }

    @Test
    public void testReKey() throws CouchbaseLiteException, IOException {
        rekeyUsingOldPassword("letmein", "letmeout");
    }

    @Test
    public void testRemoveKey() throws CouchbaseLiteException, IOException {
        rekeyUsingOldPassword("letmein", null);
    }

    void rekeyUsingOldPassword(String oldPass, String newPass) throws CouchbaseLiteException, IOException {
        // First run the encryped blobs test to populate the database:
        testEncryptedBlobs(oldPass);

        // Create some documents:
        seekrit.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("seq", i);
                    MutableDocument doc = createMutableDocument(null, map);
                    try {
                        seekrit.save(doc);
                    } catch (CouchbaseLiteException e) {
                        fail();
                    }
                }
            }
        });

        // Rekey:
        EncryptionKey newKey = new EncryptionKey(newPass);
        seekrit.setEncryptionKey(newKey);

        // Close & reopen seekrit:
        seekrit.close();
        seekrit = null;

        // Reopen the database with the new key:
        Database seekrit2 = openSeekrit(newPass);
        assertNotNull(seekrit2);
        seekrit = seekrit2;

        // Check the document and its attachment:
        Document doc = seekrit.getDocument("att");
        Blob blob = doc.getBlob("blob");
        assertNotNull(blob.getContent());
        String content = new String(blob.getContent());
        assertEquals("This is a blob!", content);

        // Query documents:
        Expression SEQ = Expression.property("seq");
        Query query = Query
                .select(SelectResult.expression(SEQ))
                .from(DataSource.database(seekrit))
                .where(SEQ.notNullOrMissing())
                .orderBy(Ordering.expression(SEQ));
        ResultSet rs = query.execute();
        assertNotNull(rs);
        int i = 0;
        for (Result r : rs) {
            assertEquals(i, r.getInt(0));
            i++;
        }
    }
}
