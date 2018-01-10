package com.couchbase.lite;

import java.util.concurrent.Executor;

class ReplicatorChangeListenerToken implements ListenerToken {
    private Executor executor;
    private final ReplicatorChangeListener listener;

    ReplicatorChangeListenerToken(Executor executor, ReplicatorChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("a listener parameter is null");
        this.executor = executor;
        this.listener = listener;
    }

    void notify(final ReplicatorChange change) {
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
