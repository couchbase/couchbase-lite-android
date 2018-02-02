package com.couchbase.lite.api;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.EncryptionKey;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatabaseAPITest extends BaseTest {
    static final String TAG = DatabaseAPITest.class.getSimpleName();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testNewDatabase() throws CouchbaseLiteException {
        // --- code example ---
        DatabaseConfiguration config = new DatabaseConfiguration(/* Android Context*/ context);
        Database database = new Database("my-database", config);
        // --- code example ---

        database.delete();
    }

    @Test
    public void testLogging() throws CouchbaseLiteException {
        // --- code example ---
        Database.setLogLevel(Database.LogDomain.REPLICATOR, Database.LogLevel.VERBOSE);
        Database.setLogLevel(Database.LogDomain.QUERY, Database.LogLevel.VERBOSE);
        // --- code example ---
    }

    @Test
    public void testSingletonPattern() throws CouchbaseLiteException {
        // --- code example ---
        DataManager mgr = DataManager.instance(new DatabaseConfiguration(/* Android Context*/ context));
        // --- code example ---

        mgr.delete();
    }

    @Test
    public void testEncryption() throws CouchbaseLiteException {
        // --- code example ---
        DatabaseConfiguration config = new DatabaseConfiguration(/* Android Context*/ context)
                .setEncryptionKey(new EncryptionKey("secretpassword"));
        Database database = new Database("my-database", config);
        // --- code example ---

        database.delete();
    }
}
