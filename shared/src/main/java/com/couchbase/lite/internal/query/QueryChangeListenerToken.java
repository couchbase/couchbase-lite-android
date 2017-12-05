package com.couchbase.lite.internal.query;

import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.internal.support.DefaultExecutor;
import com.couchbase.lite.query.QueryChange;
import com.couchbase.lite.query.QueryChangeListener;

import java.util.concurrent.Executor;

public class QueryChangeListenerToken implements ListenerToken {
    private Executor executor;
    private final QueryChangeListener listener;

    public QueryChangeListenerToken(Executor executor, QueryChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("a listener parameter is null");
        this.executor = executor;
        this.listener = listener;
    }

    public void notify(final QueryChange change) {
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
