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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.Fn;
import com.couchbase.lite.utils.Report;

import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PULL;
import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PUSH;
import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class BaseReplicatorTest extends BaseTest {
    private static final String[] ACTIVITY_NAMES = {"stopped", "offline", "connecting", "idle", "busy"};
    private static final String OTHERDB = "otherdb";

    protected Database otherDB;
    protected Replicator repl;
    protected long timeout;  // seconds

    @Before
    public void setUp() throws Exception {
        config = new Config(InstrumentationRegistry.getContext().getAssets().open(Config.TEST_PROPERTIES_FILE));

        super.setUp();

        timeout = 15; // seconds
        otherDB = openDB(OTHERDB);
        assertTrue(otherDB.isOpen());
        assertNotNull(otherDB);

        try { Thread.sleep(500); }
        catch (Exception ignore) { }
    }

    @After
    public void tearDown() {
        if (!otherDB.isOpen()) {
            Report.log("expected otherDB to be open", new Exception());
        }

        try {
            if (otherDB != null) {
                otherDB.close();
                otherDB = null;
            }
            deleteDatabase(OTHERDB);
        }
        catch (CouchbaseLiteException e) {
            Report.log("Failed closing DB", e);
        }

        super.tearDown();

        try { Thread.sleep(500); } catch (Exception ignore) { }
    }

    protected URLEndpoint getRemoteEndpoint(String dbName, boolean secure) throws URISyntaxException {
        String uri = (secure ? "wss://" : "ws://") + config.remoteHost() + ":" + (secure ? config
            .secureRemotePort() : config.remotePort()) + "/" + dbName;
        return new URLEndpoint(new URI(uri));
    }

    protected ReplicatorConfiguration makeConfig(boolean push, boolean pull, boolean continuous, Endpoint target) {
        return makeConfig(push, pull, continuous, this.db, target);
    }

    protected ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database db,
        Endpoint target) {
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
        return run(r, code, domain, false, false, null);
    }

    protected Replicator run(
        final ReplicatorConfiguration config,
        final int code,
        final String domain,
        final boolean ignoreErrorAtStopped) throws InterruptedException {
        return run(new Replicator(config), code, domain, ignoreErrorAtStopped, false, null);
    }

    protected Replicator run(
        final ReplicatorConfiguration config,
        final int code,
        final String domain,
        final boolean ignoreErrorAtStopped,
        final boolean reset,
        final Fn.Consumer<Replicator> onReady)
        throws InterruptedException {
        return run(new Replicator(config), code, domain, ignoreErrorAtStopped, reset, null);
    }

    protected Replicator run(
        final Replicator r,
        final int code,
        final String domain,
        final boolean ignoreErrorAtStopped,
        final boolean reset,
        final Fn.Consumer<Replicator> onReady)
        throws InterruptedException {
        repl = r;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<AssertionError> replicationFailure = new AtomicReference<>(null);

        ListenerToken token = repl.addChangeListener(
            executor,
            change -> {
                final Replicator.Status status = change.getStatus();
                final CouchbaseLiteException error = status.getError();

                final String activity = ACTIVITY_NAMES[status.getActivityLevel().getValue()];
                final long completed = status.getProgress().getCompleted();
                final long total = status.getProgress().getTotal();

                log(
                    LogLevel.INFO,
                    "ReplicatorChangeListener.changed() status: " + activity
                        + "(" + completed + "/" + total + "), lastError: " + error);

                try {
                    if (!r.getConfig().isContinuous()) {
                        if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                            if (code == 0) {
                                if (!ignoreErrorAtStopped) { assertNull(error); }
                            }
                            else {
                                assertNotNull(error);
                                assertEquals(code, error.getCode());
                                if (domain != null) { assertEquals(domain, error.getDomain()); }
                            }
                            latch.countDown();
                        }
                    }
                    else {
                        if ((status.getActivityLevel() == Replicator.ActivityLevel.IDLE)
                            && (status.getProgress().getCompleted() == status.getProgress().getTotal())) {
                            if (code == 0) { assertNull(error); }
                            else {
                                assertEquals(code, error.getCode());
                                if (domain != null) { assertEquals(domain, error.getDomain()); }
                            }
                            latch.countDown();
                        }
                        else if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                            if (code == 0) {
                                // TBD
                            }
                            else {
                                assertNotNull(error);
                                assertEquals(code, error.getCode());
                                assertEquals(domain, error.getDomain());
                                latch.countDown();
                            }
                        }
                    }
                }
                catch (AssertionError e) {
                    replicationFailure.set(e);
                }
            });

        if (reset) { repl.resetCheckpoint(); }

        if (onReady != null) { onReady.accept(repl); }

        repl.start();
        boolean success = latch.await(timeout, TimeUnit.SECONDS);
        repl.removeChangeListener(token);

        // see if the replication succeeded
        AssertionError err = replicationFailure.get();
        if (err != null) { throw err; }
        assertTrue(success);

        return repl;
    }

    void stopContinuousReplicator(Replicator repl) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(
            executor,
            change -> {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    latch.countDown();
                }
            });
        try {
            repl.stop();
            assertTrue(latch.await(timeout, TimeUnit.SECONDS));
        }
        finally {
            repl.removeChangeListener(token);
        }
    }
}
