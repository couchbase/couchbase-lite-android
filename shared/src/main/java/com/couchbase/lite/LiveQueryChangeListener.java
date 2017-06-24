package com.couchbase.lite;

public interface LiveQueryChangeListener {
    void changed(LiveQueryChange change);
}
