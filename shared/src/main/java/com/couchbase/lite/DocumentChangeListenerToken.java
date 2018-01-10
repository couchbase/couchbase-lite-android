package com.couchbase.lite;

import java.util.concurrent.Executor;

class DocumentChangeListenerToken implements ListenerToken {
    private Executor executor;
    private final DocumentChangeListener listener;
    private final String docID;

    DocumentChangeListenerToken(Executor executor, DocumentChangeListener listener, String docID) {
        if (listener == null || docID == null)
            throw new IllegalArgumentException("a listener parameter or a docID parameter is null");
        this.executor = executor;
        this.listener = listener;
        this.docID = docID;
    }

    String getDocID() {
        return docID;
    }

    void notify(final DocumentChange change) {
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
