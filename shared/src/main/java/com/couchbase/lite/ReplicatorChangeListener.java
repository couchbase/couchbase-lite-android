package com.couchbase.lite;

/**
 * The listener interface for receiving Replicator change events.
 */
public interface ReplicatorChangeListener {
    void changed(ReplicatorChange change);
}
