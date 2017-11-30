package com.couchbase.lite;

/**
 * Provides details about a Conflict.
 */
public class Conflict {
    private Document mine;
    private Document theirs;
    private Document base;

    Conflict(Document mine, Document theirs, Document base) {
        this.mine = mine;
        this.theirs = theirs;
        this.base = base;
    }

    public Document getMine() {
        return mine;
    }

    public Document getTheirs() {
        return theirs;
    }

    public Document getBase() {
        return base;
    }
}
