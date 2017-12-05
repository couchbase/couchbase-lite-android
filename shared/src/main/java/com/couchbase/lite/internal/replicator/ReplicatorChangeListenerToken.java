package com.couchbase.lite.internal.replicator;


import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.internal.support.DefaultExecutor;

import java.util.concurrent.Executor;

public class ReplicatorChangeListenerToken implements ListenerToken {
    private Executor executor;
    private final ReplicatorChangeListener listener;

    public ReplicatorChangeListenerToken(Executor executor, ReplicatorChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("a listener parameter is null");
        this.executor = executor;
        this.listener = listener;
    }

    public void notify(final ReplicatorChange change) {
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                listener.changed(change);
            }
        });
    }

    private Executor getExecutor() {
        return executor != null ? executor : DefaultExecutor.instance();
    }
}
