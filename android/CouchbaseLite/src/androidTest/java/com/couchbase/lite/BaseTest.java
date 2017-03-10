package com.couchbase.lite;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.couchbase.lite.utils.FileUtils;

import org.junit.After;
import org.junit.Before;

import java.io.File;

import static org.junit.Assert.*;

public class BaseTest {
    static {
        try {
            System.loadLibrary("LiteCoreJNI");
        } catch (Exception e) {
            fail("ERROR: Failed to load libLiteCoreJNI.so");
        }
    }

    protected final static String kDatabaseName = "testdb";

    protected Context context;
    protected File dir;
    protected Database db = null;


    @Before
    public void setUp() {
        Log.e("BaseTest", "setUp");
        context = InstrumentationRegistry.getContext();
        dir = new File(context.getFilesDir(), "CouchbaseLite");

        FileUtils.cleanDirectory(dir);

        openDB();
    }

    @After
    public void tearDown() {
        Log.e("BaseTest", "tearDown");
        if (db != null) {
            db.close();
            db = null;
        }
    }

    protected void openDB() {
        assertNull(db);

        DatabaseOptions options = DatabaseOptions.getDefaultOptions();
        options.setDirectory(dir);
        Database db = new Database(kDatabaseName, options);
        assertNotNull(db);
    }

    protected void closeDB() {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    protected void reopenDB() {
        closeDB();
        openDB();
    }
}

