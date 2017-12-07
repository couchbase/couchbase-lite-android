package com.couchbase.lite.internal.replicator;


import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.internal.support.DefaultExecutor;

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ReplicatorChangeListenerTokenTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new ReplicatorChangeListenerToken(null, null);
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testGetExecutor() {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
            }
        };

        ReplicatorChangeListener listener = new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) {
            }
        };

        // custom Executor
        ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new ReplicatorChangeListenerToken(null, listener);
        assertEquals(DefaultExecutor.instance(), token.getExecutor());
    }
}
