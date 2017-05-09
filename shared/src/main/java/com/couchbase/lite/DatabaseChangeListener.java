package com.couchbase.lite;

public interface DatabaseChangeListener {
    void changed(DatabaseChange change);
}
