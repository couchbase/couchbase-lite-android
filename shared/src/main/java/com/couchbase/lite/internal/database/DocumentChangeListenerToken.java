package com.couchbase.lite.internal.database;

import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.DocumentChangeListener;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.internal.support.DefaultExecutor;

import java.util.concurrent.Executor;

public class DocumentChangeListenerToken implements ListenerToken {
    private Executor executor;
    private final DocumentChangeListener listener;
    private final String docID;

    public DocumentChangeListenerToken(Executor executor, DocumentChangeListener listener, String docID) {
        if (listener == null || docID == null)
            throw new IllegalArgumentException("a listener parameter or a docID parameter is null");
        this.executor = executor;
        this.listener = listener;
        this.docID = docID;
    }

    public String getDocID() {
        return docID;
    }

    public void notify(final DocumentChange change) {
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
