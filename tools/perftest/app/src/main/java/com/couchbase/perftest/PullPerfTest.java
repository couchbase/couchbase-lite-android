package com.couchbase.perftest;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.URLEndpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

public class PullPerfTest extends PerfTest {
    static final int kNumIterations = 1;
    Benchmark bench = new Benchmark();
    static final int docs = 100 * 1000;  // 100K
    static final String SG_URL = "ws://10.17.0.201:4984/db";

    protected PullPerfTest(Context context, DatabaseConfiguration dbConfig) {
        super(context, dbConfig);
    }

    @Override
    protected void setUp() {
        super.setUp();

        eraseDB();

        if (db.getCount() != 0)
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
                    .setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
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

            if (db.getCount() != docs)
                Log.e(TAG, "DB size is invalid. size: " + db.getCount());

            double t = bench.stop();
            System.err.print(String.format("PULL %d documents in %.06f sec\n", docs, t));
        }

        System.err.print(String.format("PULL %5d docs:  ", docs));
        bench.printReport(null);
        System.err.print("                    ");
        bench.printReport(1.0 / docs, "doc");
        System.err.print(String.format("                     Rate: %.0f docs/sec\n", docs / bench.median()));
    }
}
