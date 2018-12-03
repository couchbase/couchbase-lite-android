package com.couchbase.lite;

public final class DocumentReplicatedUpdate {
    private final Replicator replicator;
    private final Replicator.DocumentReplicatedStatus status;

    DocumentReplicatedUpdate(Replicator replicator, Replicator.DocumentReplicatedStatus status) {
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
    public Replicator.DocumentReplicatedStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "DocumentReplicatedUpdate{" +
                "replicator=" + replicator +
                ", status=" + status +
                '}';
    }
}

