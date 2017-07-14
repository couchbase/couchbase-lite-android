package com.couchbase.lite;

/**
 * The listener interface for receiving Replicator change events.
 */
public interface ReplicatorChangeListener {
    void changed(Replicator replicator, Replicator.Status status, CouchbaseLiteException error);
}
