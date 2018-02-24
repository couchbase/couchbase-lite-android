//
// BaseReplicatorTest.java
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
package com.couchbase.lite;

import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.Config;

import org.junit.After;
import org.junit.Before;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PULL;
import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH;
import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BaseReplicatorTest extends BaseTest {
    protected final static String kOtherDatabaseName = "otherdb";
    Database otherDB;
    Replicator repl;
    long timeout;  // seconds

    protected URLEndpoint getRemoteEndpoint(String dbName, boolean secure) throws URISyntaxException {
        String uri = (secure ? "wss://" : "ws://") + config.remoteHost() + ":" + (secure ? config.secureRemotePort() : config.remotePort()) + "/" + dbName;
        return new URLEndpoint(new URI(uri));
    }

    protected ReplicatorConfiguration makeConfig(boolean push, boolean pull, boolean continuous, Endpoint target) {
        return makeConfig(push, pull, continuous, this.db, target);
    }

    protected ReplicatorConfiguration makeConfig(boolean push, boolean pull, boolean continuous, Database db, Endpoint target) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(db, target);
        config.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        config.setContinuous(continuous);
        return config;
    }

    protected Replicator run(final ReplicatorConfiguration config, final int code, final String domain)
            throws InterruptedException {
        return run(config, code, domain, false);
    }

    protected Replicator run(final Replicator r, final int code, final String domain)
            throws InterruptedException {
        return run(r, code, domain, false);
    }

    protected Replicator run(final ReplicatorConfiguration config, final int code, final String domain, final boolean ignoreErrorAtStopped) throws InterruptedException {
        return run(new Replicator(config), code, domain, ignoreErrorAtStopped);
    }

    protected Replicator run(final Replicator r, final int code, final String domain, final boolean ignoreErrorAtStopped)
            throws InterruptedException {
        repl = r;
        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                CouchbaseLiteException error = status.getError();
                final String kActivityNames[] = {"stopped", "offline", "connecting", "idle", "busy"};
                Log.i(TAG, "ReplicatorChangeListener.changed() status: %s (%d / %d), lastError = %s",
                        kActivityNames[status.getActivityLevel().getValue()],
                        status.getProgress().getCompleted(), status.getProgress().getTotal(),
                        error);
                if (r.getConfig().isContinuous()) {
                    if (status.getActivityLevel() == Replicator.ActivityLevel.IDLE &&
                            status.getProgress().getCompleted() == status.getProgress().getTotal()) {
                        if (code != 0) {
                            assertEquals(code, error.getCode());
                            if (domain != null)
                                assertEquals(domain, error.getDomain());
                        } else {
                            assertNull(error);
                        }
                        latch.countDown();
                    } else if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                        if (code != 0) {
                            assertNotNull(error);
                            assertEquals(code, error.getCode());
                            assertEquals(domain, error.getDomain());
                            latch.countDown();
                        } else {
                            // TBD
                        }
                    }
                } else {
                    if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                        if (code != 0) {
                            assertNotNull(error);
                            assertEquals(code, error.getCode());
                            if (domain != null)
                                assertEquals(domain, error.getDomain());
                        } else {
                            if (!ignoreErrorAtStopped)
                                assertNull(error);
                        }
                        latch.countDown();
                    }
                }
            }
        });
        repl.start();
        boolean ret = latch.await(timeout, TimeUnit.SECONDS);
        repl.removeChangeListener(token);
        assertTrue(ret);
        return repl;
    }

    void stopContinuousReplicator(Replicator repl) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                CouchbaseLiteException error = status.getError();
                final String kActivityNames[] = {"stopped", "offline", "connecting", "idle", "busy"};
                Log.i(TAG, "--- ReplicatorChangeListener.changed() -> status: %s (%d / %d), lastError = %s",
                        kActivityNames[status.getActivityLevel().getValue()],
                        status.getProgress().getCompleted(), status.getProgress().getTotal(),
                        error);
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    latch.countDown();
                }
            }
        });
        try {
            repl.stop();
            assertTrue(latch.await(timeout, TimeUnit.SECONDS));
        } finally {
            repl.removeChangeListener(token);
        }
    }

    @Before
    public void setUp() throws Exception {
        config = new Config(
                InstrumentationRegistry.getContext().getAssets().open(Config.TEST_PROPERTIES_FILE));

        conflictResolver = new ConflictTest.MergeThenTheirsWins();
        super.setUp();

        timeout = 15; // seconds
        otherDB = open(kOtherDatabaseName);
        assertTrue(otherDB.isOpen());
        assertNotNull(otherDB);

        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }
    }

    @After
    public void tearDown() throws Exception {
        assertTrue(otherDB.isOpen());
        if (otherDB != null) {
            otherDB.close();
            otherDB = null;
        }
        deleteDatabase(kOtherDatabaseName);

        super.tearDown();

        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }
    }
}
