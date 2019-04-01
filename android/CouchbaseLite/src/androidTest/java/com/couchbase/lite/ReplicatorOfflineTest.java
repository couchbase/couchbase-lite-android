package com.couchbase.lite;

import android.support.annotation.NonNull;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

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
    public void testDocumentChangeListenerToken() throws Exception {
        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = new Replicator(makeConfig(
                true,
                false,
                false,
                endpoint
        ));
        ListenerToken token = repl.addDocumentReplicationListener(new DocumentReplicationListener() {
            @Override
            public void replication(@NonNull DocumentReplication replication) { }
        });
        assertNotNull(token);

        thrown.expect(IllegalArgumentException.class);
        repl.addDocumentReplicationListener(null);

        thrown.expect(IllegalArgumentException.class);
        repl.addDocumentReplicationListener(executor, null);
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

    @Test
    public void testNetworkRetry() throws URISyntaxException, InterruptedException {
        Endpoint target = getRemoteTargetEndpoint();
        ReplicatorConfiguration config = makeConfig(false, true, true, db, target);
        Replicator repl = new Replicator(config);
        final CountDownLatch offline = new CountDownLatch(2);
        final CountDownLatch stopped = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(executor, new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                    offline.countDown();
                    if (offline.getCount() == 0) {
                        change.getReplicator().stop();
                    } else {
                        change.getReplicator().networkReachable();
                    }
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

    private URLEndpoint getRemoteTargetEndpoint() throws URISyntaxException {
        return new URLEndpoint(new URI("ws://foo.couchbase.com/db"));
    }
}
