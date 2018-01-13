package com.couchbase.lite;

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DatabaseChangeListenerTokenTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new DatabaseChangeListenerToken(null, null);
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

        DatabaseChangeListener listener = new DatabaseChangeListener() {
            @Override
            public void changed(DatabaseChange change) {
            }
        };

        // custom Executor
        DatabaseChangeListenerToken token = new DatabaseChangeListenerToken(executor, listener);
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new DatabaseChangeListenerToken(null, listener);
        assertEquals(DefaultExecutor.instance(), token.getExecutor());
    }
}
