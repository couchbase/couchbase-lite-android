package com.couchbase.lite;


import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.utils.Config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.couchbase.lite.ReplicatorType.PULL;
import static com.couchbase.lite.ReplicatorType.PUSH;
import static com.couchbase.lite.ReplicatorType.PUSH_AND_PULL;
import static com.couchbase.lite.utils.Config.TEST_PROPERTIES_FILE;
import static com.couchbase.litecore.Constants.NetworkError.kC4NetErrUnknownHost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReplicatorTest extends BaseTest {

    Database otherDB;
    Replicator repl;
    long timeout;  // seconds
    boolean stopped;

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull) {
        ReplicatorTarget target = new ReplicatorTarget(otherDB);
        return makeConfig(push, pull, target);
    }

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull, String uri) {
        ReplicatorTarget target = new ReplicatorTarget(URI.create(uri));
        return makeConfig(push, pull, target);
    }

    private ReplicatorConfiguration makeConfig(boolean push, boolean pull, ReplicatorTarget target) {
        ReplicatorConfiguration config = new ReplicatorConfiguration();
        config.setDatabase(db);
        config.setTarget(target);
        config.setType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        return config;
    }

    private void run(final ReplicatorConfiguration config, final int code, final String domain) throws InterruptedException {
        repl = new Replicator(config);
        final CountDownLatch latch = new CountDownLatch(1);
        repl.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(Replicator replicator, Replicator.Status status, CouchbaseLiteException error) {
                final String kActivityNames[] = {"stopped", "idle", "busy"};
                Log.i(TAG, "---Status: %s (%d / %d), lastError = %s",
                        kActivityNames[status.getActivityLevel().getValue()],
                        status.getProgress().getCompleted(), status.getProgress().getTotal(),
                        error);
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    if (code != 0) {
                        assertEquals(code, error.getCode());
                        if (domain != null)
                            assertEquals(domain, error.getDomainString());
                    } else {
                        assertNull(error);
                    }
                    latch.countDown();
                }
            }
        });
        repl.start();
        assertTrue(latch.await(timeout, TimeUnit.SECONDS));
    }

    @Before
    public void setUp() throws Exception {
        config = new Config(InstrumentationRegistry.getContext().getAssets().open(TEST_PROPERTIES_FILE));
        if (!config.replicatorTestsEnabled())
            return;

        super.setUp();

        timeout = 5; // seconds
        otherDB = open("otherdb");
        assertNotNull(otherDB);
    }

    @After
    public void tearDown() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        if (otherDB != null) {
            otherDB.close();
            otherDB = null;
        }

        super.tearDown();
    }

    @Test
    public void testBadURL() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;

        ReplicatorConfiguration config = makeConfig(false, true, "blxp://localhost/db");
        run(config, 15, "LiteCore");
    }

    @Test
    public void testEmptyPush() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;

        ReplicatorConfiguration config = makeConfig(true, false);
        run(config, 0, null);
    }

    @Test
    public void testEmptyPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/db", this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(true, false, uri);
        run(config, 0, null);
    }

    @Test
    public void testEmptyPullFromRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/db", this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, uri);
        run(config, 0, null);
    }

    @Test
    public void testPushToRemoteDB() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        loadJSONResource("names_100.json");

        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/db", this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(true, false, uri);
        run(config, 0, null);
    }


    // These test are disabled because they require a password-protected database 'seekrit' to exist
    // on localhost:4984, with a user 'pupshaw' whose password is 'frank'.

    @Test
    public void testAuthenticationFailure() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/seekrit", this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, uri);
        run(config, 401, "WebSocket");
    }

    @Test
    public void testAuthenticatedPullHardcoded() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://pupshaw:frank@%s:%d/seekrit", this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, uri);
        run(config, 0, null);
    }

    @Test
    public void testAuthenticatedPull() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        String uri = String.format(Locale.ENGLISH, "blip://%s:%d/seekrit", this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, uri);
        Map<String, Object> options = new HashMap<>();
        Map<String, Object> auth = new HashMap<>();
        auth.put("username", "pupshaw");
        auth.put("password", "frank");
        options.put("auth", auth);
        config.setOptions(options);
        run(config, 0, null);
    }

    // TODO: Fails with https://github.com/couchbase/couchbase-lite-core/issues/149
    // @Test
    public void testMissingHost() throws InterruptedException {
        if (!config.replicatorTestsEnabled())
            return;
        timeout = 200;
        String uri = String.format(Locale.ENGLISH, "blip://foo.couchbase.com/db");
        ReplicatorConfiguration config = makeConfig(false, true, uri);
        config.setContinuous(true);
        run(config, kC4NetErrUnknownHost, "Network");
    }

    /**
     * How to test reaciability.
     * 1. Run sync gateway
     * 2. Disable Wifi with the device
     * 3. Run  testContinuousPush()
     * 4. Confirm if the replicator stops
     * 5. Enable Wifi
     * 6. Confirm if the replicator starts
     * 7. Confirm if sync gateway recevies some messages
     */
    /**
     @Test public void testContinuousPush() throws Exception {
     if (!config.replicatorTestsEnabled())
     return;

     loadJSONResource("names_100.json");

     timeout = 180; // 3min
     String uri = String.format(Locale.ENGLISH, "blip://%s:%d/%s", this.config.remoteHost(), this.config.remotePort(), this.config.remoteDB());
     ReplicatorConfiguration config = makeConfig(true, false, uri);
     config.setContinuous(true);
     run(config, 0, null);
     }
     */
}
