package com.couchbase.lite.api;

import com.couchbase.lite.BaseReplicatorTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Log;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorConfiguration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class ReplicationAPITest extends BaseReplicatorTest {
    static final String TAG = ReplicationAPITest.class.getSimpleName();
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
    public void testStartingAReplication() throws URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        // --- code example ---
        URI uri = new URI("blip://localhost:4984/db");
        ReplicatorConfiguration replConfig = new ReplicatorConfiguration(database, uri);
        replConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replication = new Replicator(replConfig);
        replication.start();
        // --- code example ---

        replication.stop();
    }

    @Test
    public void testTroubleshooting() {
        if (!config.replicatorTestsEnabled()) return;

        // --- code example ---
        Database.setLogLevel(Database.LogDomain.REPLICATOR, Database.LogLevel.VERBOSE);
        // --- code example ---
    }

    @Test
    public void testReplicationStatus() throws URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        URI uri = new URI("blip://localhost:4984/db");
        ReplicatorConfiguration replConfig = new ReplicatorConfiguration(database, uri);
        replConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replication = new Replicator(replConfig);

        // --- code example ---
        replication.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                if (change.getStatus().getActivityLevel() == Replicator.ActivityLevel.STOPPED)
                    Log.i(TAG, "Replication stopped");
            }
        });
        // --- code example ---
    }

    @Test
    public void testHandlingNetworkErrors() throws URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        URI uri = new URI("blip://localhost:4984/db");
        ReplicatorConfiguration replConfig = new ReplicatorConfiguration(database, uri);
        replConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replication = new Replicator(replConfig);

        // --- code example ---
        replication.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                CouchbaseLiteException error = change.getStatus().getError();
                if (error != null)
                    Log.w(TAG, "Error code:: %d", error.getCode());
            }
        });
        replication.start();
        // --- code example ---

        replication.stop();
    }
}
