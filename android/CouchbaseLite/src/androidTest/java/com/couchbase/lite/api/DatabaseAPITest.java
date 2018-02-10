package com.couchbase.lite.api;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.EncryptionKey;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.ZipUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

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

    // ### New Database
    @Test
    public void testNewDatabase() throws CouchbaseLiteException {
        // --- code example ---
        DatabaseConfiguration config = new DatabaseConfiguration(/* Android Context*/ context);
        Database database = new Database("my-database", config);
        // --- code example ---

        database.delete();
    }

    // ### Logging
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

    // ###  Encryption
    @Test
    public void testEncryption() throws CouchbaseLiteException {
        // --- code example ---
        DatabaseConfiguration config = new DatabaseConfiguration(/* Android Context*/ context)
                .setEncryptionKey(new EncryptionKey("secretpassword"));
        Database database = new Database("my-database", config);
        // --- code example ---

        database.delete();
    }

    // ### Loading a pre-built database
    @Test
    public void testPreBuiltDatabase() throws IOException {
        // --- code example ---
        DatabaseConfiguration config = new DatabaseConfiguration(/* Android Context*/ context);
        ZipUtils.unzip(getAsset("replacedb/android200-sqlite.cblite2.zip"), context.getFilesDir());
        File path = new File(context.getFilesDir(), "android-sqlite");
        try {
            Database.copy(path, "travel-sample", config);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Could not load pre-built database");
        }
        // --- code example ---
    }
}
