package com.couchbase.lite.api;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Log;
import com.couchbase.lite.MutableDocument;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// https://github.com/couchbaselabs/couchbase-mobile-portal/edit/master/md-docs/_20/guides/couchbase-lite/native-api/document/java.md
public class DocumentAPITest extends BaseTest {
    static final String TAG = DocumentAPITest.class.getSimpleName();
    static final String DATABASE_NAME = "travel-sample";

    Database database;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        database = open(DATABASE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        if (database != null) {
            database.close();
            database = null;
        }

        // database exist, delete it
        deleteDatabase(DATABASE_NAME);

        super.tearDown();
    }

    @Test
    public void testInitializers() {
        // --- code example ---
        Map<String, Object> dict = new HashMap<>();
        dict.put("type", "task");
        dict.put("owner", "todo");
        dict.put("createdAt", new Date());
        MutableDocument newTask = new MutableDocument(dict);
        try {
            database.save(newTask);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        }
        // --- code example ---
    }

    @Test
    public void testMutability() {
        MutableDocument newTask = new MutableDocument();

        // --- code example ---
        newTask.setString("name", "apples");
        try {
            database.save(newTask);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        }
        // --- code example ---
    }

    @Test
    public void testTypedAccessors() {
        MutableDocument newTask = new MutableDocument();

        // --- code example ---
        newTask.setValue("createdAt", new Date());
        Date date = newTask.getDate("createdAt");
        // --- code example ---
    }

    @Test
    public void testBatchOperations() {
        // --- code example ---
        try {
            database.inBatch(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        MutableDocument doc = new MutableDocument();
                        doc.setValue("type", "user");
                        doc.setValue("name", String.format("user %d", i));
                        doc.setBoolean("admin", false);
                        try {
                            database.save(doc);
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, e.toString());
                        }
                        Log.i(TAG, String.format("saved user document %s", doc.getString("name")));
                    }
                }
            });
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        }
        // --- code example ---
    }
}
