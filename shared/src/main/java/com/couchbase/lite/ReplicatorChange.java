package com.couchbase.lite;

public final class ReplicatorChange {
    private final Replicator replicator;
    private final Replicator.Status status;

    ReplicatorChange(Replicator replicator, Replicator.Status status) {
        this.replicator = replicator;
        this.status = status;
    }

    public Replicator getReplicator() {
        return replicator;
    }

    public Replicator.Status getStatus() {
        return status;
    }
}
