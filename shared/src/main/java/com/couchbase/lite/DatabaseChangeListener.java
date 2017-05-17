package com.couchbase.lite;

/**
 * The listener interface for receiving Database change events.
 */
public interface DatabaseChangeListener {
    void changed(DatabaseChange change);
}
