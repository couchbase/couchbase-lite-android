package com.couchbase.lite;

public class Conflict {

    public enum OperationType {
        kCBLDatabaseWrite,
        kCBLPushReplication,
        kCBLPullReplication
    }

    private ReadOnlyDocument mine;
    private ReadOnlyDocument theirs;
    private ReadOnlyDocument base;
    private OperationType operationType;

    public Conflict(ReadOnlyDocument mine, ReadOnlyDocument theirs, ReadOnlyDocument base, OperationType operationType) {
        this.mine = mine;
        this.theirs = theirs;
        this.base = base;
        this.operationType = operationType;
    }

    public ReadOnlyDocument getMine() {
        return mine;
    }

    public ReadOnlyDocument getTheirs() {
        return theirs;
    }

    public ReadOnlyDocument getBase() {
        return base;
    }

    public OperationType getOperationType() {
        return operationType;
    }
}
