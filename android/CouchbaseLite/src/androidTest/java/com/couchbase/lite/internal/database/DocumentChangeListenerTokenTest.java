package com.couchbase.lite.internal.database;


import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.DocumentChangeListener;
import com.couchbase.lite.internal.support.DefaultExecutor;

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DocumentChangeListenerTokenTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new DocumentChangeListenerToken(null, null, "doc1");
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }

        try {
            new DocumentChangeListenerToken(null, new DocumentChangeListener() {
                @Override
                public void changed(DocumentChange change) {
                }
            }, null);
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

        DocumentChangeListener listener = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
            }
        };

        // custom Executor
        DocumentChangeListenerToken token = new DocumentChangeListenerToken(executor, listener, "docID");
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new DocumentChangeListenerToken(null, listener, "docID");
        assertEquals(DefaultExecutor.instance(), token.getExecutor());
    }
}
