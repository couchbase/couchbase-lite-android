package com.couchbase.perftest;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Meta;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;


public class QueryPerfTest extends PerfTest {
    static final int kNumIterations = 10;
    Benchmark bench = new Benchmark();
    final int docs = 1000 * 1000;

    protected QueryPerfTest(Context context, DatabaseConfiguration dbConfig) {
        super(context, dbConfig);
    }

    @Override
    protected void setUp() {
        super.setUp();

        closeDB();

        try {
            // 1M document database
            ZipUtils.unzip(getAsset("perfdb.cblite2.zip"), context.getFilesDir());
        } catch (IOException e) {
            e.printStackTrace();
        }

        openDB();

        // Log.i(TAG, String.format("--- Creating %d documents ---", docs));
        // createDocs(docs);

        if (db.getCount() != docs)
            Log.e(TAG, "DB size is invalid. size: " + db.getCount());
    }

    @Override
    protected void test() {
        for (int i = 0; i < kNumIterations; i++) {
            System.err.print(String.format("Starting iteration #%d...\n", i + 1));
            int count = query();
            if (count != docs)
                Log.e(TAG, String.format(Locale.ENGLISH, "Query result count does not match! Actual -> %; expected -> %d", count, docs));
        }
        System.err.print(String.format("Query %5d docs:  ", docs));
        bench.printReport(null);
        System.err.print("                    ");
        bench.printReport(1.0 / docs, "doc");
        System.err.print(String.format("                     Rate: %.0f docs/sec\n", docs / bench.median()));
    }

    int query() {
        int count = 0;
        bench.start();
        Query q = QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(db));
        ResultSet rs = null;
        try {
            rs = q.execute();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        for (Result r : rs) {
            count++;
        }
        double t = bench.stop();
        System.err.print(String.format("Query %d documents in %.06f sec\n", count, t));
        return count;
    }

    void createDocs(final int docs) {
        final AtomicInteger atomic = new AtomicInteger(0);
        for (int j = 0; j < docs / 1000; j++) {
            try {
                db.inBatch(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 1000; i++) {
                            String docID = String.format(Locale.ENGLISH, "doc-%08d", atomic.incrementAndGet());
                            MutableDocument doc = new MutableDocument(docID);
                            doc.setInt("index", atomic.get());
                            try {
                                db.save(doc);
                            } catch (CouchbaseLiteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            } catch (CouchbaseLiteException e) {
                e.printStackTrace();
            }
        }
    }
}

