package com.couchbase.lite;

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QueryChangeListenerTokenTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new QueryChangeListenerToken(null, null);
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

        QueryChangeListener listener = new QueryChangeListener() {
            @Override
            public void changed(QueryChange change) {
            }
        };

        // custom Executor
        QueryChangeListenerToken token = new QueryChangeListenerToken(executor, listener);
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new QueryChangeListenerToken(null, listener);
        assertEquals(DefaultExecutor.instance(), token.getExecutor());
    }
}
