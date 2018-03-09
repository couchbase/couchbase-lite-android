package com.couchbase.perftest;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;

import java.io.File;
import java.io.InputStream;

public abstract class PerfTest {
    protected static final String TAG = "PerfTest";
    protected static final String DB_NAME = "perfdb";
    protected Context context;
    protected Database db;
    protected String dbName;
    protected DatabaseConfiguration dbConfig;

    protected abstract void test();

    protected PerfTest(Context context, DatabaseConfiguration dbConfig) {
        this.context = context;
        this.dbConfig = dbConfig;
        this.dbName = DB_NAME;
    }

    public void run() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Log.i(TAG, String.format("====== %s ======", PerfTest.this.getClass().getSimpleName()));
                setUp();
                test();
                tearDown();
                return null;
            }
        }.execute();
    }

    protected void setUp() {
        openDB();
    }

    protected void tearDown() {
        closeDB();
    }

    protected void measure(int count, String unit, Runnable runnable) {
        Benchmark b = new Benchmark();
        final int reps = 10;
        for (int i = 0; i < reps; i++) {
            eraseDB();
            b.start();
            runnable.run();
            double t = b.stop(); // sec
            System.err.print(String.format("%.03f  ", t));
        }
        System.err.print("(sec)\n");
        b.printReport(null);
        if (count > 1)
            b.printReport(1.0 / count, unit);
    }

    protected void openDB() {
        try {
            db = new Database(dbName, dbConfig);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    protected void closeDB() {
        db = closeDB(db);
    }

    protected Database closeDB(Database _db) {
        if (_db != null) {
            try {
                _db.close();
            } catch (CouchbaseLiteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    protected void eraseDB() {
        closeDB();
        try {
            Database.delete(dbName, new File(dbConfig.getDirectory()));
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        openDB();
    }

    protected void reopenDB() {
        closeDB();
        openDB();
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }
}
