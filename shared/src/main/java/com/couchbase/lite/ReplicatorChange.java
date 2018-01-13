package com.couchbase.lite;

/**
 * ReplicatorChange contains the replicator status information.
 */
public final class ReplicatorChange {
    private final Replicator replicator;
    private final Replicator.Status status;

    ReplicatorChange(Replicator replicator, Replicator.Status status) {
        this.replicator = replicator;
        this.status = status;
    }

    /**
     * Return the source replicator object.
     */
    public Replicator getReplicator() {
        return replicator;
    }

    /**
     * Return the replicator status.
     */
    public Replicator.Status getStatus() {
        return status;
    }
}
