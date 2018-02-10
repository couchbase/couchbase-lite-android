package com.couchbase.lite;

/**
 * The listener interface for receiving Replicator change events.
 */
public interface ReplicatorChangeListener {
    /**
     * The callback function from Replicator
     *
     * @param change the Replicator change information
     */
    void changed(ReplicatorChange change);
}
