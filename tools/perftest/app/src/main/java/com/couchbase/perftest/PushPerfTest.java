package com.couchbase.perftest;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.URLEndpoint;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


public class PushPerfTest extends PerfTest {
    static final int kNumIterations = 1;
    Benchmark bench = new Benchmark();
    static final int docs = 100 * 1000;  // 100K
    static final String SG_URL = "ws://10.17.0.201:4984/db";

    protected PushPerfTest(Context context, DatabaseConfiguration dbConfig) {
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

            bench.start();

            URI uri = null;
            try {
                uri = new URI(SG_URL);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return;
            }
            URLEndpoint endpoint = new URLEndpoint(uri);
            ReplicatorConfiguration config = new ReplicatorConfiguration(db, endpoint)
                    .setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH);
            Replicator replicator = new Replicator(config);
            final CountDownLatch latch = new CountDownLatch(1);
            replicator.addChangeListener(new ReplicatorChangeListener() {
                @Override
                public void changed(ReplicatorChange change) {
                    if (change.getStatus().getActivityLevel().equals(Replicator.ActivityLevel.STOPPED))
                        latch.countDown();
                }
            });
            replicator.start();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            double t = bench.stop();
            System.err.print(String.format("Push %d documents in %.06f sec\n", docs, t));
        }

        System.err.print(String.format("Push %5d docs:  ", docs));
        bench.printReport(null);
        System.err.print("                    ");
        bench.printReport(1.0 / docs, "doc");
        System.err.print(String.format("                     Rate: %.0f docs/sec\n", docs / bench.median()));


    }


    void createDocs(final int docs) {
        final Map<String, Object> map;
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        InputStreamReader isr = new InputStreamReader(getAsset("doc.json"));
        try {
            Gson gson = new Gson();
            map = gson.fromJson(isr, type);
        } finally {
            try {
                isr.close();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        final AtomicInteger atomic = new AtomicInteger(0);
        for (int j = 0; j < docs / 1000; j++) {
            try {
                db.inBatch(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 1000; i++) {
                            try {
                                String docID = String.format(Locale.ENGLISH, "doc-%08d", atomic.incrementAndGet());
                                MutableDocument doc = new MutableDocument(docID);
                                doc.setInt("index", atomic.get());
                                doc.setData(map);
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
