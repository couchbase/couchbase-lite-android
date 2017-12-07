package com.couchbase.lite.internal.database;

import com.couchbase.lite.DatabaseChange;
import com.couchbase.lite.DatabaseChangeListener;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.internal.support.DefaultExecutor;

import java.util.concurrent.Executor;

public class DatabaseChangeListenerToken implements ListenerToken {
    private Executor executor;
    private final DatabaseChangeListener listener;

    public DatabaseChangeListenerToken(Executor executor, DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("a listener parameter is null");
        this.executor = executor;
        this.listener = listener;
    }

    public void notify(final DatabaseChange change) {
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                listener.changed(change);
            }
        });
    }

    Executor getExecutor() {
        return executor != null ? executor : DefaultExecutor.instance();
    }
}
