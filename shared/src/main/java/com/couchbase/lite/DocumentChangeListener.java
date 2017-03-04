package com.couchbase.lite;

public interface DocumentChangeListener {
    void changed(DocumentChangeEvent change);
}
