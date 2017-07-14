package com.couchbase.lite;

/**
 * Provides details about a Conflict.
 */
public class Conflict {
    private ReadOnlyDocument mine;
    private ReadOnlyDocument theirs;
    private ReadOnlyDocument base;

    Conflict(ReadOnlyDocument mine, ReadOnlyDocument theirs, ReadOnlyDocument base) {
        this.mine = mine;
        this.theirs = theirs;
        this.base = base;
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
}
