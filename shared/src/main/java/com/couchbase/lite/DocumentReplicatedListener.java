package com.couchbase.lite;

public interface DocumentReplicatedListener {
    /**
     * The callback function from Replicator
     *
     * @param update the Document replicated information
     */
    void replicated(DocumentReplicatedUpdate update);
}
