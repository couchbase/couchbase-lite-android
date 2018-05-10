//
// ReplicationAPITest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.api;

import com.couchbase.lite.BaseReplicatorTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.URLEndpoint;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.IOUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
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

        // # tag::replication[]
        URI uri = new URI("ws://localhost:4984/db");
        Endpoint endpoint = new URLEndpoint(uri);
        ReplicatorConfiguration config = new ReplicatorConfiguration(database, endpoint);
        config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replication = new Replicator(config);
        replication.start();
        // # end::replication[]

        replication.stop();
    }

    @Test
    public void testTroubleshooting() {
        if (!config.replicatorTestsEnabled()) return;

        // # tag::replication-logging[]
        Database.setLogLevel(LogDomain.REPLICATOR, LogLevel.VERBOSE);
        // # end::replication-logging[]
    }

    @Test
    public void testReplicationStatus() throws URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        URI uri = new URI("ws://localhost:4984/db");
        Endpoint endpoint = new URLEndpoint(uri);
        ReplicatorConfiguration config = new ReplicatorConfiguration(database, endpoint);
        config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replication = new Replicator(config);

        // # tag::replication-status[]
        replication.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                if (change.getStatus().getActivityLevel() == Replicator.ActivityLevel.STOPPED)
                    Log.i(TAG, "Replication stopped");
            }
        });
        // # end::replication-status[]
    }

    @Test
    public void testHandlingNetworkErrors() throws URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        URI uri = new URI("ws://localhost:4984/db");
        Endpoint endpoint = new URLEndpoint(uri);
        ReplicatorConfiguration config = new ReplicatorConfiguration(database, endpoint);
        config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replication = new Replicator(config);

        // # tag::replication-error-handling[]
        replication.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                CouchbaseLiteException error = change.getStatus().getError();
                if (error != null)
                    Log.w(TAG, "Error code:: %d", error.getCode());
            }
        });
        replication.start();
        // # end::replication-error-handling[]

        replication.stop();
    }

    // ### Certificate Pinning

    @Test
    public void testCertificatePinning() throws URISyntaxException, IOException {
        URI uri = new URI("ws://localhost:4984/db");
        Endpoint endpoint = new URLEndpoint(uri);
        ReplicatorConfiguration config = new ReplicatorConfiguration(database, endpoint);

        // # tag::certificate-pinning[]
        InputStream is = getAsset("cert.cer");
        byte[] cert = IOUtils.toByteArray(is);
        is.close();
        config.setPinnedServerCertificate(cert);
        // # end::certificate-pinning[]
    }

    // ### Reset replicator checkpoint

    @Test
    public void testReplicationResetCheckpoint() throws URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        URI uri = new URI("ws://localhost:4984/db");
        Endpoint endpoint = new URLEndpoint(uri);
        ReplicatorConfiguration config = new ReplicatorConfiguration(database, endpoint);
        config.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
        Replicator replicator = new Replicator(config);
        replicator.start();

        // # tag::replication-reset-checkpoint[]
        replicator.resetCheckpoint();
        replicator.start();
        // # end::replication-reset-checkpoint[]

        replicator.stop();
    }
}
