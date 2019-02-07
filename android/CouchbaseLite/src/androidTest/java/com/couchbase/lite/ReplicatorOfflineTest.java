package com.couchbase.lite;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplicatorOfflineTest extends BaseReplicatorTest {

    @Test
    public void testEditReadOnlyConfiguration() throws Exception {
        Endpoint endpoint = getRemoteTargetEndpoint();
        ReplicatorConfiguration config = makeConfig(
                true,
                false,
                true,
                endpoint
        );
        config.setContinuous(false);
        repl = new Replicator(config);

        thrown.expect(IllegalStateException.class);
        repl.getConfig().setContinuous(true);
    }

    @Test
    public void testStopReplicatorAfterOffline() throws URISyntaxException, InterruptedException {
        Endpoint target = getRemoteTargetEndpoint();
        ReplicatorConfiguration config = makeConfig(false, true, true, db, target);
        Replicator repl = new Replicator(config);
        final CountDownLatch offline = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                    change.getReplicator().stop();
                    offline.countDown();
                }
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    stopped.countDown();
                }
            }
        });
        repl.start();
        assertTrue(offline.await(10, TimeUnit.SECONDS));
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        repl.removeChangeListener(token);
    }

    @Test
    public void testStartSingleShotReplicatorInOffline() throws URISyntaxException, InterruptedException {
        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = new Replicator(makeConfig(
                true,
                false,
                false,
                endpoint
        ));
        final CountDownLatch stopped = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) {
                    stopped.countDown();
                }
            }
        });
        repl.start();
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        repl.removeChangeListener(token);
    }

    @Test
    public void testChangeListenerEmptyArg() throws Exception {
        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = new Replicator(makeConfig(
                true,
                false,
                true,
                endpoint
        ));

        thrown.expect(IllegalArgumentException.class);
        repl.addChangeListener(null);

        thrown.expect(IllegalArgumentException.class);
        repl.addChangeListener(executor, null);
    }

    private URLEndpoint getRemoteTargetEndpoint() throws URISyntaxException {
        return new URLEndpoint(new URI("ws://foo.couchbase.com/db"));
    }
}
