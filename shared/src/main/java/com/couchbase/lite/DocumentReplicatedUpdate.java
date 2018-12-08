package com.couchbase.lite;

/**
 * Document replicated update of a replicator.
 */
public final class DocumentReplicatedUpdate {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Replicator replicator;
    private boolean completed = false;
    private boolean isDeleted = false;
    private boolean pushing = false;
    private String docId = "";

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    DocumentReplicatedUpdate(Replicator replicator, boolean completed, boolean isDeleted, boolean pushing, String docId) {
        this.replicator = replicator;
        this.completed = completed;
        this.pushing = pushing;
        this.docId = docId;
        this.isDeleted = isDeleted;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the source replicator object.
     */
    public Replicator getReplicator() {
        return replicator;
    }

    /**
     * The current document replicated flag.
     */
    public boolean getCompletedFlag() {
        return completed;
    }

    /**
     * The current document replication direction flag.
     */
    public boolean getPushingFlag() {
        return pushing;
    }

    /**
     * The current document id.
     */
    public String getDocId() {
        return docId;
    }

    /**
     * The current document id.
     */
    public boolean getIsDeleted() {
        return isDeleted;
    }


    @Override
    public String toString() {
        return "DocumentReplicatedUpdate{" +
                "replicator=" + replicator +
                "is completed =" + completed +
                ", is pushing =" + pushing +
                ", document id =" + docId +
                ", doc is deleted =" + isDeleted +
                '}';
    }

    DocumentReplicatedUpdate copy() {
        return new DocumentReplicatedUpdate(replicator, completed, isDeleted, pushing, docId);
    }
}
