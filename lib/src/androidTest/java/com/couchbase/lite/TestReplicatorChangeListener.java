package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


public class TestReplicatorChangeListener implements ReplicatorChangeListener {
    private static final String[] ACTIVITY_NAMES = {"stopped", "offline", "connecting", "idle", "busy"};

    private final AtomicReference<AssertionError> testFailureReason = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private final Replicator replicator;
    private final String domain;
    private final int code;
    private final boolean ignoreErrorAtStopped;

    public TestReplicatorChangeListener(Replicator replicator, String domain, int code, boolean ignoreErrorAtStopped) {
        this.replicator = replicator;
        this.domain = domain;
        this.code = code;
        this.ignoreErrorAtStopped = ignoreErrorAtStopped;
    }

    public AssertionError getFailureReason() { return testFailureReason.get(); }

    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }
        return false;
    }

    @Override
    public void changed(@NonNull ReplicatorChange change) {
        final Replicator.Status status = change.getStatus();
        final Replicator.Progress progress = status.getProgress();
        final CouchbaseLiteException error = status.getError();

        BaseTest.log(
            LogLevel.INFO,
            "ReplicatorChangeListener.changed() status: " + status.getActivityLevel()
                + "(" + progress.getCompleted() + "/" + progress.getTotal() + "), lastError: " + error);

        try {
            if (!replicator.getConfig().isContinuous()) {
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
            testFailureReason.compareAndSet(null, e);
        }
    }
}
