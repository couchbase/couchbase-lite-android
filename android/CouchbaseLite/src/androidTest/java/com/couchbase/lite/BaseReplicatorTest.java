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

    Database otherDB;
    Replicator repl;
    long timeout;  // seconds

    protected ReplicatorConfiguration.Builder makeConfig(boolean push, boolean pull,
                                                         boolean continuous) {
        return makeConfig(push, pull, continuous, this.otherDB);
    }

    protected ReplicatorConfiguration.Builder makeConfig(boolean push, boolean pull,
                                                         boolean continuous, Database targetDatabase) {
        DatabaseEndpoint endpoint = new DatabaseEndpoint(targetDatabase);
        ReplicatorConfiguration.Builder builder = new ReplicatorConfiguration.Builder(this.db, endpoint);
        builder.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        builder.setContinuous(continuous);
        return builder;
    }

    protected ReplicatorConfiguration.Builder makeConfig(boolean push, boolean pull,
                                                         boolean continuous, String targetURI) throws URISyntaxException {
        return makeConfig(push, pull, continuous, URI.create(targetURI));
    }

    protected ReplicatorConfiguration.Builder makeConfig(boolean push, boolean pull,
                                                         boolean continuous, URI target) throws URISyntaxException {
        return makeConfig(push, pull, continuous, this.db, target);
    }

    protected ReplicatorConfiguration.Builder makeConfig(boolean push, boolean pull,
                                                         boolean continuous, Database db, String targetURI) throws URISyntaxException {
        return makeConfig(push, pull, continuous, db, URI.create(targetURI));
    }

    protected ReplicatorConfiguration.Builder makeConfig(boolean push, boolean pull,
                                                         boolean continuous, Database db, URI targetURI) throws URISyntaxException {
        URLEndpoint endpoint = new URLEndpoint(targetURI.getHost(), targetURI.getPort(), targetURI.getPath(), false);
        ReplicatorConfiguration.Builder builder = new ReplicatorConfiguration.Builder(this.db, endpoint);
        builder.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        builder.setContinuous(continuous);
        return builder;
    }

    protected void run(final ReplicatorConfiguration config, final int code, final String domain)
            throws InterruptedException {
        repl = new Replicator(config);
        final CountDownLatch latch = new CountDownLatch(1);
        repl.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                CouchbaseLiteException error = status.getError();
                final String kActivityNames[] = {"stopped", "offline", "connecting", "idle", "busy"};
                Log.e(TAG, "---Status: %s (%d / %d), lastError = %s",
                        kActivityNames[status.getActivityLevel().getValue()],
                        status.getProgress().getCompleted(), status.getProgress().getTotal(),
                        error);
                if (config.isContinuous()) {
                    if (status.getActivityLevel() == Replicator.ActivityLevel.IDLE &&
                            status.getProgress().getCompleted() == status.getProgress().getTotal()) {
                        if (code != 0) {
                            assertEquals(code, error.getCode());
                            if (domain != null)
                                assertEquals(domain, error.getDomainString());
                        } else {
                            assertNull(error);
                        }
                        latch.countDown();
                    } else if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                        if (code != 0) {
                            assertNotNull(error);
                            assertEquals(code, error.getCode());
                            assertEquals(domain, error.getDomainString());
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
                                assertEquals(domain, error.getDomainString());
                        } else {
                            // TODO: revisited following check!!
                            //       currently commented out for testing.
                            //assertNull(error);
                        }
                        latch.countDown();
                    }
                }
            }
        });
        repl.start();
        assertTrue(latch.await(timeout, TimeUnit.SECONDS));
    }

    @Before
    public void setUp() throws Exception {
        config = new Config(
                InstrumentationRegistry.getContext().getAssets().open(Config.TEST_PROPERTIES_FILE));

        conflictResolver = new ConflictTest.MergeThenTheirsWins();
        super.setUp();

        timeout = 10; // seconds
        otherDB = open("otherdb");
        assertNotNull(otherDB);
    }

    @After
    public void tearDown() throws Exception {
        if (otherDB != null) {
            otherDB.close();
            otherDB = null;
        }

        super.tearDown();
    }
}
